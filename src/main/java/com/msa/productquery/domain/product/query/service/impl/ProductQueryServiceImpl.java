package com.msa.productquery.domain.product.query.service.impl;

import com.msa.productquery.domain.product.query.dto.request.ProductSearchRequest;
import com.msa.productquery.domain.product.query.dto.response.QueryResponses.*;
import com.msa.productquery.domain.product.query.service.*;
import com.msa.productquery.domain.projection.entity.ProductProjection;
import com.msa.productquery.domain.projection.event.ProductSnapshot;
import com.msa.productquery.domain.projection.repository.*;
import com.msa.productquery.global.error.*;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** 공개 여부를 모든 상품 조회에 적용하고, 시간 상태는 캐시된 과거 값 대신 현재 서버 UTC로 계산합니다. */
@Service
@Transactional(readOnly = true)
public class ProductQueryServiceImpl implements ProductQueryService {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");
    private final ProductProjectionRepository products;
    private final InquiryProjectionRepository inquiries;
    private final ImageUrlService images;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ProductQueryServiceImpl(
            ProductProjectionRepository products,
            InquiryProjectionRepository inquiries,
            ImageUrlService images,
            ObjectMapper mapper,
            Clock clock) {
        this.products = products;
        this.inquiries = inquiries;
        this.images = images;
        this.mapper = mapper;
        this.clock = clock;
    }

