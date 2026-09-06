package com.piuda.callcare.domain.ocrresult.service.command;

import com.piuda.callcare.domain.ocrresult.client.NaverOcrClient;
import com.piuda.callcare.domain.ocrresult.converter.OcrResultConverter;
import com.piuda.callcare.domain.ocrresult.dto.NaverOcrCallResult;
import com.piuda.callcare.domain.ocrresult.dto.OcrParseResult;
import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.entity.OcrResult;
import com.piuda.callcare.domain.ocrresult.enums.OcrType;
import com.piuda.callcare.domain.ocrresult.repository.OcrResultRepository;
import com.piuda.callcare.domain.ocrresult.service.DrugExtractor;
import com.piuda.callcare.domain.ocrresult.service.OcrParser;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("OcrCommandService 단위 테스트 — 하이브리드 라우팅 (처방전=파서 / 그 외=LLM)")
class OcrCommandServiceTest {

    @InjectMocks private OcrCommandService ocrCommandService;

    @Mock private NaverOcrClient naverOcrClient;
    @Mock private OcrParser ocrParser;
    @Mock private DrugExtractor drugExtractor;
    @Mock private OcrResultRepository ocrResultRepository;
    @Mock private OcrResultConverter ocrResultConverter;
    @Mock private SeniorRepository seniorRepository;

    private static final ParsedOcrData PARSER_DRUG = new ParsedOcrData("파서약", "1정", 3, 3);
    private static final ParsedOcrData LLM_DRUG = new ParsedOcrData("LLM약", "2정", 2, 5);

    private void commonStubs() {
        given(seniorRepository.findByIdAndUser_Id(anyLong(), anyLong()))
                .willReturn(Optional.of(Senior.builder().build()));
        given(naverOcrClient.callOcr(any()))
                .willReturn(new NaverOcrCallResult(List.of(), "{}"));
        given(ocrParser.parse(any(), any()))
                .willReturn(new OcrParseResult("raw", List.of(PARSER_DRUG), null));
        given(ocrResultRepository.save(any())).willAnswer(i -> i.getArgument(0));
        // ocrResultConverter.toResponse는 mock 기본값(null) 그대로 사용
    }

    @Test
    @DisplayName("병원 처방전이면 LLM을 부르지 않고 파서 결과를 쓴다")
    void 처방전은_파서_사용() {
        // Given
        commonStubs();
        given(ocrParser.isPrescription(any())).willReturn(true);

        // When
        ocrCommandService.processOcr(1L, 1L, image(), OcrType.PRESCRIPTION);

        // Then
        assertThat(savedFirstDrugName()).isEqualTo("파서약");
        then(drugExtractor).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("처방전이 아니면(약봉투 등) LLM 결과를 쓴다")
    void 약봉투는_LLM_사용() {
        // Given
        commonStubs();
        given(ocrParser.isPrescription(any())).willReturn(false);
        given(drugExtractor.extract(any())).willReturn(List.of(LLM_DRUG));

        // When
        ocrCommandService.processOcr(1L, 1L, image(), OcrType.DRUG_BAG);

        // Then
        assertThat(savedFirstDrugName()).isEqualTo("LLM약");
    }

    @Test
    @DisplayName("처방전이 아니고 LLM이 빈 리스트면 파서 결과로 폴백한다")
    void LLM_실패시_파서_폴백() {
        // Given
        commonStubs();
        given(ocrParser.isPrescription(any())).willReturn(false);
        given(drugExtractor.extract(any())).willReturn(List.of());

        // When
        ocrCommandService.processOcr(1L, 1L, image(), OcrType.DRUG_BAG);

        // Then
        assertThat(savedFirstDrugName()).isEqualTo("파서약");
    }

    private String savedFirstDrugName() {
        ArgumentCaptor<OcrResult> captor = ArgumentCaptor.forClass(OcrResult.class);
        org.mockito.Mockito.verify(ocrResultRepository).save(captor.capture());
        return captor.getValue().getParsedDrugName();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "p.jpg", "image/jpeg", new byte[]{1});
    }
}
