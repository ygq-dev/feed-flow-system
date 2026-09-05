package com.ygq.feedly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FeedlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeedlyApplication.class, args);
    }

}
