package com.jiugeng.erp.customs;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import java.awt.geom.Rectangle2D;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class WarehouseSource {
 static final String URL="https://images-na.ssl-images-amazon.com/images/G/01/magicarp/common/FC_Operating_Times_USAmazon.pdf";
 private final String proxy;
 WarehouseSource(@Value("${erp.collection.proxy:}") String proxy){this.proxy=proxy;}
 record Row(String code,String region,String city,String zip,String address){}
 record Result(List<Row> rows,int rejected,String sourceDate){}
 Result fetch()throws Exception{
  var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20));
  if(!proxy.isBlank()){var u=URI.create(proxy);builder.proxy(ProxySelector.of(new InetSocketAddress(u.getHost(),u.getPort())));}
  var client=builder.build();Exception last=null;
  for(int attempt=0;attempt<3;attempt++)try{
   var response=client.send(HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(90)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
   try(var in=response.body()){if(response.statusCode()!=200)throw new IOException("来源响应 HTTP "+response.statusCode());byte[] bytes=in.readNBytes(12_000_001);if(bytes.length>12_000_000)throw new IOException("来源文件过大");return parse(bytes);}
  }catch(Exception e){if(e instanceof InterruptedException){Thread.currentThread().interrupt();throw e;}last=e;}
  throw new IOException("网络读取或格式校验失败，已尝试3次",last);
 }
 static Result parse(byte[] bytes)throws IOException{
  try(var doc=Loader.loadPDF(bytes)){
   if(doc.getNumberOfPages()>100)throw new IOException("来源页数异常");
   String text=new PDFTextStripper().getText(doc);var date=Pattern.compile("UPDATED\\s+(\\d{1,2}/\\d{1,2}/\\d{4})").matcher(text);
   if(!text.contains("US Sites")||!date.find())throw new IOException("来源格式已变化，未写入地址");String sourceDate=date.group(1);
   var rows=new LinkedHashMap<String,Row>();var conflicted=new HashSet<String>();int rejected=0;
   for(int p=0;p<doc.getNumberOfPages();p++){
    var pageText=new PDFTextStripper();pageText.setStartPage(p+1);pageText.setEndPage(p+1);if(pageText.getText(doc).contains("CA Sites"))break;
    final var markers=new TreeMap<Float,String>();var stripper=new PDFTextStripper(){@Override protected void writeString(String s,List<TextPosition> positions){
     StringBuilder code=new StringBuilder();float y=0;for(var t:positions)if(t.getXDirAdj()>=72&&t.getXDirAdj()<119){code.append(t.getUnicode());y=t.getYDirAdj();}
     String c=code.toString().trim();if(c.matches("[A-Z]{2,4}[0-9A-Z]"))markers.put(y,c);
    }};stripper.setStartPage(p+1);stripper.setEndPage(p+1);stripper.getText(doc);
    float previous=p==0?188:0;
    for(var entry:markers.entrySet()){
     float bottom=entry.getKey()+3;var area=new PDFTextStripperByArea();area.setSortByPosition(true);
     float[] x={120,218,332,376,443};String[] keys={"street","city","state","zip"};for(int i=0;i<4;i++)area.addRegion(keys[i],new Rectangle2D.Float(x[i],previous,x[i+1]-x[i],bottom-previous));
     area.extractRegions(doc.getPage(p));previous=bottom;
     String street=clean(area.getTextForRegion("street")),city=clean(area.getTextForRegion("city")),state=clean(area.getTextForRegion("state")),zip=clean(area.getTextForRegion("zip"));
     if(zip.matches("\\d{4}"))zip="0"+zip;
     if(!state.matches("AL|AK|AZ|AR|CA|CO|CT|DE|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY|DC")||!zip.matches("\\d{5}(-\\d{4})?")||city.isBlank()||!street.matches(".*\\d.*")||street.length()>180||city.length()>100){rejected++;continue;}
     var row=new Row(entry.getValue(),state,city,zip,street+", "+city+", "+state+" "+zip+", United States");
     if(conflicted.contains(row.code())){rejected++;continue;}
     var prior=rows.putIfAbsent(row.code(),row);if(prior!=null&&!prior.equals(row)){rows.remove(row.code());conflicted.add(row.code());rejected+=2;}
    }
   }
   if(rows.size()<100||rejected>rows.size()/4)throw new IOException("来源数据不足或解析异常，未写入地址");
   return new Result(List.copyOf(rows.values()),rejected,sourceDate);
  }
 }
 private static String clean(String s){return s.replaceAll("\\s+"," ").trim();}
}
