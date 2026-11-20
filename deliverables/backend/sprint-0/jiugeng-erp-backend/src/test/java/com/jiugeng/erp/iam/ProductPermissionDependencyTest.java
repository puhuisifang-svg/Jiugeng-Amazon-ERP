package com.jiugeng.erp.iam;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class ProductPermissionDependencyTest {
 @Test void readRoleIncludesRelatedReadPermissionsWithoutMutation(){assertThat(PermissionCatalog.normalize(List.of("products.read"))).contains("suppliers.read","materials.read","inventory.read").doesNotContain("suppliers.write","materials.write","inventory.inbound","products.delete");}
 @Test void managementRoleIncludesMaintenanceButNotDeletionOrStockMovements(){assertThat(PermissionCatalog.normalize(List.of("products.write"))).contains("products.read","suppliers.read","suppliers.write","materials.read","materials.write","inventory.read").doesNotContain("suppliers.delete","inventory.outbound");}
}
