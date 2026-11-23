package com.jiugeng.erp.selection;
import com.jiugeng.erp.catalogmedia.JsonExternalImageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
@org.springframework.stereotype.Component
class SelectionExternalImages extends JsonExternalImageSource {
    SelectionExternalImages(JdbcClient jdbc,ObjectMapper json){super(jdbc,json,"selection.read","SELECT CONCAT('w-',w.entity_id) id,w.document_json document FROM launch_workspace w JOIN selection_candidate c ON c.id=w.entity_id WHERE w.kind='CANDIDATE' AND c.deleted_at IS NULL UNION ALL SELECT CONCAT('o-',o.entity_id) id,o.observation_json document FROM launch_observation o JOIN selection_candidate c ON c.id=o.entity_id WHERE c.deleted_at IS NULL UNION ALL SELECT CONCAT('m-',m.id) id,m.baseline_json document FROM monitor_target m WHERE m.removed=FALSE AND EXISTS(SELECT 1 FROM selection_candidate c WHERE c.asin=m.asin AND c.deleted_at IS NULL)");}
}
