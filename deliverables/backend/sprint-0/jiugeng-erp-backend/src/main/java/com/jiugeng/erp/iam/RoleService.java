package com.jiugeng.erp.iam;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import static com.jiugeng.erp.iam.IamTypes.*;
import static com.jiugeng.erp.iam.RoleRepository.*;

@Service
@Transactional
class RoleService {
    record Edit(String name, String description, String status, List<String> permissions, long version, String reason) {}
    record Assignment(List<String> roleIds, long version, String reason) {}
    record Preview(List<String> added, List<String> removed, List<UserView> members) {}
    private final RoleRepository roles;
    private final IamRepository users;
    private final JdbcClient jdbc;
    private final AuditService audit;
    RoleService(RoleRepository roles, IamRepository users, JdbcClient jdbc, AuditService audit) {
        this.roles=roles; this.users=users; this.jdbc=jdbc; this.audit=audit;
    }
    List<RoleView> list() { return roles.list(); }
    List<UserView> members(String id) { roles.require(id); return roles.memberIds(id).stream().map(i->users.findUserById(i).orElseThrow().view()).toList(); }
    Map<String,List<RoleRef>> sources(String id) { requireUser(id); return roles.sources(id); }
    void reason(String reason) { if(reason==null || reason.trim().length()<2 || reason.length()>200) throw new IamException(400,"REASON_REQUIRED","请填写2至200字变更原因"); }
    void name(String name, String description, String except) {
        if(name==null || name.trim().length()<2 || name.trim().length()>50 || description==null || description.length()>200) throw new IamException(400,"ROLE_NAME_INVALID","角色名称需2至50字，说明最多200字");
        if(roles.list().stream().anyMatch(r->!r.id().equals(except) && r.name().equalsIgnoreCase(name.trim()))) throw new IamException(409,"ROLE_NAME_EXISTS","角色名称已存在");
    }
    RoleView create(String name, String description, String copyFrom, SessionPrincipal actor) {
        roles.lock(); roles.requireAdministrator(actor.userId()); name(name,description,"");
        var permissions=List.<String>of();
        if(copyFrom!=null && !copyFrom.isBlank()) {
            if(copyFrom.equals("ADMIN")) throw new IamException(400,"ADMIN_COPY_FORBIDDEN","系统管理员角色不可复制");
            permissions=roles.require(copyFrom).permissions();
        }
        String id=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO iam_role(id,name,description,kind,status) VALUES (:id,:name,:description,'CUSTOM','DRAFT')")
            .param("id",id).param("name",name.trim()).param("description",description.trim()).update();
        replacePermissions(id,permissions);
        var result=roles.require(id); log(actor,copyFrom==null?"ROLE_CREATE":"ROLE_COPY",id,null,result,"创建角色草稿"); return result;
    }
    RoleView editable(String id,long version) {
        var r=roles.require(id);
        if(r.id().equals("ADMIN")) throw new IamException(400,"ADMIN_ROLE_PROTECTED","系统管理员角色受保护，不可修改、停用或删除");
        if(r.version()!=version) throw new IamException(409,"ROLE_VERSION_CONFLICT","角色已更新，请重新加载后确认");
        return r;
    }
    Set<String> validate(Edit edit) {
        if (edit.reason() != null && edit.reason().length() > 200)
            throw new IamException(400,"REASON_TOO_LONG","变更原因不能超过200字");
        if(edit.permissions()==null || edit.permissions().size()>200 || edit.permissions().stream().anyMatch(Objects::isNull)) throw new IamException(400,"PERMISSION_INVALID","权限集合无效");
        var codes=PermissionCatalog.normalize(edit.permissions());
        if(!List.of("DRAFT","ENABLED","DISABLED").contains(edit.status())) throw new IamException(400,"ROLE_STATUS_INVALID","角色状态无效");
        if(edit.status().equals("ENABLED") && (codes.isEmpty() || codes.equals(Set.of("audit.read.self")))) throw new IamException(400,"ROLE_PERMISSION_REQUIRED","启用角色至少需要一项业务权限");
        return codes;
    }
    Preview preview(String id, Edit edit) {
        var before=editable(id,edit.version()); name(edit.name(),edit.description(),id); var next=validate(edit);
        return difference(before.permissions(),next,members(id));
    }
    RoleView save(String id, Edit edit, SessionPrincipal actor) {
        roles.lock(); roles.requireAdministrator(actor.userId()); var before=editable(id,edit.version()); name(edit.name(),edit.description(),id); var next=validate(edit);
        if(!before.status().equals("DRAFT") && edit.status().equals("DRAFT")) throw new IamException(400,"ROLE_STATE_INVALID","已发布角色不能退回草稿，请停用");
        jdbc.sql("UPDATE iam_role SET name=:name,description=:description,status=:status,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id")
            .param("name",edit.name().trim()).param("description",edit.description().trim()).param("status",edit.status()).param("id",id).update();
        replacePermissions(id,next); roles.guardEnabledUsers(); roles.guardAvailableAdministrator();
        if(!new HashSet<>(before.permissions()).equals(next) || !before.status().equals(edit.status())) {
            for(String member:roles.memberIds(id)) {
                users.revokeUserSessions(member);
                jdbc.sql("UPDATE iam_user SET authorization_version=authorization_version+1 WHERE id=:id").param("id",member).update();
            }
        }
        var after=roles.require(id); log(actor,"ROLE_UPDATE",id,before,after,edit.reason()); return after;
    }
    void delete(String id, long version, String reason, SessionPrincipal actor) {
        roles.lock(); roles.requireAdministrator(actor.userId()); reason(reason); var before=editable(id,version);
        if(!roles.memberIds(id).isEmpty()) throw new IamException(409,"ROLE_HAS_MEMBERS","该角色仍有成员，请先解除绑定");
        jdbc.sql("UPDATE iam_role SET deleted_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:id").param("id",id).update();
        log(actor,"ROLE_DELETE",id,before,null,reason);
    }
    private User requireUser(String id) { return users.findUserById(id).orElseThrow(()->new IamException(404,"USER_NOT_FOUND","用户不存在")); }
    private Set<String> assignment(Assignment a) {
        if(a.reason()!=null && a.reason().length()>200) throw new IamException(400,"REASON_INVALID","变更原因最多200字");
        if(a.roleIds()==null || a.roleIds().size()>20 || a.roleIds().stream().anyMatch(Objects::isNull)) throw new IamException(400,"USER_ROLES_INVALID","最多选择20个启用角色");
        var ids=new TreeSet<>(a.roleIds());
        if(ids.contains("ADMIN") && ids.size()>1) throw new IamException(400,"ADMIN_ROLE_EXCLUSIVE","系统管理员不能混绑其他角色");
        for(var id:ids) if(!roles.require(id).status().equals("ENABLED")) throw new IamException(400,"ROLE_NOT_ENABLED","不能分配草稿或停用角色");
        return ids;
    }
    Preview previewAssignment(String id, Assignment a) {
        var user=requireUser(id); var ids=assignment(a); var codes=new TreeSet<String>();
        for(var rid:ids) codes.addAll(roles.permissions(rid)); if(!codes.isEmpty()) codes.add("audit.read.self");
        return difference(user.effectivePermissions(),codes,List.of(user.view()));
    }
    UserView assign(String id, Assignment a, SessionPrincipal actor) {
        roles.lock(); roles.requireAdministrator(actor.userId()); var before=requireUser(id);
        if(id.equals(actor.userId())) throw new IamException(400,"SELF_ROLE_CHANGE_FORBIDDEN","不能修改自己的角色");
        if(before.authorizationVersion()!=a.version()) throw new IamException(409,"USER_VERSION_CONFLICT","账号授权已变化，请重新加载后确认");
        var ids=assignment(a); roles.bind(id,ids);
        if(ids.isEmpty()) users.setStatus(id,Status.DISABLED);
        roles.guardAvailableAdministrator(); users.revokeUserSessions(id);
        var after=requireUser(id).view();
        if(ids.isEmpty() && before.status()!=Status.DISABLED)
            audit.account(AuditTypes.Actor.authenticated(actor),"ACCOUNT_DISABLE",before.view(),after,"移除全部角色后停用账号"+(a.reason()==null || a.reason().isBlank() ? "" : "："+a.reason()));
        audit.append(AuditTypes.Actor.authenticated(actor),"ACCOUNT","USER_ROLES_CHANGE","ACCOUNT",id,AuditTypes.Outcome.SUCCESS,before.view().roles(),after.roles(),a.reason());
        return after;
    }
    UserView createUser(String login, String display, String email, String phone, String password, List<String> ids, SessionPrincipal actor, IamService service) {
        roles.lock(); roles.requireAdministrator(actor.userId());
        if(ids!=null && ids.isEmpty()) throw new IamException(400,"USER_ROLES_INVALID","创建用户时请选择至少一个启用角色");
        assignment(new Assignment(ids,0,"创建用户"));
        // Create and bind atomically without depending on a deletable preset role.
        var user=service.createUserForRoleAssignment(login,display,email,phone,password,actor);
        roles.bind(user.id(),ids);
        var after=requireUser(user.id()).view();
        audit.append(AuditTypes.Actor.authenticated(actor),"ACCOUNT","USER_ROLES_CHANGE","ACCOUNT",user.id(),AuditTypes.Outcome.SUCCESS,null,after.roles(),"创建用户时分配角色");
        return after;
    }
    UserView profile(String id,String display,String email,String phone,SessionPrincipal actor,IamService service) {
        if(display==null || display.isBlank() || display.length()>128) throw new IamException(400,"DISPLAY_NAME_INVALID","显示名称不能为空且最多128字");
        var before=requireUser(id).view();
        if (!Objects.equals(before.email(),email == null || email.isBlank() ? null : email) || !Objects.equals(before.phone(),phone == null || phone.isBlank() ? null : phone)) service.updateRecoveryContacts(id,email,phone,actor);
        jdbc.sql("UPDATE iam_user SET display_name=:name,updated_at=CURRENT_TIMESTAMP WHERE id=:id").param("name",display.trim()).param("id",id).update();
        var after=requireUser(id).view(); audit.account(AuditTypes.Actor.authenticated(actor),"ACCOUNT_PROFILE_UPDATE",before,after,null); return after;
    }
    private Preview difference(Collection<String> old,Collection<String> next,List<UserView> members) {
        return new Preview(next.stream().filter(p->!old.contains(p)).sorted().toList(),old.stream().filter(p->!next.contains(p)).sorted().toList(),members);
    }
    private void replacePermissions(String id, Collection<String> codes) {
        jdbc.sql("DELETE FROM iam_role_permission WHERE role_id=:id").param("id",id).update();
        for(String code:codes) jdbc.sql("INSERT INTO iam_role_permission(role_id,permission_code) VALUES (:id,:code)").param("id",id).param("code",code).update();
    }
    private void log(SessionPrincipal actor,String action,String id,Object before,Object after,String reason) {
        audit.append(AuditTypes.Actor.authenticated(actor),"ACCOUNT",action,"ROLE",id,AuditTypes.Outcome.SUCCESS,before,after,reason);
    }
}
