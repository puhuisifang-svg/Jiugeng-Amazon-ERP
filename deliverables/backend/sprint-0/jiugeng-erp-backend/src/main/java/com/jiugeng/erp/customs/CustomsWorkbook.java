package com.jiugeng.erp.customs;

import java.io.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

@Component
public class CustomsWorkbook {
 private static Cell cell(Sheet s,int row,int col){Row r=s.getRow(row);if(r==null)r=s.createRow(row);Cell c=r.getCell(col);return c==null?r.createCell(col):c;}
 private static void put(Sheet s,String address,Object value){CellReference a=new CellReference(address);Cell c=cell(s,a.getRow(),a.getCol());c.setBlank();if(value instanceof Number n)c.setCellValue(n.doubleValue());else if(value!=null&&!value.toString().isBlank()){c.setCellValue(value.toString());CellStyle style=s.getWorkbook().createCellStyle();style.cloneStyleFrom(c.getCellStyle());style.setWrapText(true);c.setCellStyle(style);int endCol=a.getCol(),endRow=a.getRow();for(var merged:s.getMergedRegions())if(merged.isInRange(a.getRow(),a.getCol())){endCol=merged.getLastColumn();endRow=merged.getLastRow();break;}double width=0;for(int col=a.getCol();col<=endCol;col++)width+=s.getColumnWidth(col)/256.0;int lines=0;for(String line:value.toString().split("\\R",-1)){int chars=line.codePoints().map(cp->cp>255?2:1).sum();lines+=Math.max(1,(int)Math.ceil(chars/Math.max(1,width-2)));}float height=(float)(lines*15.0/(endRow-a.getRow()+1));for(int r=a.getRow();r<=endRow;r++){Row row=s.getRow(r);if(row==null)row=s.createRow(r);row.setHeightInPoints(Math.max(row.getHeightInPoints(),Math.min(400,height)));}}}
 private static void formula(Sheet s,String address,String value){CellReference a=new CellReference(address);cell(s,a.getRow(),a.getCol()).setCellFormula(value);}
 private static void field(Sheet s,String address,Map<String,String> f,String key){put(s,address,f.get(key));}
 private static void decimal(Sheet s,String address,Map<String,String> f,String key){String v=f.get(key);if(v==null||v.isBlank())put(s,address,"");else put(s,address,new BigDecimal(v));}
 private static BigDecimal sum(List<Map<String,String>> rows,String key){return rows.stream().map(r->new BigDecimal(r.get(key))).reduce(BigDecimal.ZERO,BigDecimal::add);}
 // Expand template item blocks before writing any formulas, keeping footer merges and styles.
 private static void expand(XSSFSheet s,int start,int block,int count,int end){int extra=(count-1)*block;if(extra<=0)return;
  List<CellRangeAddress> merges=s.getMergedRegions().stream().filter(m->m.getFirstRow()>=start&&m.getLastRow()<start+block).map(CellRangeAddress::copy).toList();
  s.shiftRows(start+block,end,extra,true,false);
  for(int i=1;i<count;i++){for(int k=0;k<block;k++){Row src=s.getRow(start+k),dst=s.createRow(start+i*block+k);dst.setHeight(src.getHeight());for(Cell c:src){Cell out=dst.createCell(c.getColumnIndex());out.setCellStyle(c.getCellStyle());}}
   for(var m:merges)s.addMergedRegion(new CellRangeAddress(m.getFirstRow()+i*block,m.getLastRow()+i*block,m.getFirstColumn(),m.getLastColumn()));}
 }
 public byte[] export(CustomsDocuments.Document d){
  var in=new CustomsDocuments.Input(d.documentNo(),d.status(),d.fields(),d.items(),d.version());CustomsDocuments.complete(in);
  try(InputStream resource=getClass().getResourceAsStream("/templates/customs-document.xlsx");XSSFWorkbook w=new XSSFWorkbook(Objects.requireNonNull(resource))){
   var f=d.fields();var items=d.items();int n=items.size();XSSFSheet c=w.getSheet("报关单"),e=w.getSheet("申报要素"),t=w.getSheet("合同"),v=w.getSheet("发票"),p=w.getSheet("装箱单");
   for(String range:List.of("B4:G4","E7:G8","D26:K27","D28:K29","D30:F30","D34:K35"))t.addMergedRegion(CellRangeAddress.valueOf(range));
   for(String range:List.of("B6:E6","B7:E7","A8:E8"))v.addMergedRegion(CellRangeAddress.valueOf(range));
   for(String range:List.of("B5:F5","B7:F7","B11:D12"))p.addMergedRegion(CellRangeAddress.valueOf(range));
   expand(c,17,3,n,23);expand(t,19,1,n,42);expand(v,14,1,n,21);expand(p,16,1,n,28);
   String[][] fields={{"A4","seller"},{"E4","customsPort"},{"G4","exportDate"},{"L4","declarationDate"},{"O4","recordNo"},{"A6","buyer"},{"E6","transport"},{"G6","voyage"},{"M5","billNo"},{"A8","producer"},{"E8","supervision"},{"G8","taxNature"},{"M7","license"},{"A10","contractNo"},{"E10","tradeCountry"},{"G10","destination"},{"L10","destinationPort"},{"O10","exitPort"},{"A12","packaging"},{"J12","terms"},{"L12","freight"},{"N12","insurance"},{"Q12","otherCharges"},{"A14","documents"},{"D15","marks"}};
   for(String[] x:fields)field(c,x[0],f,x[1]);put(c,"E12",sum(items,"cartons"));put(c,"F12",sum(items,"grossWeight"));put(c,"G12",sum(items,"netWeight"));put(c,"C16","订单编号："+f.get("orderNo")+"  FBA编号："+f.get("fbaNo")+"  FBA仓库代码："+f.get("warehouse"));
   int foot=21+(n-1)*3;put(c,"D"+foot,"特殊关系确认："+f.get("relatedParty"));put(c,"G"+foot,"价格影响确认："+f.get("priceInfluence"));put(c,"K"+foot,"支付特许权使用费确认："+f.get("royalty"));put(c,"P"+foot,"自报自缴："+f.get("selfTax"));put(c,"A"+(foot+1),"申报人员 / 证号 / 电话："+f.get("declarant"));put(c,"C"+(foot+3),f.get("declarationCompany"));
   field(t,"B4",f,"seller");field(t,"C5",f,"sellerAddress");field(t,"C7",f,"sellerPhone");field(t,"E7",f,"sellerFax");field(t,"L3",f,"contractNo");put(t,"L5",LocalDate.parse(f.get("date")).toString());field(t,"L7",f,"signPlace");field(t,"B9",f,"buyer");field(t,"C11",f,"buyerAddress");field(t,"C13",f,"buyerPhone");field(t,"I13",f,"buyerFax");
   int offset=n-1;field(t,"C"+(22+offset),f,"tolerance");formula(t,"A"+(23+offset),"IF(C"+(22+offset)+"=\"\",\"\",C"+(22+offset)+")");put(t,"C"+(24+offset),"合计 "+f.get("currency")+"：");formula(t,"K"+(23+offset),"ROUND(SUM(K20:K"+(19+n)+"),2)");formula(t,"D"+(24+offset),"K"+(23+offset));field(t,"D"+(26+offset),f,"packingMarks");field(t,"D"+(28+offset),f,"tradeTerms");field(t,"D"+(30+offset),f,"destination");put(t,"A"+(32+offset),"（9）付款条件："+f.get("payment"));field(t,"D"+(34+offset),f,"shippingMarks");field(t,"B"+(39+offset),f,"buyer");field(t,"I"+(39+offset),f,"seller");
   field(v,"B6",f,"seller");field(v,"B7",f,"sellerAddress");put(v,"A8","收件人/SHIP TO："+f.get("buyer"));field(v,"B9",f,"buyerAddress");field(v,"G6",f,"invoiceNo");field(v,"G9",f,"date");put(v,"G13","总金额（"+f.get("currency")+"）");
   field(p,"B5",f,"seller");field(p,"B6",f,"sellerAddress");field(p,"B7",f,"buyer");field(p,"B8",f,"buyerAddress");field(p,"I6",f,"date");field(p,"I7",f,"invoiceNo");field(p,"I9",f,"contractNo");field(p,"I11",f,"payment");field(p,"B11",f,"voyage");
   // Each row is a single SKU/packing line; do not sum quantities with unlike units.
   boolean sameUnit=items.stream().map(r->r.get("unit")).distinct().count()==1;
   for(int i=0;i<n;i++){var row=items.get(i);int cr=18+3*i,tr=20+i,vr=15+i,pr=17+i;
    put(c,"A"+cr,i+1);field(c,"B"+cr,row,"hsCode");field(c,"D"+cr,row,"name");field(c,"D"+(cr+1),row,"declarationElements");decimal(c,"G"+cr,row,"quantity");field(c,"H"+cr,row,"unit");decimal(c,"I"+cr,row,"price");formula(c,"I"+(cr+1),"ROUND(G"+cr+"*I"+cr+",2)");field(c,"I"+(cr+2),f,"currency");decimal(c,"G"+(cr+1),row,"quantity2");field(c,"H"+(cr+1),row,"unit2");decimal(c,"G"+(cr+2),row,"quantity3");field(c,"H"+(cr+2),row,"unit3");field(c,"L"+cr,row,"origin");put(c,"M"+cr,value(row,f,"destination"));put(c,"O"+cr,value(row,f,"sourcePlace"));put(c,"R"+cr,value(row,f,"exemption"));
    put(t,"A"+tr,description(row));field(t,"E"+tr,row,"sku");decimal(t,"F"+tr,row,"quantity");field(t,"H"+tr,row,"unit");decimal(t,"I"+tr,row,"price");formula(t,"K"+tr,"ROUND(F"+tr+"*I"+tr+",2)");
    put(v,"A"+vr,i+1);put(v,"B"+vr,description(row));field(v,"C"+vr,row,"sku");decimal(v,"D"+vr,row,"quantity");field(v,"E"+vr,row,"unit");decimal(v,"F"+vr,row,"price");formula(v,"G"+vr,"ROUND(D"+vr+"*F"+vr+",2)");
    field(p,"A"+pr,row,"cartonNo");put(p,"B"+pr,description(row));field(p,"D"+pr,row,"sku");decimal(p,"E"+pr,row,"cartons");decimal(p,"F"+pr,row,"quantity");field(p,"G"+pr,row,"unit");decimal(p,"H"+pr,row,"grossWeight");decimal(p,"I"+pr,row,"netWeight");
   }
   int vt=15+n,pt=17+n;formula(v,"G"+vt,"ROUND(SUM(G15:G"+(14+n)+"),2)");if(sameUnit){formula(v,"D"+vt,"SUM(D15:D"+(14+n)+")");put(v,"E"+vt,items.get(0).get("unit"));formula(p,"F"+pt,"SUM(F17:F"+(16+n)+")");put(p,"G"+pt,items.get(0).get("unit"));}else{put(v,"D"+vt,"见明细");put(p,"F"+pt,"见明细");}
   for(String col:List.of("E","H","I"))formula(p,col+pt,"SUM("+col+"17:"+col+(16+n)+")");
   // Currency is carried by labels, not template-specific dollar/RMB number formats.
   for(int i=0;i<n;i++){format(w,t,"I"+(20+i),"0.0000");format(w,t,"K"+(20+i),"0.00");format(w,v,"F"+(15+i),"0.0000");format(w,v,"G"+(15+i),"0.00");}
   format(w,t,"K"+(23+offset),"0.00");format(w,t,"D"+(24+offset),"[DBNum2]0.00");format(w,v,"G"+vt,"0.00");
   elements(w,e,items);
   for(int i=0;i<5;i++){XSSFSheet sheet=w.getSheetAt(i);sheet.setDisplayGridlines(false);sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);sheet.getPrintSetup().setLandscape(i==0);sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);sheet.setFitToPage(true);sheet.setAutobreaks(true);sheet.setMargin(Sheet.LeftMargin,0.2);sheet.setMargin(Sheet.RightMargin,0.2);int end=switch(i){case 0->23+3*offset;case 1->sheet.getLastRowNum();case 2->42+offset;case 3->21+offset;default->28+offset;};w.setPrintArea(i,0,new int[]{17,1,11,6,8}[i],0,end);}
   c.setRepeatingRows(new CellRangeAddress(0,16,-1,-1));v.setRepeatingRows(new CellRangeAddress(0,13,-1,-1));p.setRepeatingRows(new CellRangeAddress(0,15,-1,-1));
   w.getCreationHelper().createFormulaEvaluator().evaluateAll();w.setForceFormulaRecalculation(true);ByteArrayOutputStream out=new ByteArrayOutputStream();w.write(out);return out.toByteArray();
  }catch(IOException e){throw new IllegalStateException("导出失败",e);}
 }
 private static void format(XSSFWorkbook w,Sheet s,String address,String pattern){CellReference a=new CellReference(address);Cell c=cell(s,a.getRow(),a.getCol());CellStyle style=w.createCellStyle();style.cloneStyleFrom(c.getCellStyle());style.setDataFormat(w.createDataFormat().getFormat(pattern));c.setCellStyle(style);}
 private static String description(Map<String,String> r){return r.get("name")+(r.get("nameEn").isBlank()?"":"\n"+r.get("nameEn"));}
 private static String value(Map<String,String> row,Map<String,String> f,String key){return row.get(key).isBlank()?f.get(key):row.get(key);}
 private void elements(XSSFWorkbook w,XSSFSheet s,List<Map<String,String>> items){
  CellStyle label=cell(s,1,0).getCellStyle(),content=cell(s,1,1).getCellStyle();for(int r=s.getLastRowNum();r>=0;r--)if(s.getRow(r)!=null)s.removeRow(s.getRow(r));for(int i=s.getNumMergedRegions()-1;i>=0;i--)s.removeMergedRegion(i);
  s.setColumnWidth(0,24*256);s.setColumnWidth(1,75*256);int r=0;
  for(var row:items){String[][] head={{"商品 / SKU",row.get("sku")},{"HS",row.get("hsCode")},{"品名",row.get("name")}};for(String[] pair:head){put(s,"A"+(++r),pair[0]);put(s,"B"+r,pair[1]);}
   for(String line:row.get("declarationElements").split("\\R")){String[] pair=line.split("[:：]",2);put(s,"A"+(++r),pair.length==2?pair[0]:"申报要素");put(s,"B"+r,pair.length==2?pair[1]:line);}r++;
  }
  for(Row row:s){row.setHeightInPoints(32);for(Cell c:row){c.setCellStyle(c.getColumnIndex()==0?label:content);}}
 }
}
