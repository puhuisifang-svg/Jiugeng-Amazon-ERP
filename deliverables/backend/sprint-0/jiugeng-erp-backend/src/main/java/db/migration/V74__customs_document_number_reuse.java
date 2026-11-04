package db.migration;

import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Deleted documents keep their original number, but no longer reserve it for live documents. */
public class V74__customs_document_number_reuse extends BaseJavaMigration {
 @Override public Integer getChecksum(){return 74001;}
 @Override public void migrate(Context context)throws Exception{
  var db=context.getConnection();boolean mysql=db.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
  var constraints=new ArrayList<String>();
  String query="SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku ON tc.CONSTRAINT_SCHEMA=ku.CONSTRAINT_SCHEMA AND tc.TABLE_NAME=ku.TABLE_NAME AND tc.CONSTRAINT_NAME=ku.CONSTRAINT_NAME WHERE UPPER(tc.TABLE_NAME)='CUSTOMS_DOCUMENT' AND tc.CONSTRAINT_TYPE='UNIQUE' AND tc.TABLE_SCHEMA=? GROUP BY tc.CONSTRAINT_NAME HAVING COUNT(*)=1 AND UPPER(MAX(ku.COLUMN_NAME))='DOCUMENT_NO'";
  try(var q=db.prepareStatement(query)){q.setString(1,mysql?db.getCatalog():db.getSchema());try(var r=q.executeQuery()){while(r.next())constraints.add(r.getString(1));}}
  if(constraints.size()!=1)throw new IllegalStateException("Expected one document-number unique constraint");
  String name=constraints.getFirst();if(!name.matches("[A-Za-z0-9_]+"))throw new IllegalStateException("Unexpected constraint identifier");
  try(var s=db.createStatement()){
   s.execute("ALTER TABLE customs_document ADD COLUMN deletion_key VARCHAR(36) NOT NULL DEFAULT ''");
   s.execute("UPDATE customs_document SET deletion_key=id WHERE deleted_at IS NOT NULL");
   s.execute("ALTER TABLE customs_document ADD CONSTRAINT uk_customs_document_live_number UNIQUE(document_no,deletion_key)");
   s.execute(mysql?"ALTER TABLE customs_document DROP INDEX `"+name+"`":"ALTER TABLE customs_document DROP CONSTRAINT \""+name+"\"");
  }
 }
}
