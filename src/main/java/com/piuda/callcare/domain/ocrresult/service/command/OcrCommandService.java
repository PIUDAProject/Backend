package com.piuda.callcare.domain.ocrresult.service.command;

import com.piuda.callcare.domain.ocrresult.client.NaverOcrClient;
import com.piuda.callcare.domain.ocrresult.converter.OcrResultConverter;
import com.piuda.callcare.domain.ocrresult.dto.OcrParseResult;
import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
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

        // OCR 호출 → fields(텍스트 + 좌표 블록 목록) 반환
        List<NaverOcrApiResponse.Field> fields = naverOcrClient.callOcr(image);

        // 파싱: rawText 조립 + 약 정보 추출 (표 처방전이면 여러 약)
        OcrParseResult parseResult = ocrParser.parse(fields, ocrType);

        // OcrResult DB 저장: rawText + 첫 번째 약 파싱 결과
        OcrResult ocrResult = OcrResult.builder()
                .senior(senior)
                .ocrType(ocrType)
                .rawText(parseResult.rawText())
                .build();

        ParsedOcrData first = parseResult.parsedDrugs().isEmpty()
                ? new ParsedOcrData(null, null, null, null)
                : parseResult.parsedDrugs().get(0);

        ocrResult.saveParsedData(first.drugName(), first.dosagePerTime(), first.timesPerDay(), first.totalDays());
        ocrResult.markAsProcessed();

        OcrResult saved = ocrResultRepository.save(ocrResult);
        log.info("OCR 처리 완료 - ocrResultId: {}, ocrType: {}, 파싱된 약 수: {}",
                saved.getId(), ocrType, parseResult.parsedDrugs().size());

        return ocrResultConverter.toResponse(saved, parseResult.parsedDrugs());
    }
}
