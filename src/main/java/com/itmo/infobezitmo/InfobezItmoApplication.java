package com.itmo.infobezitmo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InfobezItmoApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfobezItmoApplication.class, args);
    }
}
