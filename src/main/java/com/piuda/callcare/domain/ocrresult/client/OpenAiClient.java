package com.piuda.callcare.domain.ocrresult.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 호출 래퍼. OCR 필드(텍스트+좌표)를 주면 약 목록 JSON을 받아온다.
 * <p>
 * {@code openai.api-key}가 비어 있으면 비활성({@link #isEnabled()} false) — 상위에서 파서로 폴백.
 */
@Slf4j
@Component
public class OpenAiClient {

    private static final String SYSTEM_PROMPT = """
            너는 한국 병원 처방전·약국 약봉투의 OCR 결과에서 복용할 약 목록을 뽑는 도우미다.
            입력은 "텍스트 @(x,y)" 줄 목록이다. x=왼쪽 기준, y=위쪽 기준 픽셀 좌표.
            y 차이가 15 이내면 같은 행이다.

            [행 해석]
            한 약의 정보는 대개 한 행에 있다. 약 이름 오른쪽, 같은 행에 있는 한 자리 숫자들을
            x가 작은 순서대로 [1회 투약량, 1일 투여횟수, 총 투약일수]에 대응시켜라.
            "1정씩2회5일분" 같은 압축 표기가 있으면 그대로 파싱해라(1정 / 2회 / 5일).

            [drugName]
            - 제형 접미사(정, 캡슐, 캅셀, 시럽, 서방정, 장용정, 액 등)는 이름의 일부다. 절대 떼지 마라.
            - 용량 표기(500mg, 5/50밀리그램)와 괄호 속 성분명은 빼도 된다.
            - "코팅정", "필름코팅정", "경질캡슐" 처럼 제형만 나타내는 단어는 약 이름이 아니다.
            - 성분명만 있는 줄(예: "메트포르민염산염 1000mg", "아세트아미노펜")은 약이 아니다. 제품명만 뽑아라.
            - OCR 오타가 의심돼도 확신 없으면 원문 그대로 둬라(임의로 다른 약명으로 바꾸지 마라).

            [dosagePerTime] 1회 투약량. 반드시 단위 포함 문자열: "1정", "0.5정", "1캡슐", "1포", "5ml".
              숫자만 보이면 약 형태로 단위 추정. 모르면 null.
            [timesPerDay] 1일 투여 횟수 정수(보통 1~4). 모르면 null.
            [totalDays] 총 투약 일수 정수. "교부일로부터 N일", "복약만료일" 같은 건 총 투약일수가 아니다. 모르면 null.

            [제외] 주의사항, 병원·약국명, 의사·약사 이름, 금액, 날짜, 보험코드(8~10자리 숫자), 환자 정보.

            아래 JSON 형식으로만 응답한다:
            {"drugs": [ { "drugName": "...", "dosagePerTime": "...", "timesPerDay": 0, "totalDays": 0 } ]}
            """;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    public OpenAiClient(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return StringUtils.hasText(apiKey);
    }

    /** OCR 필드 → 약 목록 JSON의 "drugs" 배열. 실패 시 예외 전파(상위에서 처리). */
    public JsonNode extractDrugs(List<NaverOcrApiResponse.Field> fields) throws Exception {
        String userContent = toCoordinateText(fields);

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userContent)
                )
        );

        String response;
        try {
            response = webClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(25));
        } catch (WebClientResponseException e) {
            log.error("OpenAI 오류 {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }

        JsonNode root = objectMapper.readTree(response);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        return objectMapper.readTree(content).path("drugs");
    }

    // 각 필드를 "텍스트 @(중심x,중심y)" 한 줄로. y→x 순 정렬해 표 구조를 읽기 쉽게.
    private String toCoordinateText(List<NaverOcrApiResponse.Field> fields) {
        record Line(String text, int x, int y) {}
        return fields.stream()
                .filter(f -> f.inferText() != null && !f.inferText().isBlank())
                .map(f -> {
                    List<NaverOcrApiResponse.Vertex> v = f.boundingPoly() != null ? f.boundingPoly().vertices() : List.of();
                    int cx = v.isEmpty() ? 0 : (int) v.stream().mapToDouble(NaverOcrApiResponse.Vertex::x).average().orElse(0);
                    int cy = v.isEmpty() ? 0 : (int) v.stream().mapToDouble(NaverOcrApiResponse.Vertex::y).average().orElse(0);
                    return new Line(f.inferText().trim(), cx, cy);
                })
                .sorted((a, b) -> a.y() != b.y() ? Integer.compare(a.y(), b.y()) : Integer.compare(a.x(), b.x()))
                .map(l -> l.text() + " @(" + l.x() + "," + l.y() + ")")
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
