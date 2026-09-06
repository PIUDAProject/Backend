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
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.stream.Stream;

/**
 * 실제 OpenAI를 호출해 fixture 9종에서 파서 vs LLM 추출 정확도를 비교한다(리포트).
 * OPENAI_API_KEY 환경변수가 있을 때만 실행. 호출 비용이 발생하므로 @Tag("integration").
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@DisplayName("파서 vs LLM 추출 비교")
class LlmDrugExtractorComparisonTest {

    private final OcrParser parser = new OcrParser();
    private final LlmDrugExtractor llm = buildLlmExtractor();

    @TestFactory
    Stream<DynamicTest> 비교_리포트() {
        return OcrFixtureLoader.loadManifest().fixtures().stream()
                .filter(fc -> OcrFixtureLoader.exists(fc.file()))
                .map(fc -> DynamicTest.dynamicTest(fc.file(), () -> {
                    List<NaverOcrApiResponse.Field> fields = OcrFixtureLoader.loadFields(fc.file());
                    OcrType type = OcrType.valueOf(fc.ocrType());

                    List<ParsedOcrData> byParser = parser.parse(fields, type).parsedDrugs();
                    List<ParsedOcrData> byLlm = llm.extract(fields);

                    boolean useParser = parser.hasCodedPrescriptionLines(fields) && !byParser.isEmpty();
                    List<ParsedOcrData> hybrid = useParser ? byParser
                            : (byLlm.isEmpty() ? byParser : byLlm);
                    String route = useParser ? "파서(처방전)" : (byLlm.isEmpty() ? "파서(폴백)" : "LLM");

                    System.out.printf("[%s] 기대 %d | 파서 %.2f | LLM %.2f | 하이브리드 %.2f (%s)%n",
                            fc.file(), fc.expected().size(),
                            recall(fc.expected(), byParser), recall(fc.expected(), byLlm),
                            recall(fc.expected(), hybrid), route);
                }));
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
