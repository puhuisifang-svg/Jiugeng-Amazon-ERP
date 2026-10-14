package com.jiugeng.erp.iam;

import static com.jiugeng.erp.iam.IamTypes.Role;
import static com.jiugeng.erp.iam.IamTypes.SessionPrincipal;
import static com.jiugeng.erp.iam.IamTypes.Status;
import static com.jiugeng.erp.iam.IamTypes.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class IamRepository {
    private final JdbcClient jdbc;

    IamRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<User> findUserByLoginName(String loginName) {
        return jdbc.sql("""
                SELECT id, login_name, display_name, password_hash, role, status, must_change_password,
                       failed_login_count, locked_at
                FROM iam_user WHERE login_name = :loginName
                """).param("loginName", loginName).query(this::mapUser).optional();
    }

    Optional<User> findUserById(String id) {
        return jdbc.sql("""
                SELECT id, login_name, display_name, password_hash, role, status, must_change_password,
                       failed_login_count, locked_at
                FROM iam_user WHERE id = :id
                """).param("id", id).query(this::mapUser).optional();
    }

    List<User> findUsers() {
        return jdbc.sql("""
                SELECT id, login_name, display_name, password_hash, role, status, must_change_password,
                       failed_login_count, locked_at
                FROM iam_user ORDER BY created_at, login_name
                """).query(this::mapUser).list();
    }

    boolean userExists() {
        return jdbc.sql("SELECT COUNT(*) FROM iam_user").query(Integer.class).single() > 0;
    }

    void insertUser(String id, String loginName, String displayName, String passwordHash, Role role) {
        jdbc.sql("""
                INSERT INTO iam_user
                    (id, login_name, display_name, password_hash, role, status, must_change_password)
                VALUES (:id, :loginName, :displayName, :passwordHash, :role, 'ENABLED', TRUE)
                """).param("id", id).param("loginName", loginName).param("displayName", displayName)
                .param("passwordHash", passwordHash).param("role", role.name()).update();
    }

    int recordFailedLogin(User user, int maxFailures) {
        int failures = user.failedLoginCount() + 1;
        jdbc.sql("""
                UPDATE iam_user SET failed_login_count = :failures,
                    locked_at = CASE WHEN :failures >= :maxFailures THEN CURRENT_TIMESTAMP ELSE locked_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """).param("failures", failures).param("maxFailures", maxFailures).param("id", user.id()).update();
        return failures;
    }

    void clearFailedLogins(String userId) {
        jdbc.sql("""
                UPDATE iam_user SET failed_login_count = 0, locked_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """).param("id", userId).update();
    }

    void insertSession(String id, String userId, String tokenHash, Instant expiresAt) {
        jdbc.sql("""
                INSERT INTO iam_session (id, user_id, token_hash, expires_at)
                VALUES (:id, :userId, :tokenHash, :expiresAt)
                """).param("id", id).param("userId", userId).param("tokenHash", tokenHash)
                .param("expiresAt", expiresAt).update();
    }

    Optional<SessionPrincipal> findActiveSession(String tokenHash, Instant now) {
        return jdbc.sql("""
                SELECT u.id, u.login_name, u.display_name, u.role, u.must_change_password
                FROM iam_session s JOIN iam_user u ON u.id = s.user_id
                WHERE s.token_hash = :tokenHash AND s.revoked_at IS NULL AND s.expires_at > :now
                  AND u.status = 'ENABLED' AND u.locked_at IS NULL
                """).param("tokenHash", tokenHash).param("now", now).query((rs, rowNum) -> {
                    Role role = Role.valueOf(rs.getString("role"));
                    return new SessionPrincipal(rs.getString("id"), rs.getString("login_name"),
                            rs.getString("display_name"), role, rs.getBoolean("must_change_password"),
                            IamTypes.capabilities(role), tokenHash);
                }).optional();
    }

    void revokeSession(String tokenHash) {
        jdbc.sql("""
                UPDATE iam_session SET revoked_at = CURRENT_TIMESTAMP
                WHERE token_hash = :tokenHash AND revoked_at IS NULL
                """).param("tokenHash", tokenHash).update();
    }

    void revokeUserSessions(String userId) {
        jdbc.sql("""
                UPDATE iam_session SET revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = :userId AND revoked_at IS NULL
                """).param("userId", userId).update();
    }

    void changePassword(String userId, String passwordHash) {
        jdbc.sql("""
                UPDATE iam_user SET password_hash = :passwordHash, must_change_password = FALSE,
                    failed_login_count = 0, locked_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """).param("passwordHash", passwordHash).param("id", userId).update();
    }

    void resetPassword(String userId, String passwordHash) {
        jdbc.sql("""
                UPDATE iam_user SET password_hash = :passwordHash, must_change_password = TRUE,
                    failed_login_count = 0, locked_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """).param("passwordHash", passwordHash).param("id", userId).update();
    }

    void setStatus(String userId, Status status) {
        jdbc.sql("UPDATE iam_user SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("status", status.name()).param("id", userId).update();
    }

    void setRole(String userId, Role role) {
        jdbc.sql("UPDATE iam_user SET role = :role, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("role", role.name()).param("id", userId).update();
    }

    void unlock(String userId) {
        clearFailedLogins(userId);
    }

    void event(String userId, String loginName, String type, boolean success, String detail) {
        jdbc.sql("""
                INSERT INTO iam_security_event (id, user_id, login_name, event_type, success, detail)
                VALUES (:id, :userId, :loginName, :type, :success, :detail)
                """).param("id", UUID.randomUUID().toString()).param("userId", userId)
                .param("loginName", loginName).param("type", type).param("success", success)
                .param("detail", detail).update();
    }

    private User mapUser(ResultSet rs, int rowNum) throws SQLException {
        var lockedAt = rs.getTimestamp("locked_at");
        return new User(rs.getString("id"), rs.getString("login_name"), rs.getString("display_name"),
                Role.valueOf(rs.getString("role")), Status.valueOf(rs.getString("status")),
                rs.getBoolean("must_change_password"), rs.getInt("failed_login_count"),
                lockedAt == null ? null : lockedAt.toInstant(), rs.getString("password_hash"));
    }
}
