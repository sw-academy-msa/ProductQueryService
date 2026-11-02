package com.msa.productquery.domain.product.query.controller;

import com.msa.productquery.domain.product.query.service.ProductQueryService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 게시된 조회 모델에 존재하는 필터 목록입니다. 등록 가능한 최종 카테고리를 임의로 생성하지 않습니다. */
@RestController
public class CategoryQueryController {
    private final ProductQueryService service;

    public CategoryQueryController(ProductQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/categories")
    public List<String> categories() {
        return service.categories();
    }
}
