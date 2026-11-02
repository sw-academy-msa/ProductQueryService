package com.msa.productquery.domain.product.query.controller;

import com.msa.productquery.domain.product.query.dto.request.ProductSearchRequest;
import com.msa.productquery.domain.product.query.dto.response.QueryResponses.*;
import com.msa.productquery.domain.product.query.service.ProductQueryService;
import com.msa.productquery.domain.projection.event.ProductSnapshot;

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
    public Page<Card> list(@Valid @ModelAttribute ProductSearchRequest request) {
        return service.list(request);
    }

    @GetMapping("/schedule")
    public Schedule schedule(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return service.schedule(from, to);
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @GetMapping("/{id}/options")
    public List<Option> options(@PathVariable UUID id) {
        return service.options(id);
    }

    @GetMapping("/{id}/faqs")
    public List<ProductSnapshot.Faq> faqs(@PathVariable UUID id) {
        return service.faqs(id);
    }

    @GetMapping("/{id}/inquiries")
    @PreAuthorize("hasRole('BUYER')")
    @SecurityRequirement(name = "bearerAuth")
    public Page<Inquiry> inquiries(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.ownInquiries(UUID.fromString(jwt.getSubject()), id, page, size);
    }
}
