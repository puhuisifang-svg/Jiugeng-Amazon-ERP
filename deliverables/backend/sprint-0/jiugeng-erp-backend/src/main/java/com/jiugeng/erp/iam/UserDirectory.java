package com.jiugeng.erp.iam;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
@Service
public class UserDirectory {
 private final JdbcClient jdbc;
 public UserDirectory(JdbcClient jdbc){this.jdbc=jdbc;}
 public record Entry(String id,String loginName,String displayName){}
 public List<Entry> active(){return jdbc.sql("SELECT id,login_name,display_name FROM iam_user WHERE deleted_at IS NULL AND status='ENABLED' ORDER BY login_name").query((r,n)->new Entry(r.getString(1),r.getString(2),r.getString(3))).list();}
}
