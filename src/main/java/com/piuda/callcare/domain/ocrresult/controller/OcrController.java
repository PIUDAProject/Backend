package com.piuda.callcare.domain.ocrresult.controller;

import com.piuda.callcare.domain.ocrresult.dto.response.OcrResultResponse;
import com.piuda.callcare.domain.ocrresult.enums.OcrType;
import com.piuda.callcare.domain.ocrresult.service.command.OcrCommandService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "OCR", description = "OCR 분석 API")
@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrCommandService ocrCommandService;

    @Operation(summary = "처방전 OCR 분석", description = "처방전 이미지를 분석해 약 이름, 복용 횟수, 용량, 복용 일수를 추출합니다.")
    @PostMapping(value = "/prescription", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OcrResultResponse>> processPrescription(
        @AuthenticationPrincipal Long userId,
        @RequestParam Long seniorId,
        @RequestPart("image") MultipartFile image
    ) {
        return ResponseUtils.ok(ocrCommandService.processOcr(userId, seniorId, image, OcrType.PRESCRIPTION));
    }

    @Operation(summary = "약봉투/약곽 OCR 분석", description = "약봉투 또는 약곽 이미지를 분석해 약 정보를 추출합니다.")
    @PostMapping(value = "/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OcrResultResponse>> processPackage(
        @AuthenticationPrincipal Long userId,
        @RequestParam Long seniorId,
        @RequestPart("image") MultipartFile image,
        @RequestParam(defaultValue = "DRUG_BAG") OcrType ocrType
    ) {
        return ResponseUtils.ok(ocrCommandService.processOcr(userId, seniorId, image, ocrType));
    }
}