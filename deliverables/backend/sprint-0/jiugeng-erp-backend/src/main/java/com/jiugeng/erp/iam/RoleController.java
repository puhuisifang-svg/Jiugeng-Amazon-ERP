package com.jiugeng.erp.iam;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.jiugeng.erp.iam.IamTypes.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('system.users.manage')")
class RoleController {
    private final RoleService service;
    private final IamService users;
    private final AuditHistoryLookup history;
    RoleController(RoleService service,IamService users,AuditHistoryLookup history) { this.service=service;this.users=users;this.history=history; }
    @GetMapping("/roles") List<RoleRepository.RoleView> list() { return service.list(); }
    @GetMapping("/permissions") List<PermissionCatalog.Permission> permissions() { return PermissionCatalog.ALL; }
    @PostMapping("/roles") RoleRepository.RoleView create(@RequestBody Create r,@AuthenticationPrincipal SessionPrincipal a) { return service.create(r.name(),r.description(),r.copyFrom(),a); }
    @PostMapping("/roles/{id}/preview") RoleService.Preview preview(@PathVariable String id,@RequestBody RoleService.Edit r) { return service.preview(id,r); }
    @PutMapping("/roles/{id}") RoleRepository.RoleView save(@PathVariable String id,@RequestBody RoleService.Edit r,@AuthenticationPrincipal SessionPrincipal a) { return service.save(id,r,a); }
    @DeleteMapping("/roles/{id}") void delete(@PathVariable String id,@RequestBody Removal r,@AuthenticationPrincipal SessionPrincipal a) { service.delete(id,r.version(),r.reason(),a); }
    @GetMapping("/roles/{id}/members") List<UserView> members(@PathVariable String id) { return service.members(id); }
    @GetMapping("/roles/{id}/history") Object history(@PathVariable String id) { return history.find("ROLE",id); }
    @GetMapping("/users/{id}/permission-sources") Object sources(@PathVariable String id) { return service.sources(id); }
    @GetMapping("/users/{id}/history") Object userHistory(@PathVariable String id, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) { return history.page("ACCOUNT",id,page,size); }
    @PostMapping("/users/{id}/roles/preview") RoleService.Preview previewAssignment(@PathVariable String id,@RequestBody RoleService.Assignment r) { return service.previewAssignment(id,r); }
    @PutMapping("/users/{id}/roles") UserView assign(@PathVariable String id,@RequestBody RoleService.Assignment r,@AuthenticationPrincipal SessionPrincipal a) { return service.assign(id,r,a); }
    @PostMapping("/users/with-roles") UserView createUser(@RequestBody NewUser r,@AuthenticationPrincipal SessionPrincipal a) { return service.createUser(r.loginName(),r.displayName(),r.email(),r.phone(),r.initialPassword(),r.roleIds(),a,users); }
    @PutMapping("/users/{id}/profile") UserView profile(@PathVariable String id,@RequestBody ProfileEdit r,@AuthenticationPrincipal SessionPrincipal a) { return service.profile(id,r.displayName(),r.email(),r.phone(),a,users); }
    record Create(String name,String description,String copyFrom) {}
    record Removal(long version,String reason) {}
    record NewUser(String loginName,String displayName,String email,String phone,String initialPassword,List<String> roleIds) {}
    record ProfileEdit(String displayName,String email,String phone) {}
}
