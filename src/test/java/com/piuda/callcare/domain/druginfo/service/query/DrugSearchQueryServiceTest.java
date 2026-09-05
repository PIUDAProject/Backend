package com.piuda.callcare.domain.druginfo.service.query;

import com.piuda.callcare.domain.druginfo.converter.DrugInfoConverter;
import com.piuda.callcare.domain.druginfo.document.DrugDocument;
import com.piuda.callcare.domain.druginfo.dto.response.DrugSearchResponse;
import com.piuda.callcare.domain.druginfo.repository.DrugSearchRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("DrugSearchQueryService 단위 테스트 — ES 장애 시 MySQL 자동 폴백")
class DrugSearchQueryServiceTest {

    @InjectMocks
    private DrugSearchQueryService drugSearchQueryService;

    @Mock private DrugSearchRepository drugSearchRepository;
    @Mock private DrugInfoQueryService drugInfoQueryService;
    @Mock private DrugInfoConverter drugInfoConverter;

    @Test
    @DisplayName("정상 케이스: ES가 정상이면 MySQL 폴백을 타지 않는다")
    void search_uses_es_when_healthy() {
        DrugDocument document = DrugDocument.builder().itemSeq("1").itemName("타이레놀").build();
        DrugSearchResponse response = new DrugSearchResponse("1", "타이레놀", null, null, null, null);
        given(drugSearchRepository.searchByItemName(anyString(), any())).willReturn(List.of(document));
        given(drugInfoConverter.toSearchResponse(document)).willReturn(response);

        List<DrugSearchResponse> result = drugSearchQueryService.search("타이레놀");

        assertThat(result).containsExactly(response);
        then(drugInfoQueryService).should(never()).searchByKeyword(anyString());
    }

    @Test
    @DisplayName("장애 케이스: ES 접근 예외가 나면 MySQL LIKE 검색으로 폴백한다")
    void search_falls_back_to_mysql_when_es_fails() {
        DrugSearchResponse fallbackResponse = new DrugSearchResponse("1", "타이레놀", null, null, null, null);
        given(drugSearchRepository.searchByItemName(anyString(), any()))
                .willThrow(new DataAccessResourceFailureException("ES 연결 실패"));
        given(drugInfoQueryService.searchByKeyword("타이레놀")).willReturn(List.of(fallbackResponse));

        List<DrugSearchResponse> result = drugSearchQueryService.search("타이레놀");

        assertThat(result).containsExactly(fallbackResponse);
        then(drugInfoQueryService).should().searchByKeyword("타이레놀");
    }

    @Test
    @DisplayName("예외 케이스: 빈 키워드는 폴백 없이 즉시 INVALID_PARAMETER")
    void search_throws_on_blank_keyword_without_fallback() {
        assertThatThrownBy(() -> drugSearchQueryService.search("  "))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PARAMETER);

        then(drugSearchRepository).should(never()).searchByItemName(anyString(), any());
        then(drugInfoQueryService).should(never()).searchByKeyword(anyString());
    }

    @Test
    @DisplayName("경계: 검색어 앞뒤 공백은 잘라서 폴백 서비스에도 그대로 전달된다")
    void search_trims_keyword_before_fallback() {
        given(drugSearchRepository.searchByItemName(anyString(), any()))
                .willThrow(new DataAccessResourceFailureException("ES 연결 실패"));
        given(drugInfoQueryService.searchByKeyword("타이레놀")).willReturn(List.of());

        drugSearchQueryService.search("  타이레놀  ");

        then(drugSearchRepository).should().searchByItemName("타이레놀", PageRequest.of(0, 20));
        then(drugInfoQueryService).should().searchByKeyword("타이레놀");
    }
}
