package com.example.seoulapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling   // 스케줄링
public class SeoulApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeoulApiApplication.class, args);
    }
}