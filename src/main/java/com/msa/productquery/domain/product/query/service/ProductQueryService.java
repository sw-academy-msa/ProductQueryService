package com.msa.productquery.domain.product.query.service;

import com.msa.productquery.domain.product.query.dto.request.ProductSearchRequest;
import com.msa.productquery.domain.product.query.dto.response.QueryResponses.*;
import com.msa.productquery.domain.projection.event.ProductSnapshot;

import java.time.LocalDate;
import java.util.*;

/** 상품·옵션·FAQ·일정·본인 문의를 자기 조회 모델에서만 읽습니다. */
public interface ProductQueryService {
    Page<Card> list(ProductSearchRequest request);

    Detail detail(UUID product);

    List<Option> options(UUID product);

    List<ProductSnapshot.Faq> faqs(UUID product);

    List<String> categories();

    Schedule schedule(LocalDate from, LocalDate to);

    Page<Inquiry> ownInquiries(UUID buyer, UUID product, int page, int size);
}
