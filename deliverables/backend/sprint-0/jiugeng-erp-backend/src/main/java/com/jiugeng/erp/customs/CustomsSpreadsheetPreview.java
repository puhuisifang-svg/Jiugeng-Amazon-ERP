package com.jiugeng.erp.customs;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Read-only values: use saved formula results, never execute macros or external references. */
final class CustomsSpreadsheetPreview {
 record Sheet(String name,List<String> columns,List<List<String>> rows,boolean truncated){}
 record Preview(List<Sheet> sheets,boolean truncated){}
 static Preview read(byte[] bytes){
  try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(bytes))){
   var formatter=new DataFormatter(Locale.CHINA);formatter.setUseCachedValuesForFormulaCells(true);
   List<Sheet> sheets=new ArrayList<>();boolean truncated=workbook.getNumberOfSheets()>5;
   for(int index=0;index<Math.min(workbook.getNumberOfSheets(),5);index++){
    var sheet=workbook.getSheetAt(index);int height=sheet.getPhysicalNumberOfRows()==0?0:sheet.getLastRowNum()+1;
    boolean clipped=height>200;int width=1;
    for(int r=0;r<Math.min(height,200);r++){var row=sheet.getRow(r);if(row!=null)width=Math.max(width,row.getLastCellNum());}
    clipped|=width>30;width=Math.min(width,30);
    List<String> columns=new ArrayList<>();for(int c=0;c<width;c++)columns.add(CellReference.convertNumToColString(c));
    List<List<String>> rows=new ArrayList<>();
    for(int r=0;r<Math.min(height,200);r++){
     List<String> values=new ArrayList<>();var row=sheet.getRow(r);
     for(int c=0;c<width;c++){
      String value=formatter.formatCellValue(row==null?null:row.getCell(c));
      if(value.length()>300){value=value.substring(0,300)+"…";clipped=true;}
      values.add(value);
     }
     rows.add(values);
    }
    sheets.add(new Sheet(sheet.getSheetName(),columns,rows,clipped));truncated|=clipped;
   }
   return new Preview(sheets,truncated);
  }catch(Exception error){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"表格无法预览，请下载原文件查看");}
 }
}
