package com.jiugeng.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableScheduling;

@Modulith
@EnableScheduling
@SpringBootApplication
public class JiugengErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiugengErpApplication.class, args);
    }
}
