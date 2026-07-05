package com.piuda.callcare.domain.drugconflict.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;

// 약물 충돌 판정 순수 로직(4단계). DB/Spring 없이 단위테스트 가능(1단계 계산기 분리 패턴).
//
// 매칭 전략(실 데이터 기준): intrc_qesitm은 자유서술이며 상호작용 대상을 "제품명"이 아니라
// "약물 계열/성분/분류명"으로 서술한다(예: "테트라사이클린계", "제산제", "MAO억제제", "와파린").
// 따라서 각 약의 정체를 나타내는 식별어를 뽑아, 상대 약의 상호작용 텍스트에 그 식별어가
// 그대로 등장하는지(substring)로 충돌을 판정한다.
//
// 식별어 소스(재사용 가능한 두 신호):
//   1) prduct_type 분류명: "[01410]항히스타민제" → 코드/괄호 제거 후 "항히스타민제"
//   2) item_name 괄호 안 성분명: "휴덱시연질캡슐(덱시부프로펜)" → "덱시부프로펜"
//
// 트레이드오프(의도된 근사치):
//   - 과탐지: 분류명이 광범위(제산제·해열진통제)하고 substring 매칭이라 느슨하게 잡힐 수 있음
//            (현재는 "놓치기보다 넉넉히 잡기" 정책이라 허용).
//   - 누락: 동의어 확장이 없어 "비스테로이드성 소염진통제" vs 분류 "해열진통제"처럼 표현이
//          다르면 못 잡음. 완벽한 상호작용 검사(식약처 DUR 등)가 아닌 보유 데이터 기반 근사치.
@Component
public class DrugConflictMatcher {

    // 한 글자 성분/분류명의 우연한 매칭을 막는 최소 길이
    private static final int MIN_TERM_LENGTH = 2;

    // prduct_type 앞의 분류코드 "[01410]" 제거용
    private static final Pattern PRODUCT_TYPE_CODE = Pattern.compile("^\\[[^\\]]*]");
    // 괄호와 그 안 내용 추출용
    private static final Pattern PARENS = Pattern.compile("\\(([^)]*)\\)");
    // 성분 나열 구분자
    private static final Pattern INGREDIENT_DELIM = Pattern.compile("[,·/‧]");
    // 문장 분리: "~시오." 뒤(붙어있어도 분리) 또는 줄바꿈
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=시오\\.)|\\n+");

    // 한 방향의 매칭 결과(어떤 문장이 어떤 등급으로 걸렸는지)
    public record ConflictMatch(ConflictSeverity severity, String description) {
    }

    // 두 약의 상호작용을 양방향으로 검사. 충돌이면 가장 심각한 매칭을 반환, 아니면 empty.
    public Optional<ConflictMatch> match(DrugInfo a, DrugInfo b) {
        List<ConflictMatch> matches = new ArrayList<>();
        collectWarnings(a, extractIdentityTerms(b), matches); // a의 경고문이 b(계열/성분)를 지목?
        collectWarnings(b, extractIdentityTerms(a), matches); // 반대 방향
        return matches.stream()
                .max(Comparator.comparingInt(m -> m.severity().getPriority()));
    }

    // 약을 식별하는 용어 집합: prduct_type 분류명 + item_name 괄호 안 성분명
    public Set<String> extractIdentityTerms(DrugInfo drug) {
        Set<String> terms = new LinkedHashSet<>();
        addProductTypeClass(drug.getPrductType(), terms);
        addIngredientTokens(drug.getItemName(), terms);
        return terms;
    }

    // source의 상호작용 텍스트에서 targetTerms 중 하나라도 등장하면, 그 문장을 등급과 함께 수집
    private void collectWarnings(DrugInfo source, Set<String> targetTerms, List<ConflictMatch> out) {
        String intrc = source.getIntrcQesitm();
        if (intrc == null || intrc.isBlank()) {
            return;
        }
        for (String term : targetTerms) {
            if (intrc.contains(term)) {
                String sentence = extractSentence(intrc, term);
                out.add(new ConflictMatch(classify(sentence), sentence));
            }
        }
    }

    // term이 포함된 문장만 추출(카드에 보여줄 설명). 문장 분리 실패 시 전체 텍스트 반환.
    private String extractSentence(String intrc, String term) {
        for (String sentence : SENTENCE_SPLIT.split(intrc)) {
            String trimmed = sentence.trim();
            if (trimmed.contains(term)) {
                return trimmed;
            }
        }
        return intrc.trim();
    }

    // 문장 말투로 심각도 판정: "하지 마"/"피하" → 금기, 그 외("상의"/"주의" 포함)는 주의
    private ConflictSeverity classify(String sentence) {
        if (sentence.contains("하지 마") || sentence.contains("피하")) {
            return ConflictSeverity.CONTRAINDICATED;
        }
        return ConflictSeverity.CAUTION;
    }

    private void addProductTypeClass(String prductType, Set<String> terms) {
        if (prductType == null || prductType.isBlank()) {
            return;
        }
        String cls = PRODUCT_TYPE_CODE.matcher(prductType).replaceFirst("");
        cls = PARENS.matcher(cls).replaceAll("").trim(); // "혼합비타민제(...)" → "혼합비타민제"
        if (cls.length() >= MIN_TERM_LENGTH) {
            terms.add(cls);
        }
    }

    private void addIngredientTokens(String itemName, Set<String> terms) {
        if (itemName == null) {
            return;
        }
        Matcher parens = PARENS.matcher(itemName);
        while (parens.find()) {
            for (String token : INGREDIENT_DELIM.split(parens.group(1))) {
                String trimmed = token.trim();
                // "수출명:..." 같은 부가 표기는 성분명이 아니므로 제외
                if (trimmed.length() >= MIN_TERM_LENGTH && !trimmed.contains(":")) {
                    terms.add(trimmed);
                }
            }
        }
    }
}