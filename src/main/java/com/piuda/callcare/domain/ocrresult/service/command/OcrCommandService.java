package com.piuda.callcare.domain.ocrresult.service.command;

import com.piuda.callcare.domain.ocrresult.client.NaverOcrClient;
import com.piuda.callcare.domain.ocrresult.converter.OcrResultConverter;
import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.dto.response.OcrResultResponse;
import com.piuda.callcare.domain.ocrresult.entity.OcrResult;
import com.piuda.callcare.domain.ocrresult.enums.OcrType;
import com.piuda.callcare.domain.ocrresult.repository.OcrResultRepository;
import com.piuda.callcare.domain.ocrresult.service.OcrParser;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Transactional
@Service
@RequiredArgsConstructor
public class OcrCommandService {

    private final NaverOcrClient naverOcrClient;
    private final OcrParser ocrParser;
    private final OcrResultRepository ocrResultRepository;
    private final OcrResultConverter ocrResultConverter;
    private final SeniorRepository seniorRepository;

    public OcrResultResponse processOcr(Long userId, Long seniorId, MultipartFile image, OcrType ocrType) {
        Senior senior = seniorRepository.findByIdAndUser_Id(seniorId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));

        String rawText = naverOcrClient.callOcr(image);
        ParsedOcrData parsed = ocrParser.parse(rawText, ocrType);

        OcrResult ocrResult = OcrResult.builder()
                .senior(senior)
                .ocrType(ocrType)
                .rawText(rawText)
                .build();
        ocrResult.saveParsedData(
                parsed.drugName(),
                parsed.dosagePerTime(),
                parsed.timesPerDay(),
                parsed.totalDays()
        );
        ocrResult.markAsProcessed();

        OcrResult saved = ocrResultRepository.save(ocrResult);
        log.info("OCR 처리 완료 - ocrResultId: {}, ocrType: {}", saved.getId(), ocrType);

        return ocrResultConverter.toResponse(saved);
    }
}
