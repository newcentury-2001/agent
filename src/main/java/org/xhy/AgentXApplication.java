package org.xhy;

import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFileStorage
@EnableAsync
public class AgentXApplication {

    public static void main(String[] args) {
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretKey", "test");
        System.setProperty("aws.region", "us-east-1");
        System.setProperty("com.amazonaws.sdk.disableEc2Metadata", "true");
        SpringApplication.run(AgentXApplication.class, args);
    }
}
