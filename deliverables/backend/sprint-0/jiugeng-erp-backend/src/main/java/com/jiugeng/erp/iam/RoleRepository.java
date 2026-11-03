package com.jiugeng.erp.iam;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RoleRepository {
    record RoleRef(String id, String name, String status) {}
    record RoleView(String id, String name, String description, String kind, String status, long version,
                    List<String> permissions, int memberCount, Instant updatedAt) {}
    private final JdbcClient jdbc;
    RoleRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    void lock() { jdbc.sql("SELECT id FROM iam_authorization_lock WHERE id = 1 FOR UPDATE").query(Integer.class).single(); }
    void requireAdministrator(String userId) {
        int count=jdbc.sql("SELECT COUNT(*) FROM iam_user u JOIN iam_user_role ur ON ur.user_id=u.id WHERE u.id=:id AND ur.role_id='ADMIN' AND u.deleted_at IS NULL AND u.status='ENABLED' AND u.locked_at IS NULL").param("id",userId).query(Integer.class).single();
        if(count!=1) throw new IamException(403,"AUTHORIZATION_REVOKED","管理权限已变化，请重新登录");
    }
    List<RoleRef> userRoles(String userId) {
        return jdbc.sql("SELECT r.id,r.name,r.status FROM iam_role r JOIN iam_user_role ur ON r.id=ur.role_id WHERE ur.user_id=:id AND r.deleted_at IS NULL ORDER BY r.name")
            .param("id",userId).query((rs,n)->new RoleRef(rs.getString(1),rs.getString(2),rs.getString(3))).list();
    }
    Map<String,List<RoleRef>> sources(String userId) {
        var result = new TreeMap<String,List<RoleRef>>();
        for (var role : userRoles(userId)) if (role.status().equals("ENABLED")) {
            for (String code : permissions(role.id())) result.computeIfAbsent(code,k->new ArrayList<>()).add(role);
        }
        return result;
    }
    Set<String> capabilities(String userId) {
        var result = new TreeSet<>(sources(userId).keySet());
        return result;
    }
    List<String> permissions(String id) {
        return jdbc.sql("SELECT permission_code FROM iam_role_permission WHERE role_id=:id ORDER BY permission_code")
            .param("id",id).query(String.class).list();
    }
    List<RoleView> list() {
        return jdbc.sql("SELECT * FROM iam_role WHERE deleted_at IS NULL ORDER BY CASE WHEN id='ADMIN' THEN 0 ELSE 1 END,kind,name,id").query((rs,n)->new RoleView(
            rs.getString("id"),rs.getString("name"),rs.getString("description"),rs.getString("kind"),rs.getString("status"),rs.getLong("version"),
            permissions(rs.getString("id")),memberIds(rs.getString("id")).size(),rs.getTimestamp("updated_at").toInstant())).list();
    }
    RoleView require(String id) { return list().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(()->new IamException(404,"ROLE_NOT_FOUND","角色不存在")); }
    List<String> memberIds(String id) {
        return jdbc.sql("SELECT u.id FROM iam_user u JOIN iam_user_role ur ON ur.user_id=u.id WHERE ur.role_id=:id AND u.deleted_at IS NULL ORDER BY u.id")
            .param("id",id).query(String.class).list();
    }
    void bind(String userId, Collection<String> ids) {
        var touched=new TreeSet<>(ids); userRoles(userId).forEach(r->touched.add(r.id()));
        for(var id:touched) jdbc.sql("UPDATE iam_role SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id").param("id",id).update();
        jdbc.sql("DELETE FROM iam_user_role WHERE user_id=:id").param("id",userId).update();
        for (var id : ids) jdbc.sql("INSERT INTO iam_user_role(user_id,role_id) VALUES (:u,:r)").param("u",userId).param("r",id).update();
        String legacy = ids.size()==1 && List.of("ADMIN","PRODUCT_MATERIAL","SELECTION").contains(ids.iterator().next()) ? ids.iterator().next() : "CUSTOM";
        jdbc.sql("UPDATE iam_user SET role=:role,authorization_version=authorization_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id")
            .param("role",legacy).param("id",userId).update();
    }
    void guardAvailableAdministrator() {
        int count=jdbc.sql("SELECT COUNT(*) FROM iam_user u JOIN iam_user_role ur ON ur.user_id=u.id WHERE ur.role_id='ADMIN' AND u.deleted_at IS NULL AND u.status='ENABLED' AND u.locked_at IS NULL")
            .query(Integer.class).single();
        if(count<1) throw new IamException(409,"LAST_ADMIN_REQUIRED","必须保留至少一名启用且未锁定的系统管理员");
    }

}
