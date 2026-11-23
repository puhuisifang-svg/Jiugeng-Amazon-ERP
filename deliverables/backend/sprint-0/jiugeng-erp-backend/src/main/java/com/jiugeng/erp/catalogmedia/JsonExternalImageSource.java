package com.jiugeng.erp.catalogmedia;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Bounded keyset scans of a domain's existing JSON documents; never rewrites business history. */
public abstract class JsonExternalImageSource implements ExternalImageSource {
    private final JdbcClient jdbc;private final ObjectMapper json;private final String permission,sql;
    protected JsonExternalImageSource(JdbcClient jdbc,ObjectMapper json,String permission,String sql){this.jdbc=jdbc;this.json=json;this.permission=permission;this.sql=sql;}
    public String permission(){return permission;}
    public boolean contains(String url){
        String encoded;try{encoded=json.writeValueAsString(url);}catch(java.io.IOException e){return false;}
        return jdbc.sql("SELECT document FROM ("+sql+") source_document WHERE document LIKE :pattern ESCAPE '!'").param("pattern","%"+encoded.replace("!","!!").replace("%","!%").replace("_","!_")+"%")
            .query((r,n)->urls(r.getString(1)).contains(url)).list().stream().anyMatch(Boolean::booleanValue);
    }
    public List<String> page(String after,int limit){return scan(after,limit).urls();}
    @Override public Batch scan(String after,int limit){
        var rows=jdbc.sql("SELECT id,document FROM ("+sql+") source_document WHERE id>:after ORDER BY id LIMIT :limit").param("after",after).param("limit",limit).query((r,n)->new String[]{r.getString(1),r.getString(2)}).list();
        var urls=new LinkedHashSet<String>();for(var row:rows)urls.addAll(urls(row[1]));
        return new Batch(List.copyOf(urls),rows.isEmpty()?"":rows.get(rows.size()-1)[0]);
    }
    private Set<String> urls(String value){var result=new LinkedHashSet<String>();try{collect(json.readTree(value==null?"{}":value),false,result);}catch(java.io.IOException ignored){}return result;}
    private void collect(JsonNode node,boolean image,Set<String> result){
        if(node==null)return;
        if(node.isTextual()&&image&&(node.asText().startsWith("https://")||node.asText().startsWith("http://")))result.add(node.asText());
        else if(node.isArray())node.forEach(n->collect(n,image,result));
        else if(node.isObject()){
            boolean change="imageUrl".equals(node.path("field").asText());
            node.fields().forEachRemaining(e->collect(e.getValue(),image||change||e.getKey().equals("imageUrl")||e.getKey().equals("image_url")||e.getKey().equals("image"),result));
        }
    }
}
