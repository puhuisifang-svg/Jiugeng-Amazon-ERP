package com.jiugeng.erp.customs;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import java.io.*;
class WarehouseSourceTest {
 static void text(PDPageContentStream s,float x,float y,String value)throws IOException{s.beginText();s.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),11);s.newLineAtOffset(x,612-y);s.showText(value);s.endText();}
 static byte[] fixture()throws Exception{try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
  for(int p=0;p<10;p++){var page=new PDPage(new PDRectangle(792,612));doc.addPage(page);try(var stream=new PDPageContentStream(doc,page)){
   if(p==0){text(stream,72,90,"IPEX US Sites UPDATED 6/20/2025");}
   for(int i=0;i<11;i++){int n=p*11+i;float y=(p==0?215:75)+i*30;String code="Z"+(char)('A'+n/26)+(char)('A'+n%26)+"1";text(stream,78,y,code);text(stream,124,y-12,"123 Synthetic");text(stream,124,y,"Road");text(stream,221,y,"Synthetic City");text(stream,343,y,"CA");text(stream,391,y,"01234");}
  }}doc.save(out);return out.toByteArray();}}
 @Test void parsesWrappedAddressesWithoutMixingRows()throws Exception{var result=WarehouseSource.parse(fixture());assertThat(result.rows()).hasSize(110);assertThat(result.rows().get(0).address()).isEqualTo("123 Synthetic Road, Synthetic City, CA 01234, United States");assertThat(result.sourceDate()).isEqualTo("6/20/2025");assertThat(result.rejected()).isZero();}
 @Test void rejectsUnexpectedContent(){assertThatThrownBy(()->WarehouseSource.parse("bad source".getBytes())).isInstanceOf(IOException.class);}
}
