package com.jiugeng.erp;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RuntimeProfileGuardTest {

    @Test
    void acceptsExactlyOneDatasourceProfile() {
        for (String profile : new String[] {"mysql", "mock", "test"}) {
            RuntimeProfileGuard guard = new RuntimeProfileGuard(new MockEnvironment().withProperty(
                    "spring.profiles.active", profile));

            assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsStartupWithoutDatasourceProfile() {
        RuntimeProfileGuard guard = new RuntimeProfileGuard(new MockEnvironment());

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one datasource profile");
    }

    @Test
    void rejectsMixedPersistentAndSyntheticProfiles() {
        RuntimeProfileGuard guard = new RuntimeProfileGuard(
                new MockEnvironment().withProperty("spring.profiles.active", "mysql,mock"));

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one datasource profile");
    }
}
