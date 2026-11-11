package com.helianhealth.iflow.persistence;

import com.helianhealth.iflow.core.config.IflowCoreConfiguration;
import com.helianhealth.iflow.gateway.schedule.WorkflowScheduleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(
        scanBasePackages = {
                "com.helianhealth.iflow.persistence",
            "com.helianhealth.iflow.tasks",
            "com.helianhealth.iflow.gateway"
        })
@Import({IflowCoreConfiguration.class, WorkflowScheduleConfiguration.class})
public class IflowApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(IflowApplication.class, args);
    }
}
