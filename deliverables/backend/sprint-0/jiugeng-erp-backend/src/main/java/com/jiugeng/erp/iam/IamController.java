package com.jiugeng.erp.iam;

import static com.jiugeng.erp.iam.IamTypes.Role;
import static com.jiugeng.erp.iam.IamTypes.SessionPrincipal;
import static com.jiugeng.erp.iam.IamTypes.Status;
import static com.jiugeng.erp.iam.IamTypes.UserView;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1")
class IamController {
    private final IamService service;

    IamController(IamService service) {
        this.service = service;
    }

    @PostMapping("/auth/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var result = service.login(request.loginName(), request.password());
        return new LoginResponse(result.token(), result.expiresAt(), result.user());
    }

    @GetMapping("/auth/me")
    UserView me(@AuthenticationPrincipal SessionPrincipal principal) {
        return service.me(principal);
    }

    @PostMapping("/auth/logout")
    void logout(@AuthenticationPrincipal SessionPrincipal principal) {
        service.logout(principal);
    }

    @PostMapping("/auth/change-password")
    void changePassword(@AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(principal, request.currentPassword(), request.newPassword());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('system.users.manage')")
    List<UserView> users() {
        return service.users();
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView createUser(@Valid @RequestBody CreateUserRequest request) {
        return service.createUser(request.loginName(), request.displayName(), request.initialPassword(), request.role());
    }

    @PostMapping("/users/{userId}/disable")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView disable(@PathVariable String userId, @AuthenticationPrincipal SessionPrincipal principal) {
        return service.setStatus(userId, Status.DISABLED, principal.userId());
    }

    @PostMapping("/users/{userId}/enable")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView enable(@PathVariable String userId, @AuthenticationPrincipal SessionPrincipal principal) {
        return service.setStatus(userId, Status.ENABLED, principal.userId());
    }

    @PostMapping("/users/{userId}/role")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView setRole(@PathVariable String userId, @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody ChangeRoleRequest request) {
        return service.setRole(userId, request.role(), principal.userId());
    }

    @PostMapping("/users/{userId}/unlock")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView unlock(@PathVariable String userId) {
        return service.unlock(userId);
    }

    @PostMapping("/users/{userId}/reset-password")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView resetPassword(@PathVariable String userId, @Valid @RequestBody ResetPasswordRequest request) {
        return service.resetPassword(userId, request.newPassword());
    }

    record LoginRequest(
            @NotBlank @Size(max = 128) String loginName,
            @NotBlank @Size(max = 128) String password) {
    }

    record LoginResponse(String token, Instant expiresAt, UserView user) {
    }

    record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,128}") String loginName,
            @NotBlank @Size(max = 128) String displayName,
            @NotBlank @Size(min = 12, max = 128) String initialPassword,
            @NotNull Role role) {
    }

    record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    record ChangeRoleRequest(@NotNull Role role) {
    }
}
