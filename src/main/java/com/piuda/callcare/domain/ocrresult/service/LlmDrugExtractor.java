package com.piuda.callcare.domain.ocrresult.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.piuda.callcare.domain.ocrresult.client.OpenAiClient;
import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM(OpenAI) 기반 약 추출기. 키 미설정·API 오류·빈 응답이면 빈 리스트를 반환해 파서 폴백을 유도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmDrugExtractor implements DrugExtractor {

    private static final int MAX_TIMES_PER_DAY = 6;
    private static final int MAX_TOTAL_DAYS = 90;

    private final OpenAiClient openAiClient;

    @Override
    public List<ParsedOcrData> extract(List<NaverOcrApiResponse.Field> fields) {
        if (!openAiClient.isEnabled() || fields == null || fields.isEmpty()) {
            return List.of();
        }
        try {
            JsonNode drugs = openAiClient.extractDrugs(fields);
            List<ParsedOcrData> result = new ArrayList<>();
            for (JsonNode d : drugs) {
                String name = text(d, "drugName");
                if (name == null) continue;
                result.add(new ParsedOcrData(
                        name,
                        text(d, "dosagePerTime"),
                        boundedInt(d, "timesPerDay", MAX_TIMES_PER_DAY),
                        boundedInt(d, "totalDays", MAX_TOTAL_DAYS)
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("LLM 약 추출 실패 - 파서로 폴백", e);
            return List.of();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    // 범위를 벗어나는 값은 환각으로 보고 null 처리
    private Integer boundedInt(JsonNode node, String field, int max) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.canConvertToInt()) return null;
        int n = v.asInt();
        return (n >= 1 && n <= max) ? n : null;
    }
}
