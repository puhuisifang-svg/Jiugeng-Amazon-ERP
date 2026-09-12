package com.jiugeng.erp.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.jiugeng.erp.JiugengErpApplication;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

class MysqlProfileSecretConfigurationTest {

    @Test
    void mysqlProfileReadsPasswordFromConfigTree() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/application-mysql.yml")) {
            assertThat(input).isNotNull();
            String configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(configuration)
                    .contains("configtree:${ERP_MYSQL_SECRET_DIR}")
                    .contains("password: ${mysql_app_password}")
                    .doesNotContain("DATABASE_" + "PASSWORD");
        }
    }

    @Test
    void mysqlProfileLoadsConfigTreePropertyWithoutPasswordEnvironmentVariable(@TempDir Path secretDirectory)
            throws IOException {
        String testOnlyPassword = "test-only-configtree-value";
        Files.writeString(secretDirectory.resolve("mysql_app_password"), testOnlyPassword, StandardCharsets.UTF_8);

        SpringApplication application = new SpringApplication(JiugengErpApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=mysql",
                "--ERP_MYSQL_SECRET_DIR=" + secretDirectory + "/",
                "--DATABASE_URL=jdbc:h2:mem:mysql-profile-configtree;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "--DATABASE_USERNAME=sa",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.flyway.enabled=false")) {
            DataSource dataSource = context.getBean(DataSource.class);
            assertThat(dataSource).isInstanceOf(HikariDataSource.class);
            assertThat(((HikariDataSource) dataSource).getPassword()).isEqualTo(testOnlyPassword);
        }
    }
}
