package com.jiugeng.erp.iam;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class SamplePermissionDependencyTest {
 @Test void sampleReadIncludesProductImagesButNoWrites(){assertThat(PermissionCatalog.normalize(List.of("samples.read"))).contains("products.read","materials.read","suppliers.read","inventory.read").doesNotContain("products.write","samples.write","samples.remove");}
 @Test void sampleWriteDoesNotGrantRemoval(){assertThat(PermissionCatalog.normalize(List.of("samples.write"))).contains("samples.read","products.read").doesNotContain("samples.remove","products.write");}
}
