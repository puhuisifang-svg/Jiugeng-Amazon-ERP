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

    static Set<String> capabilities(Role role) {
        return switch (role) {
            case ADMIN -> Set.of(
                    "system.users.manage", "products.read", "products.write", "materials.read", "materials.write",
                    "inventory.read", "inventory.write", "selection.read", "selection.write", "watch.read",
                    "watch.write", "imports.read", "imports.template.download", "imports.validate", "imports.commit",
                    "imports.cancel", "imports.result.download", "audit.read.all", "audit.read.self");
            case PRODUCT_MATERIAL -> Set.of(
                    "products.read", "products.write", "materials.read", "materials.write", "inventory.read",
                    "inventory.write", "selection.read", "watch.read", "imports.read", "imports.template.download",
                    "imports.validate", "imports.commit", "imports.cancel", "imports.result.download",
                    "audit.read.self");
            case SELECTION -> Set.of(
                    "products.read", "materials.read", "selection.read", "selection.write", "watch.read",
                    "watch.write", "audit.read.self");
        };
    }

    record User(String id, String loginName, String displayName, Role role, Status status,
                boolean mustChangePassword, int failedLoginCount, java.time.Instant lockedAt, String passwordHash) {
        UserView view() {
            return new UserView(id, loginName, displayName, role, status, mustChangePassword, lockedAt,
                    capabilities(role).stream().sorted().toList());
        }
    }

    record UserView(String id, String loginName, String displayName, Role role, Status status,
                    boolean mustChangePassword, java.time.Instant lockedAt, List<String> capabilities) {
    }

    record SessionPrincipal(String userId, String loginName, String displayName, Role role,
                            boolean mustChangePassword, Set<String> capabilities, String tokenHash) implements Principal {
        @Override
        public String getName() {
            return loginName;
        }
    }
}
