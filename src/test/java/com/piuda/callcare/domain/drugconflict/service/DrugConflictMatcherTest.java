package com.piuda.callcare.domain.drugconflict.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;
import com.piuda.callcare.domain.drugconflict.service.DrugConflictMatcher.ConflictMatch;
import com.piuda.callcare.domain.druginfo.entity.DrugInfo;

@DisplayName("DrugConflictMatcher 단위 테스트")
class DrugConflictMatcherTest {

    private final DrugConflictMatcher matcher = new DrugConflictMatcher();

    @Test
    @DisplayName("식별어: prduct_type 분류명(코드 제거)과 item_name 괄호 안 성분을 추출한다")
    void extractIdentityTerms_fromProductTypeAndIngredient() {
        DrugInfo drug = DrugInfo.builder()
                .itemName("휴덱시연질캡슐(덱시부프로펜)")
                .prductType("[01410]항히스타민제")
                .build();

        Set<String> terms = matcher.extractIdentityTerms(drug);

        assertThat(terms).contains("항히스타민제", "덱시부프로펜");
    }

    @Test
    @DisplayName("금기: 상대 분류명이 '~하지 마십시오' 문장에 걸리면 CONTRAINDICATED로 판정한다")
    void match_contraindicated_when_doNotTake() {
        DrugInfo cold = DrugInfo.builder()
                .itemName("코감기약")
                .intrcQesitm("항히스타민제를 함유하는 내복약과 함께 복용하지 마십시오.")
                .build();
        DrugInfo allergy = DrugInfo.builder()
                .itemName("알레르기약")
                .prductType("[01410]항히스타민제")
                .build();

        Optional<ConflictMatch> result = matcher.match(cold, allergy);

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(ConflictSeverity.CONTRAINDICATED);
        assertThat(result.get().description()).contains("항히스타민제").contains("복용하지 마십시오");
    }

    @Test
    @DisplayName("주의: '~상의하십시오' 문장에 걸리면 CAUTION으로 판정한다")
    void match_caution_when_consult() {
        DrugInfo mineral = DrugInfo.builder()
                .itemName("철분제")
                .intrcQesitm("항알도스테론제, 트리암테렌과 함께 복용 시 의사 또는 약사와 상의하십시오.")
                .build();
        DrugInfo diuretic = DrugInfo.builder()
                .itemName("이뇨제(트리암테렌)")
                .build();

        Optional<ConflictMatch> result = matcher.match(mineral, diuretic);

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(ConflictSeverity.CAUTION);
        assertThat(result.get().description()).contains("트리암테렌");
    }

    @Test
    @DisplayName("여러 문장에 걸리면 더 심각한 등급(금기)을 선택한다")
    void match_picksMostSevere() {
        // other의 두 식별어가 각각: 분류 '항히스타민제'는 '상의'(주의) 문장에, 성분 '덱시부프로펜'은 '하지 마'(금기) 문장에 걸림
        DrugInfo painkiller = DrugInfo.builder()
                .itemName("종합감기약")
                .intrcQesitm("항히스타민제와 함께 복용 시 의사와 상의하십시오.\n\n덱시부프로펜과 함께 복용하지 마십시오.")
                .build();
        DrugInfo other = DrugInfo.builder()
                .itemName("진통제(덱시부프로펜)")
                .prductType("[01410]항히스타민제")
                .build();

        Optional<ConflictMatch> result = matcher.match(painkiller, other);

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(ConflictSeverity.CONTRAINDICATED);
    }

    @Test
    @DisplayName("겹치는 계열/성분이 없으면 충돌 아님(empty)")
    void match_empty_when_noOverlap() {
        DrugInfo a = DrugInfo.builder()
                .itemName("비타민C정")
                .prductType("[03160]혼합비타민제")
                .intrcQesitm("특별한 상호작용 정보가 없습니다.")
                .build();
        DrugInfo b = DrugInfo.builder()
                .itemName("소화제(판크레아틴)")
                .prductType("[02330]효소제제")
                .intrcQesitm("특별한 상호작용 정보가 없습니다.")
                .build();

        assertThat(matcher.match(a, b)).isEmpty();
    }

    @Test
    @DisplayName("상호작용 텍스트가 없으면(null) 충돌 아님")
    void match_empty_when_intrcNull() {
        DrugInfo a = DrugInfo.builder().itemName("A약(항히스타민제성분)").prductType("[01410]항히스타민제").build();
        DrugInfo b = DrugInfo.builder().itemName("B약").prductType("[01410]항히스타민제").build();

        assertThat(matcher.match(a, b)).isEmpty();
    }
}