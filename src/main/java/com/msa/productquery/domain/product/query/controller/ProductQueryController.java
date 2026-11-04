package com.msa.productquery.domain.product.query.controller;

import com.msa.productquery.domain.product.query.dto.request.ProductSearchRequest;
import com.msa.productquery.domain.product.query.dto.response.QueryResponses.*;
import com.msa.productquery.domain.product.query.service.ProductQueryService;
import com.msa.productquery.global.response.ApiResponse;
import com.msa.productquery.global.response.SuccessCode;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/** 공개 GET만 제공하며 구매·재고 확보·상품 변경은 다른 Command API가 담당합니다. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductQueryController {
    private final ProductQueryService service;

    public ProductQueryController(ProductQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Page<Card>> list(
            @org.springdoc.core.annotations.ParameterObject @Valid @ModelAttribute
                    ProductSearchRequest request) {
        Page<Card> response = service.list(request);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/schedule")
    public ApiResponse<Schedule> schedule(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        Schedule response = service.schedule(from, to);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> detail(@PathVariable UUID id) {
        Detail response = service.detail(id);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/{id}/options")
    public ApiResponse<List<Option>> options(@PathVariable UUID id) {
        List<Option> response = service.options(id);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/{id}/faqs")
    public ApiResponse<List<Faq>> faqs(@PathVariable UUID id) {
        List<Faq> response = service.faqs(id);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/{id}/inquiries")
    @PreAuthorize("hasRole('BUYER')")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Page<Inquiry>> inquiries(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Inquiry> response =
                service.ownInquiries(UUID.fromString(jwt.getSubject()), id, page, size);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
