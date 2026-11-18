package com.msa.productquery.domain.product.query.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** 빌더도 record의 목록 복사 규칙과 비공개 문의 로그 보호를 유지하는지 검증합니다. */
class QueryResponsesTest {
    @Test
    void buildersPreserveDefensiveCopiesInCanonicalConstructors() {
        var categories = new ArrayList<>(List.of("문구"));
        var values = new ArrayList<>(List.of("A5"));
        var response = QueryResponses.Categories.builder().categories(categories).build();
        var dimension = QueryResponses.Dimension.builder().name("규격").values(values).build();
        categories.add("의류");
        values.clear();

        assertThat(response.categories()).containsExactly("문구");
        assertThat(dimension.values()).containsExactly("A5");
        assertThatThrownBy(() -> response.categories().add("추가"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> dimension.values().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void inquiryBuilderDoesNotExposeQuestionOrAnswerInLogs() {
        var builder = QueryResponses.Inquiry.builder().question("합성 비공개 문의").answer("합성 비공개 답변");

        assertThat(builder.toString()).isEqualTo("InquiryBuilder[REDACTED]");
    }
}
