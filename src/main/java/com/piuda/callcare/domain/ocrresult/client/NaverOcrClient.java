package com.piuda.callcare.domain.ocrresult.client;

import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverOcrClient {

    private final WebClient webClient;

    @Value("${naver.ocr.invoke-url}")
    private String invokeUrl;

    @Value("${naver.ocr.secret-key}")
    private String secretKey;

    public List<NaverOcrApiResponse.Field> callOcr(MultipartFile image) {
        try {
            String filename = Objects.requireNonNullElse(image.getOriginalFilename(), "image.jpg");
            String format = extractFormat(filename);
            String messageJson = buildMessageJson(format);

            ByteArrayResource imageResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            NaverOcrApiResponse response = webClient.post()
                    .uri(invokeUrl)
                    .header("X-OCR-SECRET", secretKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData("message", messageJson)
                            .with("file", imageResource))
                    .retrieve()
                    .bodyToMono(NaverOcrApiResponse.class)
                    .block(Duration.ofSeconds(35));

            return extractFields(response);

        } catch (WebClientResponseException e) {
            log.error("Naver OCR API 응답 오류 - status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CallCareException(ErrorCode.OCR_API_ERROR);
        } catch (CallCareException e) {
            throw e;
        } catch (Exception e) {
            log.error("Naver OCR API 호출 실패", e);
            throw new CallCareException(ErrorCode.OCR_API_ERROR);
        }
    }

    private List<NaverOcrApiResponse.Field> extractFields(NaverOcrApiResponse response) {
        if (response == null || response.images() == null || response.images().isEmpty()) {
            throw new CallCareException(ErrorCode.OCR_API_ERROR);
        }
        NaverOcrApiResponse.ImageResult image = response.images().get(0);
        if (!"SUCCESS".equals(image.inferResult()) || image.fields() == null) {
            throw new CallCareException(ErrorCode.OCR_API_ERROR);
        }
        return image.fields();
    }

    //
    private String buildMessageJson(String format) {
        return """
                {"version":"V2","requestId":"%s","timestamp":%d,"lang":"ko","images":[{"format":"%s","name":"image"}]}
                """.formatted(UUID.randomUUID(), System.currentTimeMillis(), format).trim();
    }

    private String extractFormat(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex + 1).toLowerCase() : "jpg";
    }
}
