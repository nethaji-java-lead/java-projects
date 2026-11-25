package com.example.fileflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableScheduling
public class FileflowApplication {
    private static final Logger log = LoggerFactory.getLogger(FileflowApplication.class);

    public static void main(String[] args) {
        log.info("Starting Fileflow application");
        SpringApplication.run(FileflowApplication.class, args);
        log.info("Fileflow application stopped");
    }
}
