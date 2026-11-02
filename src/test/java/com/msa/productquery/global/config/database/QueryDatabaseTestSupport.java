package com.msa.productquery.global.config.database;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.*;

/** 기존 개발 DB와 분리된 실제 PostgreSQL 및 가변 UTC 시각을 제공합니다. */
public abstract class QueryDatabaseTestSupport {
    protected static final PostgreSQLContainer DB =
            new PostgreSQLContainer("postgres:17-alpine").withInitScript("db/query-schema.sql");

    static {
        DB.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @TestConfiguration
    public static class Time {
        @Bean
        @Primary
        MutableClock queryTestClock() {
            return new MutableClock();
        }
    }

    /** 날짜 경계·카운터를 실제 수면 없이 확인합니다. */
    public static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2030-01-01T00:00:00Z");

        public void set(Instant value) {
            now = value;
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        public Instant instant() {
            return now;
        }
    }
}
