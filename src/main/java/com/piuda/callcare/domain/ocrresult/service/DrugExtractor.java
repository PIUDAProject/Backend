package com.piuda.callcare.domain.ocrresult.service;

import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;

import java.util.List;

/**
 * OCR 인식 결과({@code fields} — 텍스트 + 좌표)에서 약 목록을 추출한다.
 * <p>
 * 구현체는 실패(외부 API 오류 등) 시 예외를 던지지 않고 빈 리스트를 반환한다.
 * 호출 측은 빈 리스트를 "이 추출기로는 못 뽑음 → 폴백" 신호로 쓴다.
 */
public interface DrugExtractor {

    List<ParsedOcrData> extract(List<NaverOcrApiResponse.Field> fields);
}