    public Page<Card> list(ProductSearchRequest r) {
        Instant now = clock.instant();
        if (r.startsFrom() != null
                && r.startsTo() != null
                && !r.startsTo().isAfter(r.startsFrom())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        var result =
                products.findAll(specification(r, now), paging(r.page(), r.size(), sort(r.sort())));
        return new Page<>(
                result.getContent().stream().map(p -> card(p, now)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                now);
    }

    public Detail detail(UUID id) {
        var row = published(id);
        var p = snapshot(row);
        Instant now = clock.instant();
        var status = status(row, now);
        return new Detail(
                id,
                p.sellerId(),
                p.name(),
                p.description(),
                p.brandOrStore(),
                p.category(),
                p.sourceUrl(),
                p.price(),
                p.shippingFee(),
                p.dispatchBusinessDays(),
                p.perAccountLimit(),
                p.shippingNotice(),
                p.purchaseNotice(),
                p.disposalReason(),
                p.attributes(),
                p.dimensions(),
                p.options().stream().map(this::option).toList(),
                p.images().stream()
                        .sorted(Comparator.comparingInt(ProductSnapshot.Image::position))
                        .map(this::image)
                        .toList(),
                p.faqs(),
                status,
                p.startsAt(),
                p.endsAt(),
                p.soldOutAt(),
                stock(row),
                metadata(row),
                status == SaleStatus.ON_SALE && row.availableQuantity > 0,
                now);
    }

    public List<Option> options(UUID id) {
        return snapshot(published(id)).options().stream().map(this::option).toList();
    }

    public List<ProductSnapshot.Faq> faqs(UUID id) {
        return snapshot(published(id)).faqs();
    }

    public List<String> categories() {
        return products.findPublicCategories();
    }

    /** 다음 주까지의 달력 범위는 KST로 계산하며 UTC 경계로 DB를 집계합니다. */
    public Schedule schedule(LocalDate from, LocalDate to) {
        var today = LocalDate.now(clock.withZone(DISPLAY_ZONE));
        var nextSunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusWeeks(1);
        from = from == null ? today : from;
        to = to == null ? nextSunday : to;
        if (to.isBefore(from)
                || to.isAfter(nextSunday)
                || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 30) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        var rows =
                products.schedule(
                        from.atStartOfDay(DISPLAY_ZONE).toInstant(),
                        to.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant());
        return new Schedule(
                from,
                to,
                DISPLAY_ZONE.getId(),
                rows.stream()
                        .map(
                                d ->
                                        new SaleDay(
                                                d.getSaleDate(),
                                                d.getProductCount(),
                                                d.getFirstStartsAt()))
                        .toList(),
                clock.instant());
    }

    public Page<Inquiry> ownInquiries(UUID buyer, UUID product, int page, int size) {
        published(product);
        var result =
                inquiries.findByProductIdAndBuyerId(
                        product,
                        buyer,
                        paging(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));
        return new Page<>(
                result.getContent().stream()
                        .map(
                                i ->
                                        new Inquiry(
                                                i.id,
                                                i.question,
                                                i.answer,
                                                i.createdAt,
                                                i.answeredAt,
                                                i.projectedAt))
                        .toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages(),
                clock.instant());
    }

    private Specification<ProductProjection> specification(ProductSearchRequest r, Instant now) {
        return (root, query, cb) -> {
            var filters = new ArrayList<Predicate>();
            filters.add(cb.isTrue(root.get("published")));
            if (r.keyword() != null && !r.keyword().isBlank()) {
                String pattern =
                        "%"
                                + r.keyword()
                                        .trim()
                                        .toLowerCase(Locale.ROOT)
                                        .replace("!", "!!")
                                        .replace("%", "!%")
                                        .replace("_", "!_")
                                + "%";
                filters.add(
                        cb.or(
                                cb.like(cb.lower(root.get("name")), pattern, '!'),
                                cb.like(cb.lower(root.get("brandOrStore")), pattern, '!'),
                                cb.like(cb.lower(root.get("category")), pattern, '!')));
            }
            if (r.category() != null && !r.category().isBlank()) {
                filters.add(cb.equal(root.get("category"), r.category()));
            }
            if (r.startsFrom() != null) {
                filters.add(cb.greaterThanOrEqualTo(root.get("startsAt"), r.startsFrom()));
            }
            if (r.startsTo() != null) {
                filters.add(cb.lessThan(root.get("startsAt"), r.startsTo()));
            }
            if (r.status() != null) {
                var sold = cb.equal(root.get("confirmedQuantity"), root.get("initialQuantity"));
                filters.add(
                        switch (r.status()) {
                            case UPCOMING -> cb.greaterThan(root.get("startsAt"), now);
                            case SOLD_OUT ->
                                    cb.and(cb.lessThanOrEqualTo(root.get("startsAt"), now), sold);
                            case ENDED ->
                                    cb.and(
                                            cb.lessThanOrEqualTo(root.get("endsAt"), now),
                                            cb.not(sold));
                            case ON_SALE ->
                                    cb.and(
                                            cb.lessThanOrEqualTo(root.get("startsAt"), now),
                                            cb.greaterThan(root.get("endsAt"), now),
                                            cb.not(sold));
                        });
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }

    private Sort sort(ProductSearchRequest.Sort value) {
        Sort primary =
                switch (value) {
                    case NEWEST -> Sort.by("publishedAt").descending();
                    case ENDING_SOON -> Sort.by("endsAt").ascending();
                    case PRICE_ASC -> Sort.by("price").ascending();
                    case PRICE_DESC -> Sort.by("price").descending();
                };
        return primary.and(Sort.by("id"));
    }

    private org.springframework.data.domain.Pageable paging(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return PageRequest.of(page, size, sort);
    }

    private ProductProjection published(UUID id) {
        return products.findById(id)
                .filter(p -> p.published)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    private ProductSnapshot snapshot(ProductProjection row) {
        return mapper.readValue(row.snapshot, ProductSnapshot.class);
    }

    private Card card(ProductProjection row, Instant now) {
        var p = snapshot(row);
        var representative =
                p.images().stream()
                        .filter(ProductSnapshot.Image::representative)
                        .findFirst()
                        .orElseThrow();
        return new Card(
                row.id,
                row.name,
                row.brandOrStore,
                row.category,
                row.price,
                image(representative),
                status(row, now),
                row.startsAt,
                row.endsAt,
                p.soldOutAt(),
                stock(row),
                metadata(row));
    }

    private SaleStatus status(ProductProjection row, Instant now) {
        if (now.isBefore(row.startsAt)) {
            return SaleStatus.UPCOMING;
        }
        if (row.confirmedQuantity == row.initialQuantity) {
            return SaleStatus.SOLD_OUT;
        }
        return now.isBefore(row.endsAt) ? SaleStatus.ON_SALE : SaleStatus.ENDED;
    }

    private Stock stock(ProductProjection row) {
        return new Stock(row.initialQuantity, row.confirmedQuantity, row.availableQuantity, true);
    }

    private Metadata metadata(ProductProjection row) {
        return new Metadata(row.sourceVersion, row.sourceOccurredAt, row.projectedAt);
    }

    private Option option(ProductSnapshot.Option o) {
        return new Option(o.id(), o.label(), o.selections(), o.availableQuantity());
    }

    private Image image(ProductSnapshot.Image i) {
        return new Image(
                i.id(),
                i.objectKey(),
                images.resolve(i.objectKey()),
                i.contentType(),
                i.position(),
                i.representative());
    }
}
