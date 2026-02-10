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
        SpringApplication.run(AgentXApplication.class, args);
    }
}
