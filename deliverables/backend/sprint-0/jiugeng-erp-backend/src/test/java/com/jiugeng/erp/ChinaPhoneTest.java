package com.jiugeng.erp;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ChinaPhoneTest {
 @Test void validatesChineseContactNumbers(){
  for(String s:new String[]{"","13800138000","+86 13800138000","008613800138000","0592-1234567","010-87654321-123","05921234567","400-123-4567","10086"})assertThat(ChinaPhone.contact(s)).as(s).isTrue();
  for(String s:new String[]{"你好","电话13800138000","123","12345678901","1380013800","138001380001","0592-123","138\n00138000","+1-555-1234567"})assertThat(ChinaPhone.contact(s)).as(s).isFalse();
  assertThat(ChinaPhone.mobile("0592-1234567")).isFalse();
 }
}
