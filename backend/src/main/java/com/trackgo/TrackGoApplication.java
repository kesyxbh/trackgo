package com.trackgo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // registers the *Properties records in com.trackgo.config
public class TrackGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackGoApplication.class, args);
    }
}
