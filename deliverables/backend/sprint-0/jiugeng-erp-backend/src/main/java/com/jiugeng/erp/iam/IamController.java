package com.jiugeng.erp.iam;

import static com.jiugeng.erp.iam.IamTypes.Role;
import static com.jiugeng.erp.iam.IamTypes.RecoveryChannel;
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

    @PostMapping("/auth/password-reset/request")
    PasswordResetRequestResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        service.requestPasswordReset(request.channel(), request.destination());
        return new PasswordResetRequestResponse("如果信息匹配，验证码将发送到对应联系方式");
    }

    @PostMapping("/auth/password-reset/confirm")
    void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        service.confirmPasswordReset(request.channel(), request.destination(), request.code(), request.newPassword());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('system.users.manage')")
    List<UserView> users() {
        return service.users();
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView createUser(@Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal SessionPrincipal principal) {
        return service.createUser(request.loginName(), request.displayName(), request.email(), request.phone(),
                request.initialPassword(), request.role(), principal);
    }

    @PostMapping("/users/{userId}/disable")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView disable(@PathVariable String userId, @AuthenticationPrincipal SessionPrincipal principal) {
        return service.setStatus(userId, Status.DISABLED, principal);
    }

    @PostMapping("/users/{userId}/enable")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView enable(@PathVariable String userId, @AuthenticationPrincipal SessionPrincipal principal) {
        return service.setStatus(userId, Status.ENABLED, principal);
    }

    @PostMapping("/users/{userId}/role")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView setRole(@PathVariable String userId, @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody ChangeRoleRequest request) {
        return service.setRole(userId, request.role(), principal);
    }

    @PostMapping("/users/{userId}/unlock")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView unlock(@PathVariable String userId, @AuthenticationPrincipal SessionPrincipal principal) {
        return service.unlock(userId, principal);
    }

    @PostMapping("/users/{userId}/reset-password")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView resetPassword(@PathVariable String userId, @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody ResetPasswordRequest request) {
        return service.resetPassword(userId, request.newPassword(), principal);
    }

    @PostMapping("/users/{userId}/recovery-contacts")
    @PreAuthorize("hasAuthority('system.users.manage')")
    UserView updateRecoveryContacts(@PathVariable String userId,
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody RecoveryContactsRequest request) {
        return service.updateRecoveryContacts(userId, request.email(), request.phone(), principal);
    }

    record LoginRequest(
            @NotBlank @Size(max = 128) String loginName,
            @NotBlank @Size(max = 128) String password) {
    }

    record LoginResponse(String token, Instant expiresAt, UserView user) {
    }

    record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 6, max = 128) String newPassword) {
    }

    record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,128}") String loginName,
            @NotBlank @Size(max = 128) String displayName,
            @Size(max = 254) String email,
            @Size(max = 32) String phone,
            @NotBlank @Size(min = 6, max = 128) String initialPassword,
            @NotNull Role role) {
    }

    record ResetPasswordRequest(@NotBlank @Size(min = 6, max = 128) String newPassword) {
    }

    record ChangeRoleRequest(@NotNull Role role) {
    }

    record RecoveryContactsRequest(@Size(max = 254) String email, @Size(max = 32) String phone) {
    }

    record PasswordResetRequest(@NotNull RecoveryChannel channel,
                                @NotBlank @Size(max = 254) String destination) {
    }

    record PasswordResetRequestResponse(String message) {
    }

    record PasswordResetConfirmRequest(@NotNull RecoveryChannel channel,
                                       @NotBlank @Size(max = 254) String destination,
                                       @NotBlank @Pattern(regexp = "\\d{6}") String code,
                                       @NotBlank @Size(min = 6, max = 128) String newPassword) {
    }
}
