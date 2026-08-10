package com.nanacocoa.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NanacocoaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NanacocoaServerApplication.class, args);
    }
}
