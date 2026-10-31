package com.adria.atlasdesk.dev;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("e2e")
public class E2eTestDataConfiguration {

    @Bean
    CommandLineRunner seedE2eDatabase(E2eTestData data) {
        return args -> data.reset();
    }
}
