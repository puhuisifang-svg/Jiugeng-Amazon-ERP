package com.jiugeng.erp.iam;

import static com.jiugeng.erp.iam.IamTypes.Role;
import static com.jiugeng.erp.iam.IamTypes.RecoveryChannel;
import static com.jiugeng.erp.iam.IamTypes.PasswordResetChallenge;
import static com.jiugeng.erp.iam.IamTypes.SessionPrincipal;
import static com.jiugeng.erp.iam.IamTypes.Status;
import static com.jiugeng.erp.iam.IamTypes.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
                SELECT id, login_name, display_name, email, phone, password_hash, role, status, must_change_password,
                       failed_login_count, locked_at
                FROM iam_user WHERE login_name = :loginName
                """).param("loginName", loginName).query(this::mapUser).optional();
    }

    Optional<User> findUserById(String id) {
        return jdbc.sql("""
                SELECT id, login_name, display_name, email, phone, password_hash, role, status, must_change_password,
                       failed_login_count, locked_at
                FROM iam_user WHERE id = :id
                """).param("id", id).query(this::mapUser).optional();
    }

    List<User> findUsers() {
        return jdbc.sql("""
                SELECT id, login_name, display_name, email, phone, password_hash, role, status, must_change_password,
                       failed_login_count, locked_at
                FROM iam_user ORDER BY created_at, login_name
                """).query(this::mapUser).list();
    }

    boolean userExists() {
        return jdbc.sql("SELECT COUNT(*) FROM iam_user").query(Integer.class).single() > 0;
    }

    Optional<User> findUserByRecoveryDestination(RecoveryChannel channel, String destination) {
        String column = channel == RecoveryChannel.EMAIL ? "email" : "phone";
        String sql = """
                SELECT id, login_name, display_name, email, phone, password_hash, role, status, must_change_password,
                       failed_login_count, locked_at
                FROM iam_user WHERE %s = :destination
                """.formatted(column);
        return jdbc.sql(sql).param("destination", destination).query(this::mapUser).optional();
    }

    void insertUser(String id, String loginName, String displayName, String email, String phone,
            String passwordHash, Role role) {
        jdbc.sql("""
                INSERT INTO iam_user
                    (id, login_name, display_name, email, phone, password_hash, role, status, must_change_password)
                VALUES (:id, :loginName, :displayName, :email, :phone, :passwordHash, :role, 'ENABLED', TRUE)
                """).param("id", id).param("loginName", loginName).param("displayName", displayName)
                .param("email", email).param("phone", phone).param("passwordHash", passwordHash)
                .param("role", role.name()).update();
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

    void updateRecoveryContacts(String userId, String email, String phone) {
        jdbc.sql("""
                UPDATE iam_user SET email = :email, phone = :phone, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """).param("email", email).param("phone", phone).param("id", userId).update();
    }

    int countPasswordResetRequests(String userId, RecoveryChannel channel, Instant since) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM iam_password_reset_challenge
                WHERE user_id = :userId AND channel = :channel AND requested_at >= :since
                """).param("userId", userId).param("channel", channel.name()).param("since", since)
                .query(Integer.class).single();
    }

    void invalidatePasswordResetChallenges(String userId, RecoveryChannel channel) {
        jdbc.sql("""
                UPDATE iam_password_reset_challenge SET consumed_at = CURRENT_TIMESTAMP
                WHERE user_id = :userId AND channel = :channel AND consumed_at IS NULL
                """).param("userId", userId).param("channel", channel.name()).update();
    }

    void insertPasswordResetChallenge(PasswordResetChallenge challenge) {
        jdbc.sql("""
                INSERT INTO iam_password_reset_challenge
                    (id, user_id, channel, destination, code_hash, expires_at, remaining_attempts)
                VALUES (:id, :userId, :channel, :destination, :codeHash, :expiresAt, :remainingAttempts)
                """).param("id", challenge.id()).param("userId", challenge.userId())
                .param("channel", challenge.channel().name()).param("destination", challenge.destination())
                .param("codeHash", challenge.codeHash()).param("expiresAt", challenge.expiresAt())
                .param("remainingAttempts", challenge.remainingAttempts()).update();
    }

    Optional<PasswordResetChallenge> findActivePasswordResetChallenge(
            RecoveryChannel channel, String destination, Instant now) {
        return jdbc.sql("""
                SELECT id, user_id, channel, destination, code_hash, expires_at, remaining_attempts
                FROM iam_password_reset_challenge
                WHERE channel = :channel AND destination = :destination AND consumed_at IS NULL
                  AND expires_at > :now AND remaining_attempts > 0
                ORDER BY requested_at DESC LIMIT 1
                """).param("channel", channel.name()).param("destination", destination).param("now", now)
                .query((rs, rowNum) -> new PasswordResetChallenge(rs.getString("id"), rs.getString("user_id"),
                        RecoveryChannel.valueOf(rs.getString("channel")), rs.getString("destination"),
                        rs.getString("code_hash"), rs.getTimestamp("expires_at").toInstant(),
                        rs.getInt("remaining_attempts"))).optional();
    }

    void recordPasswordResetFailure(String challengeId) {
        jdbc.sql("""
                UPDATE iam_password_reset_challenge
                SET remaining_attempts = remaining_attempts - 1,
                    consumed_at = CASE WHEN remaining_attempts <= 1 THEN CURRENT_TIMESTAMP ELSE consumed_at END
                WHERE id = :id AND consumed_at IS NULL
                """).param("id", challengeId).update();
    }

    void consumePasswordResetChallenge(String challengeId) {
        jdbc.sql("""
                UPDATE iam_password_reset_challenge SET consumed_at = CURRENT_TIMESTAMP
                WHERE id = :id AND consumed_at IS NULL
                """).param("id", challengeId).update();
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

    void lock(String userId) {
        jdbc.sql("""
                UPDATE iam_user SET locked_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """).param("id", userId).update();
    }

    int staySignedInDays() {
        return jdbc.sql("""
                SELECT setting_value FROM iam_security_setting WHERE setting_key = 'stay_signed_in_days'
                """).query(String.class).optional().map(Integer::parseInt).orElse(7);
    }

    void updateStaySignedInDays(int days, String actorUserId) {
        jdbc.sql("""
                UPDATE iam_security_setting
                SET setting_value = :days, updated_by = :actorUserId, updated_at = CURRENT_TIMESTAMP
                WHERE setting_key = 'stay_signed_in_days'
                """).param("days", Integer.toString(days)).param("actorUserId", actorUserId).update();
    }

    private User mapUser(ResultSet rs, int rowNum) throws SQLException {
        var lockedAt = rs.getTimestamp("locked_at");
        return new User(rs.getString("id"), rs.getString("login_name"), rs.getString("display_name"),
                rs.getString("email"), rs.getString("phone"),
                Role.valueOf(rs.getString("role")), Status.valueOf(rs.getString("status")),
                rs.getBoolean("must_change_password"), rs.getInt("failed_login_count"),
                lockedAt == null ? null : lockedAt.toInstant(), rs.getString("password_hash"));
    }
}
