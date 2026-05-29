package com.omnigalaxy.platform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(basePackages = {"com.omnigalaxy.platform.auth", "com.omnigalaxy.common.core.handler"})
public class PlatformAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformAuthApplication.class, args);
    }
}
