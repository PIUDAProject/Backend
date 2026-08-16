package com.piuda.callcare.domain.hospital.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.piuda.callcare.domain.hospital.client.dto.HiraHospitalApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class HiraHospitalClient {

    private static final int LOG_BODY_PREVIEW_LENGTH = 1000;
    private static final int MAX_ATTEMPTS = 4;
    private static final long[] RETRY_BACKOFF_MILLIS = {1_000L, 2_000L, 4_000L};

    private final WebClient webClient;
    private final XmlMapper xmlMapper;

    @Value("${hira.service-key}")
    private String serviceKey;

    @Value("${hira.hospital.base-url}")
    private String baseUrl;

    public HiraHospitalClient(@Qualifier("hiraWebClient") WebClient webClient) {
        this.webClient = webClient;
        this.xmlMapper = new XmlMapper();
        this.xmlMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public HiraHospitalApiResponse fetchHospitals(int pageNo, int numOfRows) {
        String requestUrl = buildRequestUrl(pageNo, numOfRows);
        String rawBody = fetchWithRetry(requestUrl, pageNo);

        if (rawBody == null || rawBody.isBlank()) {
            throw new IllegalStateException("HIRA 병원정보서비스 응답이 비어 있습니다.");
        }

        try {
            return xmlMapper.readValue(rawBody, HiraHospitalApiResponse.class);
        } catch (Exception e) {
            log.error("HIRA 병원정보서비스 응답 파싱 실패 (전체 {}자). 앞부분: {}", rawBody.length(), preview(rawBody));
            throw new IllegalStateException("HIRA 병원정보서비스 응답 파싱 실패 - 서비스키/요청 파라미터를 확인하세요.", e);
        }
    }

    private String fetchWithRetry(String requestUrl, int pageNo) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return webClient.get()
                        .uri(URI.create(requestUrl))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(30));
            } catch (WebClientResponseException e) {
                if (isRetryableStatus(e.getStatusCode().value()) && attempt < MAX_ATTEMPTS) {
                    waitBeforeRetry(pageNo, attempt, "HTTP " + e.getStatusCode());
                    continue;
                }

                log.error("HIRA 병원정보서비스 HTTP 오류 - pageNo: {}, attempts: {}, status: {}, body: {}",
                        pageNo, attempt, e.getStatusCode(), preview(e.getResponseBodyAsString()));
                throw new IllegalStateException(
                        "HIRA 병원정보서비스 호출 실패 (pageNo=%d, status=%s, attempts=%d)"
                                .formatted(pageNo, e.getStatusCode(), attempt), e);
            } catch (WebClientRequestException e) {
                if (attempt < MAX_ATTEMPTS) {
                    waitBeforeRetry(pageNo, attempt, e.getClass().getSimpleName());
                    continue;
                }
                throw requestFailure(pageNo, attempt, e);
            } catch (RuntimeException e) {
                if (isTimeout(e) && attempt < MAX_ATTEMPTS) {
                    waitBeforeRetry(pageNo, attempt, "timeout");
                    continue;
                }
                throw requestFailure(pageNo, attempt, e);
            }
        }

        throw new IllegalStateException("HIRA 병원정보서비스 호출 재시도 상태가 올바르지 않습니다.");
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void waitBeforeRetry(int pageNo, int failedAttempt, String cause) {
        long backoffMillis = RETRY_BACKOFF_MILLIS[failedAttempt - 1];
        log.warn("HIRA 병원정보서비스 호출 재시도 - pageNo: {}, nextAttempt: {}/{}, cause: {}, backoffMs: {}",
                pageNo, failedAttempt + 1, MAX_ATTEMPTS, cause, backoffMillis);
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HIRA 병원정보서비스 재시도 대기 중 인터럽트가 발생했습니다. (pageNo=" + pageNo + ")", e);
        }
    }

    private IllegalStateException requestFailure(int pageNo, int attempts, RuntimeException cause) {
        log.error("HIRA 병원정보서비스 네트워크 오류 - pageNo: {}, attempts: {}, cause: {}",
                pageNo, attempts, cause.toString());
        return new IllegalStateException(
                "HIRA 병원정보서비스 호출 실패 (pageNo=%d, attempts=%d, cause=%s)"
                        .formatted(pageNo, attempts, cause.getClass().getSimpleName()), cause);
    }

    private String preview(String body) {
        return body.length() <= LOG_BODY_PREVIEW_LENGTH ? body : body.substring(0, LOG_BODY_PREVIEW_LENGTH) + "...(생략)";
    }

    private String buildRequestUrl(int pageNo, int numOfRows) {
        return baseUrl
                + "?serviceKey=" + serviceKey
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;
    }
}
