package com.jiugeng.erp.customs;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.DriverManager;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import db.migration.V74__customs_document_number_reuse;
class CustomsNumberMigrationTest {
 @Test void existingDeletedNumbersAreReleasedWithoutChangingHistory()throws Exception{
  try(var db=DriverManager.getConnection("jdbc:h2:mem:customs_number_migration;MODE=MySQL")){try(var s=db.createStatement()){
   s.execute("CREATE TABLE customs_document(id VARCHAR(36) PRIMARY KEY,document_no VARCHAR(128) NOT NULL UNIQUE,deleted_at TIMESTAMP NULL)");
   s.execute("INSERT INTO customs_document VALUES('old','SYN-REUSE',CURRENT_TIMESTAMP),('live','SYN-LIVE',NULL)");
   Context context=mock(Context.class);when(context.getConnection()).thenReturn(db);new V74__customs_document_number_reuse().migrate(context);
   s.execute("INSERT INTO customs_document(id,document_no) VALUES('new','SYN-REUSE')");
   assertThatThrownBy(()->s.execute("INSERT INTO customs_document(id,document_no) VALUES('duplicate','SYN-LIVE')")).isInstanceOf(java.sql.SQLException.class);
   try(var r=s.executeQuery("SELECT document_no,deletion_key FROM customs_document WHERE id='old'")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("SYN-REUSE");assertThat(r.getString(2)).isEqualTo("old");}
  }}
 }
}
