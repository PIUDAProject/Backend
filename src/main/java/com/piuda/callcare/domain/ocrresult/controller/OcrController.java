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

    @Operation(summary = "OCR 분석", description = "이미지를 분석해 약 정보를 추출합니다. ocrType: PRESCRIPTION(처방전), DRUG_BAG(약봉투), DRUG_BOX(약곽)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OcrResultResponse>> processOcr(
        @AuthenticationPrincipal Long userId,
        @RequestParam Long seniorId,
        @RequestPart("image") MultipartFile image,
        @RequestParam(defaultValue = "DRUG_BAG") OcrType ocrType
    ) {
        // TODO: 인증 필터 도입 후 제거. 현재는 로컬 테스트를 위해 userId 임시 하드코딩
        Long testUserId = (userId != null) ? userId : 1L;
        return ResponseUtils.ok(ocrCommandService.processOcr(testUserId, seniorId, image, ocrType));
    }
}