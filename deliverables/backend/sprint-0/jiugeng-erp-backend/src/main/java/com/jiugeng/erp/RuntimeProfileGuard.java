package com.jiugeng.erp;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Prevents accidental startup with an implicit or mixed datasource mode. */
@Component
final class RuntimeProfileGuard implements InitializingBean {

    private static final Set<String> DATASOURCE_PROFILES = Set.of("mysql", "mock", "test");

    private final Environment environment;

    RuntimeProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        Set<String> activeProfiles = new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
        activeProfiles.retainAll(DATASOURCE_PROFILES);

        if (activeProfiles.size() != 1) {
            throw new IllegalStateException(
                    "Activate exactly one datasource profile: mysql for persistent runtime, mock for synthetic demo, or test for automated tests");
        }
    }
}
