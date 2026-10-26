package com.jiugeng.erp.iam;

import java.security.Principal;
import java.util.List;
import java.util.Set;

final class IamTypes {
    private IamTypes() {
    }

    enum Role {
        ADMIN, PRODUCT_MATERIAL, SELECTION
    }

    enum Status {
        ENABLED, DISABLED
    }

    enum RecoveryChannel {
        EMAIL, SMS
    }

    static Set<String> capabilities(Role role) {
        return switch (role) {
            case ADMIN -> Set.of(
                    "costs.read", "costs.write", "costs.publish", "costs.export",
                    "system.users.manage", "products.read", "products.write", "materials.read", "materials.write",
                    "inventory.read", "inventory.write", "selection.read", "selection.write", "watch.read",
                    "watch.write", "imports.read", "imports.template.download", "imports.validate", "imports.commit",
                    "imports.cancel", "imports.result.download", "audit.read.all", "audit.read.self", "dashboard.read",
                    "dashboard.shipments.manage", "dashboard.pending-products.manage", "suppliers.read",
                    "suppliers.write", "purchases.read", "purchases.write", "purchases.receive", "customers.read", "customers.write");
            case PRODUCT_MATERIAL -> Set.of(
                    "costs.read", "costs.write", "costs.export",
                    "products.read", "products.write", "materials.read", "materials.write", "inventory.read",
                    "inventory.write", "selection.read", "watch.read", "imports.read", "imports.template.download",
                    "imports.validate", "imports.commit", "imports.cancel", "imports.result.download",
                    "audit.read.self", "dashboard.read", "dashboard.shipments.manage",
                    "dashboard.pending-products.manage", "suppliers.read", "suppliers.write",
                    "purchases.read", "purchases.write", "purchases.receive", "customers.read", "customers.write");
            case SELECTION -> Set.of(
                    "products.read", "materials.read", "selection.read", "selection.write", "watch.read",
                    "watch.write", "audit.read.self", "dashboard.read", "dashboard.pending-products.manage");
        };
    }

    record User(String id, String loginName, String displayName, String email, String phone, Role role, Status status,
                boolean mustChangePassword, int failedLoginCount, java.time.Instant lockedAt, String passwordHash) {
        UserView view() {
            return new UserView(id, loginName, displayName, email, phone, role, status, mustChangePassword, lockedAt,
                    capabilities(role).stream().sorted().toList());
        }
    }

    record UserView(String id, String loginName, String displayName, String email, String phone, Role role, Status status,
                    boolean mustChangePassword, java.time.Instant lockedAt, List<String> capabilities) {
    }

    record PasswordResetChallenge(String id, String userId, RecoveryChannel channel, String destination,
                                  String codeHash, java.time.Instant expiresAt, int remainingAttempts) {
    }

    record SessionPrincipal(String userId, String loginName, String displayName, Role role,
                            boolean mustChangePassword, Set<String> capabilities, String tokenHash)
            implements Principal, AuthenticatedActor {
        @Override
        public String getName() {
            return loginName;
        }
    }
}
