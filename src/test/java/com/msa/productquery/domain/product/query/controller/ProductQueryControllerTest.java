package com.msa.productquery.domain.product.query.controller;

import static com.msa.productquery.domain.projection.EventFixtures.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.productquery.domain.projection.event.*;
import com.msa.productquery.domain.projection.repository.*;
import com.msa.productquery.domain.projection.service.ProjectionService;
import com.msa.productquery.global.config.database.QueryDatabaseTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

/** 실제 조회 DB와 MVC로 Figma의 목록·상세·카운터·일정·본인 문의 경계를 확인합니다. */
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(QueryDatabaseTestSupport.Time.class)
class ProductQueryControllerTest extends QueryDatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ProjectionService service;
    @Autowired ProductProjectionRepository products;
    @Autowired InquiryProjectionRepository inquiries;
    @Autowired ProcessedEventRepository receipts;
    @Autowired ObjectMapper mapper;
    @Autowired MutableClock clock;
    @MockitoBean JwtDecoder decoder;
    UUID product;

    @BeforeEach
    void setup() {
        receipts.deleteAll();
        inquiries.deleteAll();
        products.deleteAll();
        clock.set(NOW);
        product = UUID.randomUUID();
    }

    void add(ProjectionEvent event) {
        service.apply(
                event.type() == ProjectionEvent.Type.INQUIRY_UPSERT ? INQUIRY_TOPIC : PRODUCT_TOPIC,
                event.inquiry() == null
                        ? event.aggregateId().toString()
                        : event.inquiry().productId().toString(),
                mapper.writeValueAsBytes(event));
    }

    @Test
    void anonymousCanReadApprovedDetailsOptionsFaqsAndCategories() throws Exception {
        add(product(product, 1, "새 노트", NOW.minusSeconds(60), 2, 1));
        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_SUCCESS"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[0].name").value("새 노트"));
        mvc.perform(get("/api/v1/products/" + product))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.stock.availableQuantity").value(7))
                .andExpect(jsonPath("$.data.stock.confirmedQuantity").value(1))
                .andExpect(jsonPath("$.data.stock.approximate").value(true))
                .andExpect(jsonPath("$.data.dispatchAddress").doesNotExist())
                .andExpect(jsonPath("$.data.attributes[0].name").value("규격"))
                .andExpect(jsonPath("$.data.attributes[0].value").value("A5"))
                .andExpect(jsonPath("$.data.faqs[0].answer").value("A5입니다."));
        mvc.perform(get("/api/v1/products/" + product + "/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].availableQuantity").value(7));
        mvc.perform(get("/api/v1/products/" + product + "/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].answer").value("A5입니다."));
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0]").value("문구"));
    }

    @Test
    void allReservedStockIsNotSoldOutAndTimeStateUsesCurrentServerClock() throws Exception {
        add(product(product, 1, "임시 확보", NOW, 10, 0));
        mvc.perform(get("/api/v1/products/" + product))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.canAttemptPurchase").value(false))
                .andExpect(jsonPath("$.data.stock.confirmedQuantity").value(0));
        clock.set(NOW.minusNanos(1));
        mvc.perform(get("/api/v1/products/" + product))
                .andExpect(jsonPath("$.data.status").value("UPCOMING"));
        clock.set(NOW.plusSeconds(10800));
        mvc.perform(get("/api/v1/products/" + product))
                .andExpect(jsonPath("$.data.status").value("ENDED"));
    }

    @Test
    void soldOutRequiresConfirmedQuantityAndSearchDoesNotTreatWildcardsAsOperators()
            throws Exception {
        add(product(product, 1, "특별 100% 노트", NOW.minusSeconds(60), 0, 10));
        add(product(UUID.randomUUID(), 1, "다른 노트", NOW.minusSeconds(60), 0, 0));
        mvc.perform(get("/api/v1/products").param("status", "SOLD_OUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/products").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/products").param("keyword", "판매처"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void hiddenProductIsAbsentFromEveryPublicReadAndOldEventCannotRestoreIt() throws Exception {
        add(product(product, 1, "공개", NOW, 0, 0));
        add(hidden(product, 3));
        add(product(product, 2, "늦은 과거", NOW, 0, 0));
        mvc.perform(get("/api/v1/products")).andExpect(jsonPath("$.data.totalElements").value(0));
        for (String path :
                List.of(
                        "/api/v1/products/" + product,
                        "/api/v1/products/" + product + "/options",
                        "/api/v1/products/" + product + "/faqs")) {
            mvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMON_RESOURCE_NOT_FOUND"));
        }
    }

    @Test
    void inquiriesRequireBuyerAndOnlyShowThatAccountsRows() throws Exception {
        add(product(product, 1, "문의 상품", NOW, 0, 0));
        UUID buyer = UUID.randomUUID(), other = UUID.randomUUID();
        add(inquiry(UUID.randomUUID(), product, buyer, 0, null));
        add(inquiry(UUID.randomUUID(), product, other, 0, "다른 문의 답변"));
        String path = "/api/v1/products/" + product + "/inquiries";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(
                        get(path)
                                .with(
                                        jwt().jwt(j -> j.subject(buyer.toString()))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].buyerId").doesNotExist());
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void writesAndInternalProjectionHttpAreClosedAndInvalidBearerIsRejected() throws Exception {
        mvc.perform(post("/api/v1/products").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(post("/internal/projections").with(jwt())).andExpect(status().isForbidden());
        when(decoder.decode("invalid-fixture")).thenThrow(new BadJwtException("fixture-detail"));
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer invalid-fixture"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dateScheduleAggregatesKstDayAndLimitsFutureCalendarToNextWeek() throws Exception {
        // UTC 전날 15시는 KST 당일 0시이며 일별 카운터의 경계입니다.
        add(product(product, 1, "일정", Instant.parse("2029-12-31T15:00:00Z"), 0, 0));
        mvc.perform(
                        get("/api/v1/products/schedule")
                                .param("from", "2030-01-01")
                                .param("to", "2030-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.zone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.days[0].date").value("2030-01-01"))
                .andExpect(jsonPath("$.data.days[0].productCount").value(1));
        mvc.perform(get("/api/v1/products/schedule").param("to", "2030-12-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void invalidPagingAndEnumAreBadRequests() throws Exception {
        mvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        mvc.perform(get("/api/v1/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        mvc.perform(get("/api/v1/products").param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    /** 지원하지 않는 Accept에도 406 JSON 오류 계약을 반환합니다. */
    @Test
    void unsupportedAcceptReturnsJsonError() throws Exception {
        mvc.perform(get("/api/v1/products").accept("application/xml"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("COMMON_NOT_ACCEPTABLE"));
    }

    @Test
    void exportsActualOpenApiWithoutCreatingAnHttpProjectionWriteEndpoint() throws Exception {
        var result =
                mvc.perform(get("/v3/api-docs").with(jwt())).andExpect(status().isOk()).andReturn();
        var path = java.nio.file.Path.of("build/generated/product-query.openapi.json");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(
                path,
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        // 표기 순서가 아닌 실제 API 계약 변경을 검출합니다.
        org.assertj.core.api.Assertions.assertThat(
                        mapper.readTree(java.nio.file.Files.readString(path)))
                .isEqualTo(
                        mapper.readTree(
                                java.nio.file.Files.readString(
                                        java.nio.file.Path.of(
                                                "openapi/product-query.openapi.json"))));
    }
}
