package com.jiugeng.erp.catalogmedia;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.time.Instant;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.jiugeng.erp.iam.AuthenticatedActor;

@SpringBootTest @ActiveProfiles("test")
class ImageDeliveryTest {
    @Autowired CatalogImageStore store;@Autowired CatalogImageRepository assets;@Autowired CatalogImageVariants variants;
    @Autowired CatalogImageController controller;@Autowired ExternalImageService external;@Autowired JdbcClient jdbc;
    @Autowired List<ExternalImageSource> sources;
    @MockitoBean ExternalImageFetcher fetcher;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    final AuthenticatedActor actor=new AuthenticatedActor(){public String userId(){return "synthetic-image-reader";}public String loginName(){return "synthetic";}public String displayName(){return "合成测试";}};
    UsernamePasswordAuthenticationToken auth(String... permissions){var a=new UsernamePasswordAuthenticationToken(actor,null,Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(a);return a;}
    @org.junit.jupiter.api.AfterEach void clearAuth(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    static byte[] image(int salt)throws IOException{
        var input=new java.awt.image.BufferedImage(640,480,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<480;y++)for(int x=0;x<640;x++)input.setRGB(x,y,((x*255/640)<<16)|((y*255/480)<<8)|((x+y+salt)%256));
        var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(input,"png",out);return out.toByteArray();
    }
    String asset(CatalogImageGateway.Purpose purpose)throws IOException{
        var saved=store.store(image(0),false);String id=UUID.randomUUID().toString();
        assets.insert(new CatalogImageRepository.Asset(id,purpose,"PENDING",saved.originalKey(),saved.thumbnailKey(),"synthetic.png",saved.sourceMediaType(),saved.storedMediaType(),saved.sourceSize(),saved.sha256(),saved.width(),saved.height(),actor.userId(),null,null,Instant.now()));
        assets.attach(id,purpose,actor.userId(),"SYNTHETIC",id,Instant.now());return id;
    }
    @Test void revalidatesAfterAuthorizationAndBackfillsWithoutChangingOriginal()throws Exception{
        String id=asset(CatalogImageGateway.Purpose.SKU_MAIN);var original=controller.content(id,"original",null,auth("products.read"),actor);
        var before=controller.content(id,"thumbnail",null,auth("products.read"),actor);
        assertThat(before.getHeaders().getCacheControl()).isEqualTo("private, no-cache, must-revalidate");
        var cached=controller.content(id,"thumbnail",before.getHeaders().getETag(),auth("products.read"),actor);
        assertThat(cached.getStatusCode().value()).isEqualTo(304);assertThat(cached.getBody()).isNull();
        assertThatThrownBy(()->controller.content(id,"thumbnail",before.getHeaders().getETag(),auth("suppliers.read"),actor)).isInstanceOf(CatalogImageException.class);
        variants.generate(id);
        var after=controller.content(id,"thumbnail",before.getHeaders().getETag(),auth("products.read"),actor);
        assertThat(after.getStatusCode().value()).isEqualTo(200);assertThat(after.getHeaders().getETag()).isNotEqualTo(before.getHeaders().getETag());
        var decoded=javax.imageio.ImageIO.read(new ByteArrayInputStream(after.getBody()));assertThat(decoded.getWidth()).isEqualTo(160);
        assertThat(after.getBody().length).isLessThan(before.getBody().length/2);
        assertThat(controller.content(id,"original",null,auth("products.read"),actor).getBody()).isEqualTo(original.getBody());
        String key=variants.find(id).key();variants.generate(id);assertThat(variants.find(id).key()).isEqualTo(key);
        System.out.printf("ERP101_SYNTHETIC old_thumbnail_bytes=%d new_thumbnail_bytes=%d%n",before.getBody().length,after.getBody().length);
    }
    @Test void keepsContactImagesLosslessAndUncached()throws Exception{
        String id=asset(CatalogImageGateway.Purpose.SUPPLIER_QR);variants.generate(id);
        var response=controller.content(id,"thumbnail","*",auth("suppliers.read"),actor);
        assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(javax.imageio.ImageIO.read(new ByteArrayInputStream(response.getBody())).getWidth()).isEqualTo(320);
    }
    @Test void conditionalResponseDoesNotReadStorage()throws Exception{
        String etag="\""+ImageHttpResponse.hash("immutable")+"\"";
        var response=ImageHttpResponse.send("immutable","image/jpeg",true,"\"other\", W/"+etag,System.nanoTime(),()->{throw new AssertionError("must not read bytes");});
        assertThat(response.getStatusCode().value()).isEqualTo(304);
    }
    @Test void everySourceQueryIsValidAndUnknownUrlsAreNotAuthorized(){
        for(var source:sources){assertThat(source.contains("https://example.invalid/not-a-real-image.png")).isFalse();source.scan("",2);}
        assertThatThrownBy(()->external.resolve("https://example.invalid/never.png",null,auth("products.read"),actor)).isInstanceOf(CatalogImageException.class);
    }
    @Test void mirrorsUpdatesAndKeepsLastGoodImageOnFailure()throws Exception{
        String url="https://example.invalid/"+UUID.randomUUID()+".png";String id=external.register(url,false,actor.userId());
        when(fetcher.fetch(eq(url),any(),any())).thenReturn(new ExternalImageFetcher.Result(image(0),"v1",null,false));
        external.refresh(id);var first=external.find(id);assertThat(first.original()).isNotNull();assertThat(first.thumbnailType()).isEqualTo("image/jpeg");
        due(id);when(fetcher.fetch(eq(url),any(),any())).thenReturn(new ExternalImageFetcher.Result(null,"v1",null,true));
        external.refresh(id);assertThat(external.find(id).original()).isEqualTo(first.original());
        due(id);when(fetcher.fetch(eq(url),any(),any())).thenReturn(new ExternalImageFetcher.Result(image(77),"v2",null,false));
        external.refresh(id);var changed=external.find(id);assertThat(changed.original()).isNotEqualTo(first.original());assertThat(changed.sha()).isNotEqualTo(first.sha());
        assertThat(store.read(first.original())).isNotEmpty();
        due(id);when(fetcher.fetch(eq(url),any(),any())).thenThrow(new IOException("synthetic timeout"));
        external.refresh(id);assertThat(external.find(id).original()).isEqualTo(changed.original());assertThat(external.find(id).failures()).isEqualTo(1);
    }
    @Test void cachedExternalImagesStillRequireResourceOrDraftPermission(){
        String id=external.register("https://example.invalid/"+UUID.randomUUID()+".png",true,actor.userId());
        jdbc.sql("INSERT INTO catalog_external_image_grant(image_id,actor_id,permission) VALUES(:id,:actor,'suppliers.write')").param("id",id).param("actor",actor.userId()).update();
        assertThatThrownBy(()->external.authorized(id,auth("products.write"),actor)).isInstanceOf(CatalogImageException.class);
    }
    @Test void removesGeneratedVariantWhenItsDatabaseTransactionRollsBack()throws Exception{
        String id=asset(CatalogImageGateway.Purpose.SKU_MAIN);String[] generated=new String[1];
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->{
            variants.generate(id);generated[0]=variants.find(id).key();tx.setRollbackOnly();
        });
        assertThat(variants.find(id)).isNull();
        assertThatThrownBy(()->store.read(generated[0])).isInstanceOf(IOException.class);
        assertThat(store.read(assets.find(id).orElseThrow().originalKey())).isNotEmpty();
    }
    @Test void expiredWorkerCannotReplaceOrClearAnotherWorkersLease()throws Exception{
        String url="https://example.invalid/"+UUID.randomUUID()+".png";
        String id=external.register(url,false,actor.userId());
        Instant newerLease=Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        when(fetcher.fetch(eq(url),any(),any())).thenAnswer(call->{
            jdbc.sql("UPDATE catalog_external_image SET lease_until=:lease WHERE id=:id").param("lease",newerLease).param("id",id).update();
            return new ExternalImageFetcher.Result(image(1),"new",null,false);
        });
        external.refresh(id);
        assertThat(external.find(id).original()).isNull();
        assertThat(jdbc.sql("SELECT lease_until FROM catalog_external_image WHERE id=:id").param("id",id)
            .query(java.sql.Timestamp.class).single().toInstant()).isEqualTo(newerLease);
    }
    private void due(String id){jdbc.sql("UPDATE catalog_external_image SET next_check_at=:now WHERE id=:id").param("now",Instant.now().minusSeconds(5)).param("id",id).update();}
}
