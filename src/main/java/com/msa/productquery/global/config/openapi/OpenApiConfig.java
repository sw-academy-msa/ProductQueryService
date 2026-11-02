package com.msa.productquery.global.config.openapi;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;

import org.springframework.context.annotation.*;

/** 실제 조회 컨트롤러를 계약의 기준으로 사용합니다. 개인키·쓰기 API는 제공하지 않습니다. */
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI queryOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("STOCKDROP Product Query")
                                .version("v1")
                                .description(
                                        "Kafka로 갱신한 자체 조회 모델만 사용합니다. 표시 재고는 확보 보장이 아니며 본인 문의는 BUYER JWT가 필요합니다."))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }
}
