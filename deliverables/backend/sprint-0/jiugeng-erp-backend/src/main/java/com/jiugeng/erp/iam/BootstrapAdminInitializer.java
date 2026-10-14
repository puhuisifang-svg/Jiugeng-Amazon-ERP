package com.jiugeng.erp.iam;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!mock")
class BootstrapAdminInitializer implements ApplicationRunner {
    private final IamService service;
    private final String loginName;
    private final String displayName;
    private final String password;

    BootstrapAdminInitializer(IamService service,
            @Value("${app.iam.bootstrap.login-name:}") String loginName,
            @Value("${app.iam.bootstrap.display-name:系统管理员}") String displayName,
            @Value("${app.iam.bootstrap.password:}") String password) {
        this.service = service;
        this.loginName = loginName;
        this.displayName = displayName;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!loginName.isBlank() && !password.isBlank()) {
            service.bootstrapAdmin(loginName, displayName, password);
        }
    }
}
