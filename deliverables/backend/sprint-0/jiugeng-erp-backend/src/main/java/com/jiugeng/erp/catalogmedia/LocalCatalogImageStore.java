package com.jiugeng.erp.catalogmedia;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LocalCatalogImageStore implements CatalogImageStore {
    private com.jiugeng.erp.objectstorage.PrivateObjectStorage objects =
        new com.jiugeng.erp.objectstorage.PrivateObjectStorage(null, "", "erp/");
    @org.springframework.beans.factory.annotation.Autowired
    void configureStorage(com.jiugeng.erp.objectstorage.PrivateObjectStorage storage) { this.objects = storage; }
    static final int MAX_BYTES=5*1024*1024, MAX_DIMENSION=4096;
    static final long MAX_PIXELS=16_000_000L;
    private static final int THUMBNAIL_BOUND=320;
    // Remote files are immutable UUID keys. Authorization happens in the service before every read.
    private final java.util.Map<String,byte[]> cache = new java.util.LinkedHashMap<>(32,.75f,true);
    private long cacheBytes;
    private static final long CACHE_BYTES=32L*1024*1024;
    private final Object[] readLocks=java.util.stream.IntStream.range(0,32).mapToObj(i->new Object()).toArray();
    private io.micrometer.core.instrument.MeterRegistry metrics = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    @org.springframework.beans.factory.annotation.Autowired
    void configureMetrics(io.micrometer.core.instrument.MeterRegistry registry){metrics=registry;}
    private void timing(String stage,long start){metrics.timer("catalog.image.duration","stage",stage).record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS);}
    private synchronized byte[] cached(String key){return cache.get(key);}
    private synchronized void remember(String key,byte[] bytes){
        if(bytes.length>CACHE_BYTES)return;
        byte[] previous=cache.put(key,bytes);if(previous!=null)cacheBytes-=previous.length;cacheBytes+=bytes.length;
        var it=cache.entrySet().iterator();while(cacheBytes>CACHE_BYTES&&it.hasNext()){cacheBytes-=it.next().getValue().length;it.remove();}
    }
    private synchronized void forget(String key){byte[] old=cache.remove(key);if(old!=null)cacheBytes-=old.length;}

    private final Path root;
    LocalCatalogImageStore(@Value("${app.catalog.image-dir}") String directory){try{
        root=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(root);
        if(Files.isSymbolicLink(root))throw new IllegalStateException("Catalog image directory cannot be symlink");
        permissions(root,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE));
    }catch(IOException e){throw new IllegalStateException("Cannot initialize catalog image directory",e);}}
    public StoredImage store(byte[] source)throws IOException{return store(source,true);}
    @Override public StoredImage store(byte[] source,boolean optimizePhotographs)throws IOException{if(source==null||source.length==0||source.length>MAX_BYTES)
        throw new CatalogImageException(413,"CATALOG_IMAGE_TOO_LARGE","图片必须存在且不得超过 5 MB");
        long processing=System.nanoTime();Decoded decoded=decode(source);
        // Preserve dimensions and alpha; normalized JPEG is used only for opaque photographic inputs.
        boolean jpeg=optimizePhotographs&&!decoded.image.getColorModel().hasAlpha()&& !decoded.mediaType.equals("image/png") && (long)decoded.image.getWidth()*decoded.image.getHeight()>4096;
        String format=jpeg?"jpg":"png";
        byte[] original=encode(decoded.image,jpeg),thumbnail=encode(thumbnail(decoded.image),jpeg);
        timing("process",processing);
        String id=UUID.randomUUID().toString(),ok=id+"-original."+format,tk=id+"-thumbnail."+format;
        // A bounded shared executor avoids unbounded per-upload threads. Wait for BOTH writes even on failure.
        var first=java.util.concurrent.CompletableFuture.runAsync(()->writeUnchecked(ok,original),WRITES);
        var second=java.util.concurrent.CompletableFuture.runAsync(()->writeUnchecked(tk,thumbnail),WRITES);
        try{java.util.concurrent.CompletableFuture.allOf(first,second).join();}
        catch(java.util.concurrent.CompletionException e){
            IOException failure=new IOException("Image storage upload failed");
            try{delete(ok);}catch(IOException cleanup){failure.addSuppressed(cleanup);}
            try{delete(tk);}catch(IOException cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
        return new StoredImage(ok,tk,jpeg?"image/jpeg":"image/png",decoded.mediaType,source.length,sha(source),decoded.image.getWidth(),decoded.image.getHeight(),thumbnail);}
    private static final java.util.concurrent.ExecutorService WRITES=new java.util.concurrent.ThreadPoolExecutor(2,2,0,java.util.concurrent.TimeUnit.MILLISECONDS,
        new java.util.concurrent.ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"catalog-image-storage");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    private void writeUnchecked(String key,byte[] bytes){try{write(key,bytes);}catch(IOException e){throw new java.util.concurrent.CompletionException(e);}}
    public byte[] read(String key)throws IOException{
        Path file=resolve(key);long start=System.nanoTime();
        try{synchronized(readLocks[Math.floorMod(key.hashCode(),readLocks.length)]){
            byte[] hit=cached(key);if(hit!=null){metrics.counter("catalog.image.read","source","memory").increment();return hit;}
            if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(file)){
                byte[] bytes=Files.readAllBytes(file);remember(key,bytes);metrics.counter("catalog.image.read","source","disk").increment();return bytes;
            }
            var remote=objects.read("catalog-images",key);if(remote.isPresent()){byte[] bytes=remote.get();remember(key,bytes);metrics.counter("catalog.image.read","source","oss").increment();return bytes;}
            throw new IOException("Image missing");
        }}finally{timing("read",start);}
    }
    public void delete(String key)throws IOException{try{synchronized(readLocks[Math.floorMod(key.hashCode(),readLocks.length)]){resolve(key);forget(key);objects.delete("catalog-images",key);Files.deleteIfExists(resolve(key));}}catch(IllegalArgumentException e){throw new IOException("Invalid image key",e);}}
    private Decoded decode(byte[] source)throws IOException{try(ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(source))){
        if(stream==null)throw invalid();var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw invalid();ImageReader reader=readers.next();try{
            reader.setInput(stream,true,true);String format=reader.getFormatName().toLowerCase(Locale.ROOT);String media=switch(format){case"jpeg","jpg"->"image/jpeg";case"png"->"image/png";case"webp"->"image/webp";default->throw invalid();};
            int width=reader.getWidth(0),height=reader.getHeight(0);if(width<=0||height<=0||width>MAX_DIMENSION||height>MAX_DIMENSION||(long)width*height>MAX_PIXELS)throw new CatalogImageException(400,"CATALOG_IMAGE_DIMENSIONS_INVALID","图片宽高不得超过 4096 像素且总像素不得超过 1600 万");
            BufferedImage image=reader.read(0);if(image==null)throw invalid();return new Decoded(image,media);}finally{reader.dispose();}}
        catch(CatalogImageException e){throw e;}catch(RuntimeException e){throw invalid();}}
    private BufferedImage thumbnail(BufferedImage source){double scale=Math.min(1d,(double)THUMBNAIL_BOUND/Math.max(source.getWidth(),source.getHeight()));int w=Math.max(1,(int)Math.round(source.getWidth()*scale)),h=Math.max(1,(int)Math.round(source.getHeight()*scale));BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(source,0,0,w,h,null);}finally{g.dispose();}return out;}
    private byte[] encode(BufferedImage image,boolean jpeg)throws IOException{
        if(!jpeg)return png(image);
        BufferedImage rgb=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=rgb.createGraphics();try{g.drawImage(image,0,0,null);}finally{g.dispose();}
        var writer=ImageIO.getImageWritersByFormatName("jpeg").next();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(var stream=ImageIO.createImageOutputStream(bytes)){writer.setOutput(stream);var params=writer.getDefaultWriteParam();params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);params.setCompressionQuality(.95f);writer.write(null,new javax.imageio.IIOImage(rgb,null,null),params);}
        finally{writer.dispose();}return bytes.toByteArray();
    }
    private byte[] png(BufferedImage image)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",out))throw new IOException("PNG writer unavailable");return out.toByteArray();}
    private void write(String key,byte[] bytes)throws IOException{Path target=resolve(key),tmp=root.resolve(key+".tmp").normalize();long started=System.nanoTime();try{objects.put("catalog-images",key,bytes);}finally{timing("oss-write",started);}try{Files.write(tmp,bytes);permissions(tmp,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(tmp,target);}}finally{Files.deleteIfExists(tmp);}}
    private Path resolve(String key){if(key==null||!key.matches("[0-9a-fA-F-]{36}-(?:original|thumbnail)\\.(?:png|jpg)"))throw new IllegalArgumentException("Invalid image key");Path path=root.resolve(key).normalize();if(!path.startsWith(root))throw new IllegalArgumentException("Invalid image key");return path;}
    private CatalogImageException invalid(){return new CatalogImageException(400,"CATALOG_IMAGE_INVALID","仅支持可正常解码的 JPG、PNG 或 WebP 图片");}
    private String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void permissions(Path path,Set<PosixFilePermission> value){try{Files.setPosixFilePermissions(path,value);}catch(UnsupportedOperationException|IOException ignored){}}
    private record Decoded(BufferedImage image,String mediaType){}
}
