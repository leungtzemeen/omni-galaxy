package com.omnigalaxy.platform.auth;

import com.omnigalaxy.platform.user.api.client.UserProfileClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients(basePackageClasses = UserProfileClient.class)
@MapperScan("com.omnigalaxy.platform.auth.mapper")
@ComponentScan(basePackages = {
        "com.omnigalaxy.platform.auth",
        "com.omnigalaxy.common.core.handler",
        "com.omnigalaxy.common.mybatis"
})
public class PlatformAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformAuthApplication.class, args);
    }
}
