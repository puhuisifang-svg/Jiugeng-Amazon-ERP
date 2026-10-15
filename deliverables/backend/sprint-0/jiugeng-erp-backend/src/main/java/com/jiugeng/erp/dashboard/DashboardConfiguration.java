package com.jiugeng.erp.dashboard;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DashboardConfiguration {
    @Bean Clock dashboardClock() { return Clock.systemUTC(); }
}
