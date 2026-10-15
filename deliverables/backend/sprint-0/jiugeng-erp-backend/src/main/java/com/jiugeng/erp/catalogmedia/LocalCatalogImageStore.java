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
    static final int MAX_BYTES=5*1024*1024, MAX_DIMENSION=4096;
    static final long MAX_PIXELS=16_000_000L;
    private static final int THUMBNAIL_BOUND=320;
    private final Path root;
    LocalCatalogImageStore(@Value("${app.catalog.image-dir}") String directory){try{
        root=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(root);
        if(Files.isSymbolicLink(root))throw new IllegalStateException("Catalog image directory cannot be symlink");
        permissions(root,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE));
    }catch(IOException e){throw new IllegalStateException("Cannot initialize catalog image directory",e);}}
    public StoredImage store(byte[] source)throws IOException{if(source==null||source.length==0||source.length>MAX_BYTES)
        throw new CatalogImageException(413,"CATALOG_IMAGE_TOO_LARGE","图片必须存在且不得超过 5 MB");
        Decoded decoded=decode(source);byte[] original=png(decoded.image),thumbnail=png(thumbnail(decoded.image));
        String id=UUID.randomUUID().toString(),ok=id+"-original.png",tk=id+"-thumbnail.png";
        try{write(ok,original);write(tk,thumbnail);}catch(IOException e){delete(ok);delete(tk);throw e;}
        return new StoredImage(ok,tk,"image/png",decoded.mediaType,source.length,sha(source),decoded.image.getWidth(),decoded.image.getHeight());}
    public byte[] read(String key)throws IOException{Path file=resolve(key);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(file))throw new IOException("Image missing");return Files.readAllBytes(file);}
    public void delete(String key)throws IOException{try{Files.deleteIfExists(resolve(key));}catch(IllegalArgumentException e){throw new IOException("Invalid image key",e);}}
    private Decoded decode(byte[] source)throws IOException{try(ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(source))){
        if(stream==null)throw invalid();var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw invalid();ImageReader reader=readers.next();try{
            reader.setInput(stream,true,true);String format=reader.getFormatName().toLowerCase(Locale.ROOT);String media=switch(format){case"jpeg","jpg"->"image/jpeg";case"png"->"image/png";case"webp"->"image/webp";default->throw invalid();};
            int width=reader.getWidth(0),height=reader.getHeight(0);if(width<=0||height<=0||width>MAX_DIMENSION||height>MAX_DIMENSION||(long)width*height>MAX_PIXELS)throw new CatalogImageException(400,"CATALOG_IMAGE_DIMENSIONS_INVALID","图片宽高不得超过 4096 像素且总像素不得超过 1600 万");
            BufferedImage image=reader.read(0);if(image==null)throw invalid();return new Decoded(image,media);}finally{reader.dispose();}}
        catch(CatalogImageException e){throw e;}catch(RuntimeException e){throw invalid();}}
    private BufferedImage thumbnail(BufferedImage source){double scale=Math.min(1d,(double)THUMBNAIL_BOUND/Math.max(source.getWidth(),source.getHeight()));int w=Math.max(1,(int)Math.round(source.getWidth()*scale)),h=Math.max(1,(int)Math.round(source.getHeight()*scale));BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(source,0,0,w,h,null);}finally{g.dispose();}return out;}
    private byte[] png(BufferedImage image)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",out))throw new IOException("PNG writer unavailable");return out.toByteArray();}
    private void write(String key,byte[] bytes)throws IOException{Path target=resolve(key),tmp=root.resolve(key+".tmp").normalize();try{Files.write(tmp,bytes);permissions(tmp,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(tmp,target);}}finally{Files.deleteIfExists(tmp);}}
    private Path resolve(String key){if(key==null||!key.matches("[0-9a-fA-F-]{36}-(?:original|thumbnail)\\.png"))throw new IllegalArgumentException("Invalid image key");Path path=root.resolve(key).normalize();if(!path.startsWith(root))throw new IllegalArgumentException("Invalid image key");return path;}
    private CatalogImageException invalid(){return new CatalogImageException(400,"CATALOG_IMAGE_INVALID","仅支持可正常解码的 JPG、PNG 或 WebP 图片");}
    private String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void permissions(Path path,Set<PosixFilePermission> value){try{Files.setPosixFilePermissions(path,value);}catch(UnsupportedOperationException|IOException ignored){}}
    private record Decoded(BufferedImage image,String mediaType){}
}
