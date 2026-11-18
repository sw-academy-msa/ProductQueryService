package com.msa.productquery.global.config.security;

import com.msa.productquery.global.error.*;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** 공개 상품 GET과 본인 문의 GET을 구분하며 모든 변경 요청은 차단합니다. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain querySecurity(HttpSecurity http, ObjectMapper mapper, Environment env)
            throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return http.csrf(c -> c.disable())
                .formLogin(c -> c.disable())
                .httpBasic(c -> c.disable())
                .logout(c -> c.disable())
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        c ->
                                c.authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        error(
                                                                res,
                                                                CommonErrorCode.UNAUTHORIZED,
                                                                mapper))
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        error(
                                                                res,
                                                                CommonErrorCode.FORBIDDEN,
                                                                mapper)))
                .authorizeHttpRequests(
                        c -> {
                            c.requestMatchers(
                                            HttpMethod.GET,
                                            "/actuator/health",
                                            "/actuator/health/readiness",
                                            "/actuator/health/liveness")
                                    .permitAll();
                            // 테스트의 실제 OpenAPI 추출도 허용하되 prod가 함께 지정되면 공개하지 않습니다.
                            if (env.acceptsProfiles(Profiles.of("(local | test) & !prod"))) {
                                c.requestMatchers(
                                                HttpMethod.GET,
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll();
                            }
                            c.requestMatchers(HttpMethod.GET, "/api/v1/products/*/inquiries")
                                    .hasRole("BUYER");
                            c.requestMatchers(
                                            HttpMethod.GET,
                                            "/api/v1/products",
                                            "/api/v1/products/schedule",
                                            "/api/v1/products/*",
                                            "/api/v1/products/*/options",
                                            "/api/v1/products/*/faqs",
                                            "/api/v1/categories")
                                    .permitAll();
                            c.anyRequest().denyAll();
                        })
                .oauth2ResourceServer(
                        c ->
                                c.jwt(j -> j.jwtAuthenticationConverter(converter))
                                        .authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        error(
                                                                res,
                                                                CommonErrorCode.UNAUTHORIZED,
                                                                mapper)))
                .build();
    }

    private void error(HttpServletResponse response, ErrorCode code, ObjectMapper mapper)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (code == CommonErrorCode.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, MDC.get("traceId")));
    }
}
