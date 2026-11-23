package com.jiugeng.erp.catalogmedia;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
/** SQL supplied by the owning module; URL predicates and page limits remain parameterized. */
public abstract class JdbcExternalImageSource implements ExternalImageSource {
    private final JdbcClient jdbc;private final String sql,permission;private final boolean lossless;
    protected JdbcExternalImageSource(JdbcClient jdbc,String permission,String sql,boolean lossless){this.jdbc=jdbc;this.permission=permission;this.sql=sql;this.lossless=lossless;}
    public String permission(){return permission;}
    public boolean lossless(){return lossless;}
    public boolean contains(String url){return jdbc.sql("SELECT url FROM ("+sql+") image_source WHERE url=:url LIMIT 1").param("url",url).query(String.class).optional().isPresent();}
    public List<String> page(String after,int limit){return jdbc.sql("SELECT DISTINCT url FROM ("+sql+") image_source WHERE url>:after AND (url LIKE 'https://%' OR url LIKE 'http://%') ORDER BY url LIMIT :limit").param("after",after).param("limit",limit).query(String.class).list();}
}
