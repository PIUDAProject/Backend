package com.piuda.callcare.domain.ocrresult.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piuda.callcare.domain.ocrresult.client.OpenAiClient;
import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;
import com.piuda.callcare.domain.ocrresult.enums.OcrType;
import com.piuda.callcare.domain.ocrresult.fixture.OcrFixtureLoader;
import com.piuda.callcare.domain.ocrresult.fixture.OcrFixtureLoader.FixtureCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * 실제 OpenAI를 호출해 fixture 9종에서 파서 / LLM / 하이브리드 추출 정확도를 비교한다(리포트).
 * OPENAI_API_KEY 환경변수가 있을 때만 실행. 호출 비용이 발생하므로 @Tag("integration").
 * <p>
 * exactR = 4개 필드(이름·1회량·1일횟수·총일수)가 정답과 완전히 일치하는 약의 비율.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@DisplayName("OCR 추출 벤치 — 파서 vs LLM vs 하이브리드")
class LlmDrugExtractorComparisonTest {

    private final OcrParser parser = new OcrParser();
    private final LlmDrugExtractor llm = buildLlmExtractor();

    @Test
    void 벤치_리포트() {
        String model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o");
        System.out.printf("%n=== OCR 추출 벤치 (LLM = %s) ===%n", model);
        System.out.printf("%-34s %-12s %6s %6s %6s%n", "fixture", "하이브리드경로", "파서", "LLM", "하이브리드");
        System.out.println("-".repeat(78));

        double sumParser = 0, sumLlm = 0, sumHybrid = 0;
        int n = 0;

        for (FixtureCase fc : OcrFixtureLoader.loadManifest().fixtures()) {
            if (!OcrFixtureLoader.exists(fc.file())) continue;
            List<NaverOcrApiResponse.Field> fields = OcrFixtureLoader.loadFields(fc.file());
            OcrType type = OcrType.valueOf(fc.ocrType());

            List<ParsedOcrData> byParser = parser.parse(fields, type).parsedDrugs();
            List<ParsedOcrData> byLlm = llm.extract(fields);

            boolean useParser = parser.isPrescription(fields) && !byParser.isEmpty();
            List<ParsedOcrData> hybrid = useParser ? byParser : (byLlm.isEmpty() ? byParser : byLlm);
            String route = useParser ? "파서(처방전)" : (byLlm.isEmpty() ? "파서(폴백)" : "LLM");

            double p = recall(fc.expected(), byParser);
            double l = recall(fc.expected(), byLlm);
            double h = recall(fc.expected(), hybrid);
            sumParser += p; sumLlm += l; sumHybrid += h; n++;

            System.out.printf("%-34s %-12s %6.2f %6.2f %6.2f%n", fc.file(), route, p, l, h);
        }

        System.out.println("-".repeat(78));
        System.out.printf("%-34s %-12s %6.2f %6.2f %6.2f%n", "평균", "", sumParser / n, sumLlm / n, sumHybrid / n);
        System.out.println("\n(exactR = 정답과 4개 필드 완전 일치한 약의 비율. LLM은 temperature 0이어도 run마다 소폭 변동)");
    }

    private double recall(List<ParsedOcrData> expected, List<ParsedOcrData> actual) {
        if (expected.isEmpty()) return 0;
        long hit = expected.stream().filter(actual::contains).count();
        return (double) hit / expected.size();
    }

    private static LlmDrugExtractor buildLlmExtractor() {
        OpenAiClient client = new OpenAiClient(WebClient.create(), new ObjectMapper());
        ReflectionTestUtils.setField(client, "apiKey", System.getenv("OPENAI_API_KEY"));
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(client, "model", System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o"));
        return new LlmDrugExtractor(client);
    }
}
