package com.helianhealth.iflow.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class},
        scanBasePackages = {
            "com.helianhealth.iflow.web",
            "com.helianhealth.iflow.tasks",
            "com.helianhealth.iflow.gateway"
        })
public class IflowApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(IflowApplication.class, args);
    }
}
