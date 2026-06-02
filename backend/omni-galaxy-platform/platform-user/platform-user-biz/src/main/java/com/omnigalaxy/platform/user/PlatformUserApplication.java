package com.omnigalaxy.platform.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.omnigalaxy.platform.user.mapper")
@ComponentScan(basePackages = {
        "com.omnigalaxy.platform.user",
        "com.omnigalaxy.common.core.handler",
        "com.omnigalaxy.common.mybatis"
})
public class PlatformUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformUserApplication.class, args);
    }
}