package com.piuda.callcare.domain.ocrresult.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;

import java.io.InputStream;
import java.util.List;

/**
 * 저장된 Naver OCR 응답(fixture)과 기대 약 목록(manifest)을 읽어 테스트에 넘긴다.
 * fixture JSON은 실제 응답과 동일한 형태({@link NaverOcrApiResponse})다.
 */
public final class OcrFixtureLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIXTURE_DIR = "/ocr/fixtures/";
    private static final String MANIFEST_PATH = "/ocr/expected/manifest.json";

    private OcrFixtureLoader() {
    }

    public static List<NaverOcrApiResponse.Field> loadFields(String fixtureFile) {
        try (InputStream in = open(FIXTURE_DIR + fixtureFile)) {
            NaverOcrApiResponse response = MAPPER.readValue(in, NaverOcrApiResponse.class);
            return response.images().get(0).fields();
        } catch (Exception e) {
            throw new IllegalStateException("fixture 로드 실패: " + fixtureFile, e);
        }
    }

    public static Manifest loadManifest() {
        try (InputStream in = open(MANIFEST_PATH)) {
            return MAPPER.readValue(in, Manifest.class);
        } catch (Exception e) {
            throw new IllegalStateException("manifest 로드 실패", e);
        }
    }

    private static InputStream open(String path) {
        InputStream in = OcrFixtureLoader.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("리소스 없음: " + path);
        }
        return in;
    }

    public record Manifest(List<FixtureCase> fixtures) {
    }

    public record FixtureCase(
            String file,
            String ocrType,
            boolean guard,
            String note,
            List<ParsedOcrData> expected
    ) {
    }
}
