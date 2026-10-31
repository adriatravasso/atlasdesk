package com.adria.atlasdesk;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AtlasDeskApplication {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        SpringApplication.run(AtlasDeskApplication.class, args);
    }
}
