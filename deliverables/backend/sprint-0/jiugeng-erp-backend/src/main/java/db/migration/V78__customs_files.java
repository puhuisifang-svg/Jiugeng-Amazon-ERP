package db.migration;
import org.flywaydb.core.api.migration.*;
import com.fasterxml.jackson.databind.ObjectMapper;
public class V78__customs_files extends BaseJavaMigration {
 public void migrate(Context context)throws Exception{
  var c=context.getConnection();try(var s=c.createStatement()){
   s.execute("ALTER TABLE customs_document ADD COLUMN fba_no VARCHAR(4000) NOT NULL DEFAULT ''");
   s.execute("CREATE TABLE customs_attachment(id VARCHAR(36) PRIMARY KEY,document_id VARCHAR(36) NOT NULL,category VARCHAR(16) NOT NULL,file_name VARCHAR(200) NOT NULL,media_type VARCHAR(100) NOT NULL,file_data LONGBLOB NOT NULL,byte_size BIGINT NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,FOREIGN KEY(document_id) REFERENCES customs_document(id) ON DELETE CASCADE)");
   s.execute("CREATE INDEX customs_attachment_document ON customs_attachment(document_id,category)");
  }
  var json=new ObjectMapper();try(var s=c.createStatement();var r=s.executeQuery("SELECT id,payload FROM customs_document");var u=c.prepareStatement("UPDATE customs_document SET fba_no=? WHERE id=?")){
   while(r.next()){u.setString(1,json.readTree(r.getString(2)).path("fields").path("fbaNo").asText(""));u.setString(2,r.getString(1));u.executeUpdate();}
  }
 }
}
