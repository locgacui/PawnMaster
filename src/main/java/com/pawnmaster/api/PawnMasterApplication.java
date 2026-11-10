package com.pawnmaster.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class PawnMasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(PawnMasterApplication.class, args);
    }
}

