package com.jiugeng.erp.iam;

import static com.jiugeng.erp.iam.IamTypes.Role;
import static com.jiugeng.erp.iam.IamTypes.SessionPrincipal;
import static com.jiugeng.erp.iam.IamTypes.Status;
import static com.jiugeng.erp.iam.IamTypes.User;
import static com.jiugeng.erp.iam.IamTypes.UserView;
import static com.jiugeng.erp.iam.AuditTypes.Actor;
import static com.jiugeng.erp.iam.AuditTypes.Outcome;

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
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration sessionDuration;
    private final int maxFailedLogins;

    IamService(IamRepository repository, PasswordEncoder passwordEncoder, AuditService auditService,
            @Value("${app.iam.session-hours:8}") long sessionHours,
            @Value("${app.iam.max-failed-logins:5}") int maxFailedLogins) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.sessionDuration = Duration.ofHours(sessionHours);
        this.maxFailedLogins = maxFailedLogins;
    }

    @Transactional(noRollbackFor = IamException.class)
    LoginResult login(String loginName, String password) {
        String normalized = normalize(loginName);
        Optional<User> found = repository.findUserByLoginName(normalized);
        if (found.isEmpty()) {
            auditService.security(Actor.attemptedLogin(null, normalized, null), "LOGIN_FAILED", normalized,
                    Outcome.FAILURE, "INVALID_CREDENTIALS");
            throw new IamException(401, "INVALID_CREDENTIALS", "账号或密码错误");
        }
        User user = found.get();
        Actor actor = Actor.attemptedLogin(user.id(), user.loginName(), user.displayName());
        if (user.status() == Status.DISABLED) {
            auditService.security(actor, "LOGIN_FAILED", user.id(), Outcome.FAILURE, "ACCOUNT_DISABLED");
            throw new IamException(403, "ACCOUNT_DISABLED", "账号已停用");
        }
        if (user.lockedAt() != null) {
            auditService.security(actor, "LOGIN_FAILED", user.id(), Outcome.FAILURE, "ACCOUNT_LOCKED");
            throw new IamException(423, "ACCOUNT_LOCKED", "账号已锁定，请联系管理员解锁");
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            int failures = repository.recordFailedLogin(user, maxFailedLogins);
            boolean locked = failures >= maxFailedLogins;
            auditService.security(actor, "LOGIN_FAILED", user.id(), Outcome.FAILURE,
                    locked ? "ACCOUNT_LOCKED" : "INVALID_CREDENTIALS");
            if (locked) auditService.security(actor, "ACCOUNT_LOCK", user.id(), Outcome.SUCCESS,
                    "FAILURE_THRESHOLD_REACHED");
            if (locked) throw new IamException(423, "ACCOUNT_LOCKED", "登录失败次数过多，账号已锁定");
            throw new IamException(401, "INVALID_CREDENTIALS", "账号或密码错误");
        }
        repository.clearFailedLogins(user.id());
        String token = newToken();
        Instant expiresAt = Instant.now().plus(sessionDuration);
        repository.insertSession(UUID.randomUUID().toString(), user.id(), hashToken(token), expiresAt);
        auditService.security(actor, "LOGIN", user.id(), Outcome.SUCCESS, null);
        User current = repository.findUserById(user.id()).orElseThrow();
        return new LoginResult(token, expiresAt, current.view());
    }

    Optional<SessionPrincipal> authenticate(String token) {
        return repository.findActiveSession(hashToken(token), Instant.now());
    }

    @Transactional
    void logout(SessionPrincipal principal) {
        repository.revokeSession(principal.tokenHash());
        auditService.security(Actor.authenticated(principal), "LOGOUT", principal.userId(), Outcome.SUCCESS, null);
    }

    UserView me(SessionPrincipal principal) {
        return repository.findUserById(principal.userId()).orElseThrow(() -> IamException.unauthorized()).view();
    }

    @Transactional(noRollbackFor = IamException.class)
    void changePassword(SessionPrincipal principal, String currentPassword, String newPassword) {
        requireValidPassword(newPassword);
        User user = repository.findUserById(principal.userId()).orElseThrow(() -> IamException.unauthorized());
        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            auditService.security(Actor.authenticated(principal), "PASSWORD_CHANGE", user.id(), Outcome.FAILURE,
                    "CURRENT_PASSWORD_INVALID");
            throw new IamException(400, "CURRENT_PASSWORD_INVALID", "当前密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            auditService.security(Actor.authenticated(principal), "PASSWORD_CHANGE", user.id(), Outcome.FAILURE,
                    "PASSWORD_REUSED");
            throw new IamException(400, "PASSWORD_REUSED", "新密码不能与当前密码相同");
        }
        UserView before = user.view();
        repository.changePassword(user.id(), passwordEncoder.encode(newPassword));
        auditService.account(Actor.authenticated(principal), "PASSWORD_CHANGE", before,
                repository.findUserById(user.id()).orElseThrow().view(), null);
    }

    List<UserView> users() {
        return repository.findUsers().stream().map(User::view).toList();
    }

    @Transactional
    UserView createUser(String loginName, String displayName, String initialPassword, Role role) {
        return createUser(loginName, displayName, initialPassword, role, Actor.system());
    }

    @Transactional
    UserView createUser(String loginName, String displayName, String initialPassword, Role role,
            SessionPrincipal principal) {
        return createUser(loginName, displayName, initialPassword, role, Actor.authenticated(principal));
    }

    private UserView createUser(String loginName, String displayName, String initialPassword, Role role, Actor actor) {
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
        UserView created = repository.findUserById(id).orElseThrow().view();
        auditService.account(actor, "ACCOUNT_CREATE", null, created, null);
        return created;
    }

    @Transactional
    UserView setStatus(String userId, Status status, SessionPrincipal principal) {
        if (userId.equals(principal.userId()) && status == Status.DISABLED) {
            throw new IamException(400, "SELF_DISABLE_FORBIDDEN", "不能停用当前登录账号");
        }
        User user = requireUser(userId);
        UserView before = user.view();
        repository.setStatus(userId, status);
        if (status == Status.DISABLED) repository.revokeUserSessions(userId);
        UserView after = repository.findUserById(userId).orElseThrow().view();
        auditService.account(Actor.authenticated(principal),
                status == Status.DISABLED ? "ACCOUNT_DISABLE" : "ACCOUNT_ENABLE", before, after, null);
        return after;
    }

    @Transactional
    UserView setRole(String userId, Role role, SessionPrincipal principal) {
        if (userId.equals(principal.userId())) {
            throw new IamException(400, "SELF_ROLE_CHANGE_FORBIDDEN", "不能修改当前登录账号的角色");
        }
        User user = requireUser(userId);
        UserView before = user.view();
        repository.setRole(userId, role);
        repository.revokeUserSessions(userId);
        UserView after = repository.findUserById(userId).orElseThrow().view();
        auditService.account(Actor.authenticated(principal), "ROLE_CHANGE", before, after, null);
        return after;
    }

    @Transactional
    UserView unlock(String userId, SessionPrincipal principal) {
        User user = requireUser(userId);
        UserView before = user.view();
        repository.unlock(userId);
        UserView after = repository.findUserById(userId).orElseThrow().view();
        auditService.account(Actor.authenticated(principal), "ACCOUNT_UNLOCK", before, after, null);
        return after;
    }

    @Transactional
    UserView resetPassword(String userId, String newPassword, SessionPrincipal principal) {
        requireValidPassword(newPassword);
        User user = requireUser(userId);
        UserView before = user.view();
        repository.resetPassword(userId, passwordEncoder.encode(newPassword));
        repository.revokeUserSessions(userId);
        UserView after = repository.findUserById(userId).orElseThrow().view();
        auditService.account(Actor.authenticated(principal), "PASSWORD_RESET", before, after, null);
        return after;
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
