package com.wrx.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiResourceBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiResourceBookingApplication.class, args);
    }

}
