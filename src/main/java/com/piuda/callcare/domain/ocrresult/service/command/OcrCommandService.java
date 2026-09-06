package com.piuda.callcare.domain.ocrresult.service.command;

import com.piuda.callcare.domain.ocrresult.client.NaverOcrClient;
import com.piuda.callcare.domain.ocrresult.converter.OcrResultConverter;
import com.piuda.callcare.domain.ocrresult.dto.NaverOcrCallResult;
import com.piuda.callcare.domain.ocrresult.dto.OcrParseResult;
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
import com.piuda.callcare.global.util.PiiMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

        // OCR 호출 → fields(텍스트 + 좌표 블록 목록) + 응답 원문 반환
        NaverOcrCallResult ocrCallResult = naverOcrClient.callOcr(image);

        // 파싱: rawText 조립 + 약 정보 추출 (표 처방전이면 여러 약)
        OcrParseResult parseResult = ocrParser.parse(ocrCallResult.fields(), ocrType);

        // OcrResult DB 저장: rawText + 응답 원문 + 첫 번째 약 파싱 결과. 주민번호는 저장 전 마스킹
        OcrResult ocrResult = OcrResult.builder()
                .senior(senior)
                .ocrType(ocrType)
                .rawText(PiiMasker.maskResidentNumber(parseResult.rawText()))
                .rawResponse(PiiMasker.maskResidentNumber(ocrCallResult.rawResponseJson()))
                .build();

        ParsedOcrData first = parseResult.parsedDrugs().isEmpty()
                ? new ParsedOcrData(null, null, null, null)
                : parseResult.parsedDrugs().get(0);

        ocrResult.saveParsedData(first.drugName(), first.dosagePerTime(), first.timesPerDay(), first.totalDays());
        ocrResult.markAsProcessed();

        OcrResult saved = ocrResultRepository.save(ocrResult);
        log.info("OCR 처리 완료 - ocrResultId: {}, ocrType: {}, 파싱된 약 수: {}",
                saved.getId(), ocrType, parseResult.parsedDrugs().size());

        return ocrResultConverter.toResponse(saved, parseResult.parsedDrugs(), parseResult.prescriptionDate());
    }
}
