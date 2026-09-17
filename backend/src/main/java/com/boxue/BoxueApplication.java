package com.boxue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.boxue.module.**.mapper")
public class BoxueApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoxueApplication.class, args);
    }
}
