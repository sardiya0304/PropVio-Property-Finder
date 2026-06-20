package com.propvio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PropvioApplication {
    public static void main(String[] args) {
        SpringApplication.run(PropvioApplication.class, args);
    }
}
