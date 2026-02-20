package com.campus.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication
public class CampusRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusRagApplication.class, args);
    }
}
