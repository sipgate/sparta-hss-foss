package com.sipgate.sparta.hss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpartaHssApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SpartaHssApplication.class, args);
    }

}
