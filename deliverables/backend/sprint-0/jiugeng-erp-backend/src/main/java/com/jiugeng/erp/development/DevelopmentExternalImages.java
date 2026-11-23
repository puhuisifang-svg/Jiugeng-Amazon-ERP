package com.jiugeng.erp.development;
import com.jiugeng.erp.catalogmedia.JsonExternalImageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
@org.springframework.stereotype.Component
class DevelopmentExternalImages extends JsonExternalImageSource {
    DevelopmentExternalImages(JdbcClient jdbc,ObjectMapper json){super(jdbc,json,"development.read","SELECT w.entity_id id,w.document_json document FROM launch_workspace w JOIN product_development d ON d.id=w.entity_id WHERE w.kind='DEVELOPMENT' AND d.deleted_at IS NULL");}
}
