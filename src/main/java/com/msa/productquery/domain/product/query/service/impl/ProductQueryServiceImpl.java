package com.msa.productquery.domain.product.query.service.impl;

import com.msa.productquery.domain.product.exception.ProductQueryErrorCode;
import com.msa.productquery.domain.product.exception.ProductQueryException;
import com.msa.productquery.domain.product.query.dto.request.ProductSearchRequest;
import com.msa.productquery.domain.product.query.dto.response.QueryResponses.*;
import com.msa.productquery.domain.product.query.service.*;
import com.msa.productquery.domain.projection.entity.ProductProjection;
import com.msa.productquery.domain.projection.event.ProductSnapshot;
import com.msa.productquery.domain.projection.repository.*;

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
            throw new ProductQueryException(ProductQueryErrorCode.INVALID_REQUEST);
        }
        var result =
                products.findAll(specification(r, now), paging(r.page(), r.size(), sort(r.sort())));
        return Page.<Card>builder()
                .items(result.getContent().stream().map(p -> card(p, now)).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .serverTime(now)
                .build();
    }

    public Detail detail(UUID id) {
        var row = published(id);
        var p = snapshot(row);
        Instant now = clock.instant();
        var status = status(row, now);
        return Detail.builder()
                .id(id)
                .sellerId(p.sellerId())
                .name(p.name())
                .description(p.description())
                .brandOrStore(p.brandOrStore())
                .category(p.category())
                .sourceUrl(p.sourceUrl())
                .price(p.price())
                .shippingFee(p.shippingFee())
                .dispatchBusinessDays(p.dispatchBusinessDays())
                .perAccountLimit(p.perAccountLimit())
                .shippingNotice(p.shippingNotice())
                .purchaseNotice(p.purchaseNotice())
                .disposalReason(p.disposalReason())
                .attributes(
                        p.attributes().stream()
                                .map(
                                        a ->
                                                Attribute.builder()
                                                        .name(a.name())
                                                        .value(a.value())
                                                        .build())
                                .toList())
                .dimensions(
                        p.dimensions().stream()
                                .map(
                                        d ->
                                                Dimension.builder()
                                                        .name(d.name())
                                                        .values(d.values())
                                                        .build())
                                .toList())
                .options(p.options().stream().map(this::option).toList())
                .images(
                        p.images().stream()
                                .sorted(Comparator.comparingInt(ProductSnapshot.Image::position))
                                .map(this::image)
                                .toList())
                .faqs(p.faqs().stream().map(this::faq).toList())
                .status(status)
                .startsAt(p.startsAt())
                .endsAt(p.endsAt())
                .soldOutAt(p.soldOutAt())
                .stock(stock(row))
                .metadata(metadata(row))
                .canAttemptPurchase(status == SaleStatus.ON_SALE && row.availableQuantity > 0)
                .serverTime(now)
                .build();
    }

    public List<Option> options(UUID id) {
        return snapshot(published(id)).options().stream().map(this::option).toList();
    }

    public List<Faq> faqs(UUID id) {
        return snapshot(published(id)).faqs().stream().map(this::faq).toList();
    }

    public Categories categories() {
        return Categories.builder().categories(products.findPublicCategories()).build();
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
            throw new ProductQueryException(ProductQueryErrorCode.INVALID_REQUEST);
        }
        var rows =
                products.schedule(
                        from.atStartOfDay(DISPLAY_ZONE).toInstant(),
                        to.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant());
        return Schedule.builder()
                .from(from)
                .to(to)
                .zone(DISPLAY_ZONE.getId())
                .days(
                        rows.stream()
                                .map(
                                        d ->
                                                SaleDay.builder()
                                                        .date(d.getSaleDate())
                                                        .productCount(d.getProductCount())
                                                        .firstStartsAt(d.getFirstStartsAt())
                                                        .build())
                                .toList())
                .serverTime(clock.instant())
                .build();
    }

    public Page<Inquiry> ownInquiries(UUID buyer, UUID product, int page, int size) {
        published(product);
        var result =
                inquiries.findByProductIdAndBuyerId(
                        product,
                        buyer,
                        paging(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));
        return Page.<Inquiry>builder()
                .items(
                        result.getContent().stream()
                                .map(
                                        i ->
                                                Inquiry.builder()
                                                        .id(i.id)
                                                        .question(i.question)
                                                        .answer(i.answer)
                                                        .createdAt(i.createdAt)
                                                        .answeredAt(i.answeredAt)
                                                        .projectedAt(i.projectedAt)
                                                        .build())
                                .toList())
                .page(page)
                .size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .serverTime(clock.instant())
                .build();
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
            throw new ProductQueryException(ProductQueryErrorCode.INVALID_REQUEST);
        }
        return PageRequest.of(page, size, sort);
    }

    private ProductProjection published(UUID id) {
        return products.findById(id)
                .filter(p -> p.published)
                .orElseThrow(() -> new ProductQueryException(ProductQueryErrorCode.NOT_FOUND));
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
        return Card.builder()
                .id(row.id)
                .name(row.name)
                .brandOrStore(row.brandOrStore)
                .category(row.category)
                .price(row.price)
                .image(image(representative))
                .status(status(row, now))
                .startsAt(row.startsAt)
                .endsAt(row.endsAt)
                .soldOutAt(p.soldOutAt())
                .stock(stock(row))
                .metadata(metadata(row))
                .build();
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
        return Stock.builder()
                .initialQuantity(row.initialQuantity)
                .confirmedQuantity(row.confirmedQuantity)
                .availableQuantity(row.availableQuantity)
                .approximate(true)
                .build();
    }

    private Metadata metadata(ProductProjection row) {
        return Metadata.builder()
                .version(row.sourceVersion)
                .occurredAt(row.sourceOccurredAt)
                .projectedAt(row.projectedAt)
                .build();
    }

    private Option option(ProductSnapshot.Option o) {
        return Option.builder()
                .id(o.id())
                .label(o.label())
                .selections(o.selections())
                .availableQuantity(o.availableQuantity())
                .build();
    }

    /** HTTP 응답은 공개 필드를 복사하며 이벤트 객체 자체를 반환하지 않습니다. */
    private Faq faq(ProductSnapshot.Faq f) {
        return Faq.builder()
                .id(f.id())
                .question(f.question())
                .answer(f.answer())
                .confirmedAt(f.confirmedAt())
                .build();
    }

    private Image image(ProductSnapshot.Image i) {
        return Image.builder()
                .id(i.id())
                .objectKey(i.objectKey())
                .url(images.resolve(i.objectKey()))
                .contentType(i.contentType())
                .position(i.position())
                .representative(i.representative())
                .build();
    }
}
