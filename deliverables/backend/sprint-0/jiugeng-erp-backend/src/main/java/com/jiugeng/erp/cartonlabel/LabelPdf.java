package com.jiugeng.erp.cartonlabel;

import static com.jiugeng.erp.cartonlabel.LabelTypes.*;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

/** Vector text and lines; embedded Chinese font, no dependence on server/system fonts. */
@Component
class LabelPdf {
    static final float MM=72f/25.4f, SIZE=100*MM;
    byte[] render(List<Snapshot> items) {
        try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream();
            var fontStream=getClass().getResourceAsStream("/fonts/NotoSansSC-Regular.ttf")) {
            if(fontStream==null) throw new IOException("Missing bundled label font");
            var font=PDType0Font.load(doc,fontStream,true);
            for(var item:items) for(int copy=0;copy<item.pages();copy++) {
                var page=new PDPage(new PDRectangle(SIZE,SIZE));doc.addPage(page);
                try(var cs=new PDPageContentStream(doc,page)) { draw(cs,font,item); }
            }
            doc.getDocumentInformation().setTitle("外箱标 100x100mm");
            doc.save(bytes);return bytes.toByteArray();
        } catch(IllegalArgumentException e) {
            throw LabelService.invalid("标签包含字体不支持的字符，请使用常用中英文或数字");
        } catch(IOException e) { throw new IllegalStateException("外箱标 PDF 生成失败",e); }
    }
    private void draw(PDPageContentStream cs,PDType0Font font,Snapshot s) throws IOException {
        float x=3*MM,w=94*MM,top=97*MM;
        var entry=s.entry();var config=s.template().config();
        cell(cs,font,entry.productName(),x,top,w*.60f,8*MM,8.5f,false,false,2,false);
        cell(cs,font,blank(entry.contractNo())?"":entry.contractNo(),x+w*.60f,top,w*.40f,8*MM,8.5f,false,false,2,false);
        top-=8*MM;
        cell(cs,font,config.title(),x,top,w,12*MM,config.titleSize(),true,config.color());top-=12*MM;
        // Compact two-column header leaves 56 mm for the main table.
        float weight=config.rows().stream().mapToInt(Row::height).sum();
        for(var row:config.rows()) {
            float h=56*MM*row.height()/weight;
            String value=switch(row.key()) {
                case SKU->entry.customerCode();case QUANTITY->Integer.toString(s.quantity());
                case BOXES->Integer.toString(s.boxes());case PURCHASER->entry.purchaser();
            };
            cell(cs,font,row.label(),x,top,w*.45f,h,Math.min(row.fontSize(),13),true,false);
            cell(cs,font,value,x+w*.45f,top,w*.55f,h,row.fontSize(),true,false);top-=h;
        }
        String footer=entry.productName()+"\n"+join(entry.destination(),entry.carrier(),entry.trackingNo());
        cell(cs,font,footer,x,top,w,18*MM,11,false,false);
    }
    private boolean blank(String s) { return s==null||s.isBlank(); }
    private String join(String... values) { return String.join("  ",Arrays.stream(values).filter(v->!blank(v)).toList()); }
    private void cell(PDPageContentStream cs,PDType0Font font,String value,float x,float top,float w,float h,float preferred,boolean centered,boolean yellow) throws IOException {
        cell(cs,font,value,x,top,w,h,preferred,centered,yellow,4,true);
    }
    private void cell(PDPageContentStream cs,PDType0Font font,String value,float x,float top,float w,float h,float preferred,boolean centered,boolean yellow,float verticalPadding,boolean border) throws IOException {
        if(yellow) {cs.setNonStrokingColor(1f,1f,0f);cs.addRect(x,top-h,w,h);cs.fill();}
        cs.setNonStrokingColor(0f);
        if(border) {cs.setStrokingColor(0f);cs.setLineWidth(.5f);cs.addRect(x,top-h,w,h);cs.stroke();}
        float size=preferred;List<String> lines;
        while(true) {
            lines=wrap(font,value,w-10,size);
            if(lines.size()*size*1.25f<=h-2*verticalPadding) break;
            size-=.5f;if(size<7) throw LabelService.invalid("标签文字过长，无法在 100×100 mm 内清晰打印，请缩短名称/编码或调整模板");
        }
        float lineHeight=size*1.25f;
        float y=top-(h-lines.size()*lineHeight)/2-size;
        for(String line:lines) {
            float left=centered?x+(w-font.getStringWidth(line)/1000*size)/2:x+5;
            cs.beginText();cs.setFont(font,size);cs.newLineAtOffset(left,y);cs.showText(line);cs.endText();y-=lineHeight;
        }
    }
    private List<String> wrap(PDType0Font font,String value,float width,float size) throws IOException {
        var result=new ArrayList<String>();
        for(String paragraph:value.split("\\n",-1)) {
            String line="";
            for(int cp:paragraph.codePoints().toArray()) {
                String next=new String(Character.toChars(cp));
                if(!line.isEmpty() && font.getStringWidth(line+next)/1000*size>width) {result.add(line);line="";}
                line+=next;
            }
            result.add(line);
        }
        return result;
    }
}
