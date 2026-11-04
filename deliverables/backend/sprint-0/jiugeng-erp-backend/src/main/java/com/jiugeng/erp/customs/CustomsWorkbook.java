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
 // Write values without changing the template's row heights or text layout.
 private static void put(Sheet s,String address,Object value){CellReference a=new CellReference(address);Cell c=cell(s,a.getRow(),a.getCol());c.setBlank();if(value instanceof LocalDate date)c.setCellValue(date);else if(value instanceof Number n)c.setCellValue(n.doubleValue());else if(value!=null&&!value.toString().isBlank())c.setCellValue(value.toString());}
 private static void textLayout(Sheet s,String address,boolean wrap){CellReference a=new CellReference(address);Cell c=cell(s,a.getRow(),a.getCol());CellStyle style=s.getWorkbook().createCellStyle();style.cloneStyleFrom(c.getCellStyle());style.setRotation((short)0);style.setWrapText(wrap);c.setCellStyle(style);}
 // The supplied template has two SKU blocks. Reduce to one before applying the existing expansion.
 private static void normalizeItems(XSSFSheet s,int start,int block){
  int from=start+block,to=start+2*block-1;
  for(int i=s.getNumMergedRegions()-1;i>=0;i--){var m=s.getMergedRegion(i);if(m.getFirstRow()>=from&&m.getLastRow()<=to)s.removeMergedRegion(i);}
  for(int r=from;r<=to;r++)if(s.getRow(r)!=null)s.removeRow(s.getRow(r));
  s.shiftRows(to+1,s.getLastRowNum(),-block,true,false);
 }
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
   if(n!=2){normalizeItems(c,17,3);normalizeItems(t,19,1);normalizeItems(v,14,1);normalizeItems(p,16,1);
   expand(c,17,3,n,c.getLastRowNum());expand(t,19,1,n,t.getLastRowNum());expand(v,14,1,n,v.getLastRowNum());expand(p,16,1,n,p.getLastRowNum());}
   String[][] fields={{"A4","seller"},{"E4","customsPort"},{"G4","exportDate"},{"L4","declarationDate"},{"O4","recordNo"},{"A6","buyer"},{"E6","transport"},{"G6","voyage"},{"M5","billNo"},{"A8","producer"},{"E8","supervision"},{"G8","taxNature"},{"M7","license"},{"A10","contractNo"},{"E10","tradeCountry"},{"G10","destination"},{"L10","destinationPort"},{"O10","exitPort"},{"A12","packaging"},{"J12","terms"},{"L12","freight"},{"N12","insurance"},{"Q12","otherCharges"},{"A14","documents"},{"D15","marks"}};
   for(String[] x:fields)field(c,x[0],f,x[1]);
   put(c,"A14",singleLineDocuments(f.get("documents")));
   put(c,"E12",sum(items,"cartons"));if("2".equals(f.get("ruleVersion"))){decimal(c,"F12",f,"grossWeight");decimal(c,"G12",f,"netWeight");}else{put(c,"F12",sum(items,"grossWeight"));put(c,"G12",sum(items,"netWeight"));}put(c,"C16","订单编号："+f.get("orderNo")+"  FBA编号："+f.get("fbaNo")+"    FBA仓库代码："+f.get("warehouse"));
   int foot=21+(n-1)*3;put(c,"D"+foot,"特殊关系确认："+f.get("relatedParty"));put(c,"G"+foot,"价格影响确认："+f.get("priceInfluence"));put(c,"K"+foot,"支付特许权使用费确认："+f.get("royalty"));put(c,"P"+foot,"自报自缴："+f.get("selfTax"));if(!f.get("declarant").isBlank())put(c,"A"+(foot+1),"申报人员        申报人员证号            电话："+f.get("declarant"));put(c,"C"+(foot+3),f.get("declarationCompany"));
   field(t,"B4",f,"seller");field(t,"C5",f,"sellerAddress");identifier(t,"C7",f.get("sellerPhone"));field(t,"E7",f,"sellerFax");field(t,"L3",f,"contractNo");put(t,"L5",LocalDate.parse(f.get("date")));field(t,"L7",f,"signPlace");field(t,"B9",f,"buyer");field(t,"C11",f,"buyerAddress");field(t,"C13",f,"buyerPhone");field(t,"I13",f,"buyerFax");
   int offset=n-1;decimal(t,"C"+(22+offset),f,"tolerance");formula(t,"A"+(23+offset),"IF(C"+(22+offset)+"=\"\",\"\",C"+(22+offset)+")");put(t,"C"+(24+offset),"合计美元:");formula(t,"K"+(23+offset),"ROUND(SUM(K20:K"+(19+n)+"),2)");formula(t,"D"+(24+offset),"K"+(23+offset));field(t,"D"+(26+offset),f,"packingMarks");field(t,"D"+(28+offset),f,"tradeTerms");field(t,"D"+(30+offset),f,"destination");put(t,"A"+(32+offset),"（9）付款条件："+f.get("payment"));field(t,"D"+(34+offset),f,"shippingMarks");field(t,"B"+(39+offset),f,"buyer");field(t,"I"+(39+offset),f,"seller");
   field(v,"B6",f,"seller");field(v,"B7",f,"sellerAddress");put(v,"A8","收件人/SHIP TO："+f.get("buyer"));field(v,"B9",f,"buyerAddress");field(v,"G6",f,"invoiceNo");put(v,"G9",LocalDate.parse(f.get("date")));format(w,v,"G9","yyyy/m/d");
   field(p,"B5",f,"seller");field(p,"B6",f,"sellerAddress");field(p,"B7",f,"buyer");field(p,"B8",f,"buyerAddress");put(p,"I6",LocalDate.parse(f.get("date")));field(p,"I7",f,"invoiceNo");field(p,"I9",f,"contractNo");field(p,"I11",f,"payment");field(p,"B11",f,"voyage");
   // Each row is a single SKU/packing line; do not sum quantities with unlike units.
   boolean sameUnit=items.stream().map(r->r.get("unit")).distinct().count()==1;
   for(int i=0;i<n;i++){var row=items.get(i);int cr=18+3*i,tr=20+i,vr=15+i,pr=17+i;
    put(c,"A"+cr,i+1);identifier(c,"B"+cr,row.get("hsCode"));field(c,"D"+cr,row,"name");put(c,"D"+(cr+1),specification(row));textLayout(c,"D"+(cr+1),true);decimal(c,"G"+cr,row,"quantity");field(c,"H"+cr,row,"unit");decimal(c,"I"+cr,row,"price");formula(c,"I"+(cr+1),"ROUND(G"+cr+"*I"+cr+",2)");put(c,"I"+(cr+2),"美元");decimal(c,"G"+(cr+1),row,"quantity2");field(c,"H"+(cr+1),row,"unit2");decimal(c,"G"+(cr+2),row,"quantity3");field(c,"H"+(cr+2),row,"unit3");field(c,"L"+cr,row,"origin");put(c,"M"+cr,value(row,f,"destination"));put(c,"O"+cr,value(row,f,"sourcePlace"));put(c,"R"+cr,value(row,f,"exemption"));
    put(t,"A"+tr,description(row));field(t,"E"+tr,row,"sku");decimal(t,"F"+tr,row,"quantity");field(t,"H"+tr,row,"unit");decimal(t,"I"+tr,row,"price");formula(t,"K"+tr,"ROUND(F"+tr+"*I"+tr+",2)");
    put(v,"A"+vr,i+1);put(v,"B"+vr,description(row));field(v,"C"+vr,row,"sku");decimal(v,"D"+vr,row,"quantity");field(v,"E"+vr,row,"unit");decimal(v,"F"+vr,row,"price");formula(v,"G"+vr,"ROUND(D"+vr+"*F"+vr+",2)");
    identifier(p,"A"+pr,row.get("cartonNo"));put(p,"B"+pr,description(row));field(p,"D"+pr,row,"sku");decimal(p,"E"+pr,row,"cartons");decimal(p,"F"+pr,row,"quantity");field(p,"G"+pr,row,"unit");decimal(p,"H"+pr,row,"grossWeight");decimal(p,"I"+pr,row,"netWeight");
   }
   int vt=15+n,pt=17+n;formula(v,"G"+vt,"ROUND(SUM(G15:G"+(14+n)+"),2)");if(sameUnit){formula(v,"D"+vt,"SUM(D15:D"+(14+n)+")");put(v,"E"+vt,items.get(0).get("unit"));formula(p,"F"+pt,"SUM(F17:F"+(16+n)+")");put(p,"G"+pt,items.get(0).get("unit"));}else{put(v,"D"+vt,"见明细");put(p,"F"+pt,"见明细");}
   for(String col:List.of("E","H","I"))formula(p,col+pt,"SUM("+col+"17:"+col+(16+n)+")");
   if("2".equals(f.get("ruleVersion"))){decimal(p,"H"+pt,f,"grossWeight");decimal(p,"I"+pt,f,"netWeight");}
   // Keep template money formats, with USD rather than the template's erroneous RMB format.
   format(w,t,"D"+(24+offset),"[DBNum2]General;[Red][DBNum2]General");
   elements(w,e,items);
   if(n!=2){for(int i:new int[]{0,2,3,4}){XSSFSheet sheet=w.getSheetAt(i);int end=switch(i){case 0->23+3*offset;case 2->42+offset;case 3->21+offset;default->28+offset;};w.setPrintArea(i,0,new int[]{17,1,11,6,8}[i],0,end);if(n>2){sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);sheet.setFitToPage(true);}}}
   if(n>2){c.setRepeatingRows(new CellRangeAddress(0,16,-1,-1));v.setRepeatingRows(new CellRangeAddress(0,13,-1,-1));p.setRepeatingRows(new CellRangeAddress(0,15,-1,-1));}
   w.getCreationHelper().createFormulaEvaluator().evaluateAll();w.setForceFormulaRecalculation(true);ByteArrayOutputStream out=new ByteArrayOutputStream();w.write(out);return out.toByteArray();
  }catch(IOException e){throw new IllegalStateException("导出失败",e);}
 }
 private static void format(XSSFWorkbook w,Sheet s,String address,String pattern){CellReference a=new CellReference(address);Cell c=cell(s,a.getRow(),a.getCol());CellStyle style=w.createCellStyle();style.cloneStyleFrom(c.getCellStyle());style.setDataFormat(w.createDataFormat().getFormat(pattern));c.setCellStyle(style);}
 private static String description(Map<String,String> r){return r.get("name")+(r.get("nameEn").isBlank()?"":"\n"+r.get("nameEn"));}
 private static String value(Map<String,String> row,Map<String,String> f,String key){return row.get(key).isBlank()?f.get(key):row.get(key);}
 private record Element(String label,String value){}
 private static List<Element> elementValues(Map<String,String> row){
  var out=new ArrayList<Element>();String version=row.get("elementsVersion");
  if("2".equals(version)||"1".equals(version)){
   var fields="2".equals(version)?com.jiugeng.erp.hs.DeclarationSchema.parse(row.get("elementFields")):com.jiugeng.erp.hs.DeclarationSchema.LEGACY;
   for(var field:fields)out.add(new Element(field.key().equals("brand")?"品牌":field.label(),Objects.toString(row.get(field.key()),"").replace('，',',')));
  }else for(String line:Objects.toString(row.get("declarationElements"),"").split("\\R")){String[] pair=line.split("[:：]",2);out.add(new Element(pair.length==2?pair[0]:"申报要素",pair.length==2?pair[1]:line));}
  return out;
 }
 static String specification(Map<String,String> row){
  return elementValues(row).stream().map(e->{String value=e.value().trim().replaceAll("\\R+"," ").replace('，',',');return value.isEmpty()&&Set.of("GTIN","CAS","其他").contains(e.label().trim())?"0":value;}).collect(java.util.stream.Collectors.joining("|"));
 }
 private void elements(XSSFWorkbook w,XSSFSheet s,List<Map<String,String>> items){
  var layouts=new HashMap<String,org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRow>();
  var styles=new HashMap<String,List<CellStyle>>();
  for(int i=1;i<=10;i++){var row=s.getRow(i);String label=row.getCell(0).getStringCellValue();layouts.put(label,(org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRow)row.getCTRow().copy());styles.put(label,List.of(row.getCell(0).getCellStyle(),row.getCell(1).getCellStyle()));}
  for(int r=s.getLastRowNum();r>=1;r--)if(s.getRow(r)!=null)s.removeRow(s.getRow(r));
  int r=1;
  for(var item:items){var pairs=new ArrayList<Element>();pairs.add(new Element("HS",item.get("hsCode")));pairs.add(new Element("品名",item.get("name")));pairs.addAll(elementValues(item));
   for(var pair:pairs){String key=layouts.containsKey(pair.label())?pair.label():"HS";var layout=(org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRow)layouts.get(key).copy();layout.setCArray(new org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCell[0]);layout.setR(r+1);var row=s.createRow(r++);row.getCTRow().set(layout);
    Cell a=row.createCell(0),b=row.createCell(1);a.setCellStyle(styles.get(key).get(0));b.setCellStyle(styles.get(key).get(1));a.setCellValue(pair.label());if(pair.label().equals("HS"))identifier(s,"B"+r,pair.value());else if(pair.value()!=null&&!pair.value().isBlank())b.setCellValue(pair.value());
    int lines=Math.max(lines(pair.label(),s.getColumnWidth(0)),lines(pair.value(),s.getColumnWidth(1)));
    if(lines>1){textLayout(s,"A"+r,true);textLayout(s,"B"+r,true);row.setHeightInPoints(Math.min(400,row.getHeightInPoints()*lines));}
   }
   r+=2;
  }
 }
 private static String singleLineDocuments(String value){var parts=Objects.toString(value,"").split("\\R+");if(parts.length<2)return Objects.toString(value,"");return parts[0]+"       "+String.join("    ",Arrays.copyOfRange(parts,1,parts.length));}
 private static void identifier(Sheet s,String address,String value){if(value!=null&&value.matches("[1-9][0-9]{0,14}"))put(s,address,new BigDecimal(value));else put(s,address,value);}
 private static int lines(String value,int width){int total=0;for(String line:Objects.toString(value,"").split("\\R",-1))total+=Math.max(1,(int)Math.ceil(line.codePoints().map(c->c>255?2:1).sum()/Math.max(1,width/256.0-1)));return total;}
}
