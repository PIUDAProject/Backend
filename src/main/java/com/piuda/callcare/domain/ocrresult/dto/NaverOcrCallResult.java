package com.piuda.callcare.domain.ocrresult.dto;

import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;

import java.util.List;

/**
 * Naver OCR 호출 결과. 파싱에 쓰는 {@code fields}와, 실패 재현·회귀 테스트용으로
 * 저장할 응답 원문 {@code rawResponseJson}을 함께 담는다.
 */
public record NaverOcrCallResult(
        List<NaverOcrApiResponse.Field> fields,
        String rawResponseJson
) {}
