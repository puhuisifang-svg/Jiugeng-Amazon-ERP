package com.jiugeng.erp.materialinventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public factory boundary: shared identities, no cost-version mutation. */
@Service @Profile("!mock") @Transactional
public class FactoryOperationDirectory {
    private final JdbcClient jdbc;
    public FactoryOperationDirectory(JdbcClient jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> list() { return jdbc.sql("SELECT id,name,enabled FROM cost_operation ORDER BY name,id").query((r,n)->Map.<String,Object>of("id",r.getString("id"),"name",r.getString("name"),"active",r.getBoolean("enabled"))).list(); }
    public String ensure(String name) {
        var existing=list().stream().filter(x->x.get("name").toString().equalsIgnoreCase(name)).findFirst();
        if(existing.isPresent()) {
            if(!Boolean.TRUE.equals(existing.get().get("active"))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"该工序已停用，请先在产品成本中启用");
            return existing.get().get("id").toString();
        }
        String id=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO cost_operation(id,name,enabled) VALUES(:id,:name,TRUE)").param("id",id).param("name",name).update();return id;
    }
}
