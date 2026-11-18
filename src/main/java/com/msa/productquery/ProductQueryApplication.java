package com.msa.productquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 별도 조회 모델과 Kafka 소비를 소유합니다. Command 원본 DB에는 접속하지 않습니다. */
@SpringBootApplication
public class    ProductQueryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductQueryApplication.class, args);
    }
}
