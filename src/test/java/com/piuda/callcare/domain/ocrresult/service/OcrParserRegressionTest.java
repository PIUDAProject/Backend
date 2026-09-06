package com.piuda.callcare.domain.ocrresult.service;

import com.piuda.callcare.domain.ocrresult.dto.OcrParseResult;
import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;
import com.piuda.callcare.domain.ocrresult.enums.OcrType;
import com.piuda.callcare.domain.ocrresult.fixture.OcrFixtureLoader;
import com.piuda.callcare.domain.ocrresult.fixture.OcrFixtureLoader.FixtureCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 저장된 실제/합성 OCR 응답을 {@link OcrParser}에 돌려 회귀를 잡는다.
 * <ul>
 *   <li>guard=true fixture: 현재 정상 동작 → 결과가 어긋나면 실패 (회귀 가드)</li>
 *   <li>guard=false fixture: 아직 미달 → precision/recall만 리포트, 실패시키지 않음
 *       (해당 이슈에서 fix + guard 승격)</li>
 * </ul>
 */
@DisplayName("OcrParser 회귀 테스트")
class OcrParserRegressionTest {

    private final OcrParser ocrParser = new OcrParser();

    @TestFactory
    @DisplayName("fixture별 precision/recall 리포트 + 회귀 가드")
    Stream<DynamicTest> 회귀_리포트() {
        List<FixtureCase> cases = OcrFixtureLoader.loadManifest().fixtures();
        return cases.stream().map(fc -> DynamicTest.dynamicTest(fc.file(), () -> {
            // 실사진 fixture는 팀이 마스킹 후 추가 — 없으면 스킵(가드는 유지)
            assumeTrue(OcrFixtureLoader.exists(fc.file()), "fixture 파일 없음: " + fc.file());
            List<NaverOcrApiResponse.Field> fields = OcrFixtureLoader.loadFields(fc.file());
            OcrParseResult result = ocrParser.parse(fields, OcrType.valueOf(fc.ocrType()));
            List<ParsedOcrData> actual = result.parsedDrugs();

            Score score = score(fc.expected(), actual);
            System.out.printf(
                    "[%s] exact P=%.2f R=%.2f | name R=%.2f | 기대 %d, 추출 %d, 정확일치 %d%n  ↳ %s%n",
                    fc.file(), score.precision(), score.recall(), score.nameRecall(),
                    fc.expected().size(), actual.size(), score.exactMatches(), fc.note());

            if (fc.guard()) {
                assertThat(actual)
                        .as("guard fixture는 기대 약 목록과 정확히 일치해야 한다: %s", fc.file())
                        .containsExactlyInAnyOrderElementsOf(fc.expected());
            }
        }));
    }

    @Test
    @DisplayName("정상 케이스: 별표형 영수증 - 약 4건, 병원명 '튼튼정'은 약으로 잡히지 않는다")
    void 별표형_영수증_약4건_병원명오탐없음() {
        // Given
        List<NaverOcrApiResponse.Field> fields = OcrFixtureLoader.loadFields("pharmacy_receipt_starred.json");

        // When
        List<ParsedOcrData> drugs = ocrParser.parse(fields, OcrType.PRESCRIPTION).parsedDrugs();

        // Then
        assertThat(drugs).hasSize(4);
        assertThat(drugs).extracting(ParsedOcrData::drugName)
                .containsExactly("아클펜정", "아트놀셋세미정", "모사피트정", "에페신정")
                .doesNotContain("튼튼정");
        assertThat(drugs).allSatisfy(d -> {
            assertThat(d.dosagePerTime()).isEqualTo("1정");
            assertThat(d.timesPerDay()).isEqualTo(2);
            assertThat(d.totalDays()).isEqualTo(5);
        });
    }

    // 기대 약 목록 대비 파싱 결과 채점. exact = 4개 필드 완전 일치, name = 약 이름만 일치
    private Score score(List<ParsedOcrData> expected, List<ParsedOcrData> actual) {
        long exact = expected.stream().filter(actual::contains).count();
        long nameHit = expected.stream()
                .filter(e -> actual.stream().anyMatch(a -> equalsIgnoreNull(e.drugName(), a.drugName())))
                .count();
        double precision = actual.isEmpty() ? 0.0 : (double) exact / actual.size();
        double recall = expected.isEmpty() ? 0.0 : (double) exact / expected.size();
        double nameRecall = expected.isEmpty() ? 0.0 : (double) nameHit / expected.size();
        return new Score(precision, recall, nameRecall, exact);
    }

    private boolean equalsIgnoreNull(String a, String b) {
        return a != null && a.equals(b);
    }

    private record Score(double precision, double recall, double nameRecall, long exactMatches) {
    }
}
