package com.team.study;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RevMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(RevMateApplication.class, args);
    }
}
