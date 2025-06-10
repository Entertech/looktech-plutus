package com.looktech.plutus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PlutusApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlutusApplication.class, args);
    }
} 
