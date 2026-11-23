package com.jiugeng.erp.monitoring;
import com.jiugeng.erp.catalogmedia.JsonExternalImageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
@org.springframework.stereotype.Component
class MonitoringExternalImages extends JsonExternalImageSource {
    MonitoringExternalImages(JdbcClient jdbc,ObjectMapper json){super(jdbc,json,"monitoring.read","SELECT CONCAT('b-',id) id,baseline_json document FROM monitor_target WHERE removed=FALSE UNION ALL SELECT CONCAT('s-',s.id) id,s.fields_json document FROM monitor_snapshot s JOIN monitor_target t ON t.id=s.target_id WHERE t.removed=FALSE UNION ALL SELECT CONCAT('c-',s.id) id,s.changes_json document FROM monitor_snapshot s JOIN monitor_target t ON t.id=s.target_id WHERE t.removed=FALSE");}
}
