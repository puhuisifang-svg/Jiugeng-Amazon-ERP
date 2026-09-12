package com.jiugeng.erp.architecture;

import com.jiugeng.erp.JiugengErpApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

    @Test
    void moduleBoundariesAreValid() {
        ApplicationModules.of(JiugengErpApplication.class).verify();
    }
}
