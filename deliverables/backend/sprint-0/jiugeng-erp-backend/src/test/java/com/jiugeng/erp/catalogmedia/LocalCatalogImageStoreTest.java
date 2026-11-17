package com.jiugeng.erp.catalogmedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCatalogImageStoreTest {
    private static final byte[] ONE_PIXEL_WEBP = Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");

    @Test
    void decodesWebpAndStoresNormalizedProtectedPng(@TempDir java.nio.file.Path directory) throws Exception {
        LocalCatalogImageStore store = new LocalCatalogImageStore(directory.toString());

        CatalogImageStore.StoredImage image = store.store(ONE_PIXEL_WEBP);

        assertThat(image.sourceMediaType()).isEqualTo("image/webp");
        assertThat(image.storedMediaType()).isEqualTo("image/png");
        assertThat(image.width()).isEqualTo(1);
        assertThat(image.height()).isEqualTo(1);
        assertThat(store.read(image.originalKey())).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(Files.isRegularFile(directory.resolve(image.thumbnailKey()))).isTrue();
    }

    @Test
    void rejectsPayloadLargerThanFiveMegabytes(@TempDir java.nio.file.Path directory) {
        LocalCatalogImageStore store = new LocalCatalogImageStore(directory.toString());

        assertThatThrownBy(() -> store.store(new byte[LocalCatalogImageStore.MAX_BYTES + 1]))
                .isInstanceOf(CatalogImageException.class)
                .extracting(exception -> ((CatalogImageException) exception).code())
                .isEqualTo("CATALOG_IMAGE_TOO_LARGE");
    }

    @Test void keepsDimensionsAndAlphaAndReducesPhotographicBytes(@TempDir java.nio.file.Path directory)throws Exception {
        var source=new java.awt.image.BufferedImage(1600,1200,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<1200;y++)for(int x=0;x<1600;x++)source.setRGB(x,y,((x*255/1600)<<16)|((y*255/1200)<<8)|((x+y)%256));
        var input=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(source,"jpg",input);
        var decoded=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(input.toByteArray()));
        var old=new java.io.ByteArrayOutputStream();long baseline=System.nanoTime();javax.imageio.ImageIO.write(decoded,"png",old);long oldNanos=System.nanoTime()-baseline;
        var store=new LocalCatalogImageStore(directory.toString());long start=System.nanoTime();var asset=store.store(input.toByteArray());long newNanos=System.nanoTime()-start;
        assertThat(store.store(input.toByteArray(),false).storedMediaType()).isEqualTo("image/png");
        byte[] bytes=store.read(asset.originalKey());var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        assertThat(asset.storedMediaType()).isEqualTo("image/jpeg");assertThat(image.getWidth()).isEqualTo(1600);assertThat(image.getHeight()).isEqualTo(1200);
        var thumb=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(store.read(asset.thumbnailKey())));assertThat(thumb.getWidth()).isEqualTo(320);assertThat(thumb.getHeight()).isEqualTo(240);
        assertThat(bytes.length).isLessThan(old.size());
        long difference=0;for(int y=0;y<1200;y+=8)for(int x=0;x<1600;x+=8)for(int shift:new int[]{0,8,16})difference+=Math.abs(((decoded.getRGB(x,y)>>shift)&255)-((image.getRGB(x,y)>>shift)&255));
        assertThat(difference/(200d*150*3)).isLessThan(5);
        System.out.printf("IMAGE_BENCH synthetic 1600x1200 baseline_png_bytes=%d new_jpeg_bytes=%d baseline_png_encode_ms=%.1f new_full_store_ms=%.1f%n",old.size(),bytes.length,oldNanos/1e6,newNanos/1e6);
        var transparent=new java.awt.image.BufferedImage(600,400,java.awt.image.BufferedImage.TYPE_INT_ARGB);transparent.setRGB(0,0,0x80112233);
        input.reset();javax.imageio.ImageIO.write(transparent,"png",input);var alpha=store.store(input.toByteArray());
        assertThat(alpha.storedMediaType()).isEqualTo("image/png");assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(store.read(alpha.originalKey()))).getRGB(0,0)).isEqualTo(0x80112233);
    }
    @Test void readsLocalFilesBeforeOssAndCachesRemoteBytes(@TempDir java.nio.file.Path directory)throws Exception {
        var store=new LocalCatalogImageStore(directory.toString());var image=store.store(ONE_PIXEL_WEBP);
        var objects=org.mockito.Mockito.mock(com.jiugeng.erp.objectstorage.PrivateObjectStorage.class);store.configureStorage(objects);
        store.read(image.originalKey());store.read(image.originalKey());org.mockito.Mockito.verifyNoInteractions(objects);
        Files.delete(directory.resolve(image.thumbnailKey()));
        org.mockito.Mockito.when(objects.read("catalog-images",image.thumbnailKey())).thenReturn(java.util.Optional.of(new byte[]{1,2,3}));
        assertThat(store.read(image.thumbnailKey())).containsExactly(1,2,3);store.read(image.thumbnailKey());org.mockito.Mockito.verify(objects,org.mockito.Mockito.times(1)).read("catalog-images",image.thumbnailKey());
        store.delete(image.thumbnailKey());org.mockito.Mockito.when(objects.read("catalog-images",image.thumbnailKey())).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(()->store.read(image.thumbnailKey())).isInstanceOf(java.io.IOException.class);
    }
    @Test void waitsForBothWritesBeforeCleaningFailure(@TempDir java.nio.file.Path directory)throws Exception {
        var store=new LocalCatalogImageStore(directory.toString());var objects=org.mockito.Mockito.mock(com.jiugeng.erp.objectstorage.PrivateObjectStorage.class);
        var finished=new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call->{String key=call.getArgument(1);if(key.contains("original"))throw new java.io.IOException("synthetic failure");Thread.sleep(30);finished.set(true);return null;}).when(objects).put(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer(call->{assertThat(finished.get()).isTrue();return null;}).when(objects).delete(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        store.configureStorage(objects);assertThatThrownBy(()->store.store(ONE_PIXEL_WEBP)).isInstanceOf(java.io.IOException.class);
        org.mockito.Mockito.verify(objects,org.mockito.Mockito.times(2)).delete(org.mockito.ArgumentMatchers.eq("catalog-images"),org.mockito.ArgumentMatchers.anyString());
        try(var files=Files.list(directory)){assertThat(files.count()).isZero();}
    }
}
