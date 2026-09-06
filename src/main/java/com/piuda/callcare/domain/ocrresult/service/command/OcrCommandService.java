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
import com.piuda.callcare.domain.ocrresult.service.DrugExtractor;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrCommandService {

    private final NaverOcrClient naverOcrClient;
    private final OcrParser ocrParser;
    private final DrugExtractor drugExtractor;
    private final OcrResultRepository ocrResultRepository;
    private final OcrResultConverter ocrResultConverter;
    private final SeniorRepository seniorRepository;

    public OcrResultResponse processOcr(Long userId, Long seniorId, MultipartFile image, OcrType ocrType) {
        Senior senior = seniorRepository.findByIdAndUser_Id(seniorId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));

        // OCR 호출 → fields(텍스트 + 좌표 블록 목록) + 응답 원문 반환
        NaverOcrCallResult ocrCallResult = naverOcrClient.callOcr(image);

        // rawText 조립 + 처방일 + 파서 약 추출
        OcrParseResult parseResult = ocrParser.parse(ocrCallResult.fields(), ocrType);

        // 하이브리드 라우팅 (실측 기반):
        //  - 보험코드 줄 처방전: 저화질에서 파서(좌표)가 LLM보다 정확 → 파서 결과 사용
        //  - 약봉투/영수증/그 외: LLM 추출, 실패 시 파서 폴백
        List<ParsedOcrData> parsedDrugs;
        String method;
        if (ocrParser.hasCodedPrescriptionLines(ocrCallResult.fields()) && !parseResult.parsedDrugs().isEmpty()) {
            parsedDrugs = parseResult.parsedDrugs();
            method = "파서(처방전)";
        } else {
            parsedDrugs = drugExtractor.extract(ocrCallResult.fields());
            if (!parsedDrugs.isEmpty()) {
                method = "LLM";
            } else {
                parsedDrugs = parseResult.parsedDrugs();
                method = "파서(폴백)";
            }
        }

        // OcrResult DB 저장: rawText + 응답 원문 + 첫 번째 약. 주민번호는 저장 전 마스킹
        OcrResult ocrResult = OcrResult.builder()
                .senior(senior)
                .ocrType(ocrType)
                .rawText(PiiMasker.maskResidentNumber(parseResult.rawText()))
                .rawResponse(PiiMasker.maskResidentNumber(ocrCallResult.rawResponseJson()))
                .build();

        ParsedOcrData first = parsedDrugs.isEmpty()
                ? new ParsedOcrData(null, null, null, null)
                : parsedDrugs.get(0);

        ocrResult.saveParsedData(first.drugName(), first.dosagePerTime(), first.timesPerDay(), first.totalDays());
        ocrResult.markAsProcessed();

        OcrResult saved = ocrResultRepository.save(ocrResult);
        log.info("OCR 처리 완료 - ocrResultId: {}, ocrType: {}, 추출: {}, 약 수: {}",
                saved.getId(), ocrType, method, parsedDrugs.size());

        return ocrResultConverter.toResponse(saved, parsedDrugs, parseResult.prescriptionDate());
    }
}
