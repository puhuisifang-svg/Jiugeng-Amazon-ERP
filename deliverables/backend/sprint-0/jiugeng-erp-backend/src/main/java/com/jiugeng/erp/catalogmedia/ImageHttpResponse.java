package com.jiugeng.erp.catalogmedia;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Arrays;
import org.springframework.http.*;

final class ImageHttpResponse {
    interface Bytes {byte[] read()throws IOException;}
    static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static boolean matches(String condition,String etag){return condition!=null&&Arrays.stream(condition.split(",")).map(String::trim).anyMatch(v->v.equals("*")||v.equals(etag)||v.equals("W/"+etag));}
    static ResponseEntity<byte[]> send(String immutableKey,String type,boolean cacheable,String condition,long start,Bytes bytes)throws IOException {
        String etag="\""+hash(immutableKey)+"\"";
        var headers=new HttpHeaders();
        headers.setCacheControl(cacheable?"private, no-cache, must-revalidate":"no-store");
        headers.setVary(java.util.List.of("Authorization"));headers.setContentType(MediaType.parseMediaType(type));
        headers.set("X-Content-Type-Options","nosniff");headers.set(HttpHeaders.CONTENT_DISPOSITION,"inline");
        if(cacheable)headers.setETag(etag);
        long authorized=System.nanoTime();
        if(cacheable&&matches(condition,etag)){
            headers.set("Server-Timing",timing("catalog_meta",authorized-start));return new ResponseEntity<>(null,headers,HttpStatus.NOT_MODIFIED);
        }
        byte[] body=bytes.read();
        headers.set("Server-Timing",timing("catalog_meta",authorized-start)+", "+timing("image_read",System.nanoTime()-authorized));
        return new ResponseEntity<>(body,headers,HttpStatus.OK);
    }
    private static String timing(String name,long nanos){return name+";dur="+String.format(java.util.Locale.ROOT,"%.2f",nanos/1e6);}
}
