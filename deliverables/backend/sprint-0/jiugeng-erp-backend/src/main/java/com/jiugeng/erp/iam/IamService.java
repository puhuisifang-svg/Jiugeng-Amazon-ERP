package com.jiugeng.erp.iam;

import static com.jiugeng.erp.iam.IamTypes.Role;
import static com.jiugeng.erp.iam.IamTypes.SessionPrincipal;
import static com.jiugeng.erp.iam.IamTypes.Status;
import static com.jiugeng.erp.iam.IamTypes.User;
import static com.jiugeng.erp.iam.IamTypes.UserView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IamService {
    private static final java.util.regex.Pattern LOGIN_NAME_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9._-]{3,128}");
    private final IamRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration sessionDuration;
    private final int maxFailedLogins;

    IamService(IamRepository repository, PasswordEncoder passwordEncoder,
            @Value("${app.iam.session-hours:8}") long sessionHours,
            @Value("${app.iam.max-failed-logins:5}") int maxFailedLogins) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.sessionDuration = Duration.ofHours(sessionHours);
        this.maxFailedLogins = maxFailedLogins;
    }

    @Transactional(noRollbackFor = IamException.class)
    LoginResult login(String loginName, String password) {
        String normalized = normalize(loginName);
        Optional<User> found = repository.findUserByLoginName(normalized);
        if (found.isEmpty()) {
            repository.event(null, normalized, "LOGIN_FAILED", false, "INVALID_CREDENTIALS");
            throw new IamException(401, "INVALID_CREDENTIALS", "账号或密码错误");
        }
        User user = found.get();
        if (user.status() == Status.DISABLED) {
            repository.event(user.id(), normalized, "LOGIN_FAILED", false, "ACCOUNT_DISABLED");
            throw new IamException(403, "ACCOUNT_DISABLED", "账号已停用");
        }
        if (user.lockedAt() != null) {
            repository.event(user.id(), normalized, "LOGIN_FAILED", false, "ACCOUNT_LOCKED");
            throw new IamException(423, "ACCOUNT_LOCKED", "账号已锁定，请联系管理员解锁");
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            int failures = repository.recordFailedLogin(user, maxFailedLogins);
            boolean locked = failures >= maxFailedLogins;
            repository.event(user.id(), normalized, "LOGIN_FAILED", false,
                    locked ? "ACCOUNT_LOCKED" : "INVALID_CREDENTIALS");
            if (locked) repository.event(user.id(), normalized, "ACCOUNT_LOCK", true, "FAILURE_THRESHOLD_REACHED");
            if (locked) throw new IamException(423, "ACCOUNT_LOCKED", "登录失败次数过多，账号已锁定");
            throw new IamException(401, "INVALID_CREDENTIALS", "账号或密码错误");
        }
        repository.clearFailedLogins(user.id());
        String token = newToken();
        Instant expiresAt = Instant.now().plus(sessionDuration);
        repository.insertSession(UUID.randomUUID().toString(), user.id(), hashToken(token), expiresAt);
        repository.event(user.id(), normalized, "LOGIN", true, null);
        User current = repository.findUserById(user.id()).orElseThrow();
        return new LoginResult(token, expiresAt, current.view());
    }

    Optional<SessionPrincipal> authenticate(String token) {
        return repository.findActiveSession(hashToken(token), Instant.now());
    }

    @Transactional
    void logout(SessionPrincipal principal) {
        repository.revokeSession(principal.tokenHash());
        repository.event(principal.userId(), principal.loginName(), "LOGOUT", true, null);
    }

    UserView me(SessionPrincipal principal) {
        return repository.findUserById(principal.userId()).orElseThrow(() -> IamException.unauthorized()).view();
    }

    @Transactional(noRollbackFor = IamException.class)
    void changePassword(SessionPrincipal principal, String currentPassword, String newPassword) {
        requireValidPassword(newPassword);
        User user = repository.findUserById(principal.userId()).orElseThrow(() -> IamException.unauthorized());
        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            repository.event(user.id(), user.loginName(), "PASSWORD_CHANGE", false, "CURRENT_PASSWORD_INVALID");
            throw new IamException(400, "CURRENT_PASSWORD_INVALID", "当前密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new IamException(400, "PASSWORD_REUSED", "新密码不能与当前密码相同");
        }
        repository.changePassword(user.id(), passwordEncoder.encode(newPassword));
        repository.event(user.id(), user.loginName(), "PASSWORD_CHANGE", true, null);
    }

    List<UserView> users() {
        return repository.findUsers().stream().map(User::view).toList();
    }

    @Transactional
    UserView createUser(String loginName, String displayName, String initialPassword, Role role) {
        String normalized = normalize(loginName);
        if (!LOGIN_NAME_PATTERN.matcher(normalized).matches()) {
            throw new IamException(400, "LOGIN_NAME_INVALID", "登录名格式不正确");
        }
        if (displayName == null || displayName.isBlank() || displayName.length() > 128) {
            throw new IamException(400, "DISPLAY_NAME_INVALID", "显示名称格式不正确");
        }
        requireValidPassword(initialPassword);
        if (repository.findUserByLoginName(normalized).isPresent()) {
            throw new IamException(409, "LOGIN_NAME_EXISTS", "登录名已存在");
        }
        String id = UUID.randomUUID().toString();
        repository.insertUser(id, normalized, displayName.trim(), passwordEncoder.encode(initialPassword), role);
        repository.event(id, normalized, "ACCOUNT_CREATE", true, role.name());
        return repository.findUserById(id).orElseThrow().view();
    }

    @Transactional
    UserView setStatus(String userId, Status status, String actorUserId) {
        if (userId.equals(actorUserId) && status == Status.DISABLED) {
            throw new IamException(400, "SELF_DISABLE_FORBIDDEN", "不能停用当前登录账号");
        }
        User user = requireUser(userId);
        repository.setStatus(userId, status);
        if (status == Status.DISABLED) repository.revokeUserSessions(userId);
        repository.event(userId, user.loginName(), status == Status.DISABLED ? "ACCOUNT_DISABLE" : "ACCOUNT_ENABLE", true, null);
        return repository.findUserById(userId).orElseThrow().view();
    }

    @Transactional
    UserView setRole(String userId, Role role, String actorUserId) {
        if (userId.equals(actorUserId)) {
            throw new IamException(400, "SELF_ROLE_CHANGE_FORBIDDEN", "不能修改当前登录账号的角色");
        }
        User user = requireUser(userId);
        repository.setRole(userId, role);
        repository.revokeUserSessions(userId);
        repository.event(userId, user.loginName(), "ROLE_CHANGE", true, role.name());
        return repository.findUserById(userId).orElseThrow().view();
    }

    @Transactional
    UserView unlock(String userId) {
        User user = requireUser(userId);
        repository.unlock(userId);
        repository.event(userId, user.loginName(), "ACCOUNT_UNLOCK", true, null);
        return repository.findUserById(userId).orElseThrow().view();
    }

    @Transactional
    UserView resetPassword(String userId, String newPassword) {
        requireValidPassword(newPassword);
        User user = requireUser(userId);
        repository.resetPassword(userId, passwordEncoder.encode(newPassword));
        repository.revokeUserSessions(userId);
        repository.event(userId, user.loginName(), "PASSWORD_RESET", true, null);
        return repository.findUserById(userId).orElseThrow().view();
    }

    void bootstrapAdmin(String loginName, String displayName, String password) {
        if (!repository.userExists()) createUser(loginName, displayName, password, Role.ADMIN);
    }

    private User requireUser(String userId) {
        return repository.findUserById(userId).orElseThrow(() -> new IamException(404, "USER_NOT_FOUND", "用户不存在"));
    }

    private String normalize(String loginName) {
        return loginName.trim().toLowerCase(Locale.ROOT);
    }

    private void requireValidPassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new IamException(400, "PASSWORD_POLICY_VIOLATION", "密码长度必须为 12 至 128 位");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashToken(String token) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record LoginResult(String token, Instant expiresAt, UserView user) {
    }
}
