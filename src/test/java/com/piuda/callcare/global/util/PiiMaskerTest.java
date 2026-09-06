package com.piuda.callcare.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiMasker 단위 테스트")
class PiiMaskerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "환자 900101-1234567 님",
            "주민등록번호: 900101 - 1234567"
    })
    @DisplayName("주민번호(6자리-7자리, 공백 허용)는 마스킹된다")
    void 주민번호_마스킹(String input) {
        String masked = PiiMasker.maskResidentNumber(input);

        assertThat(masked).contains("******-*******");
        assertThat(masked).doesNotContain("1234567");
    }

    @Test
    @DisplayName("하이픈 있는 주민번호만 마스킹하고 나머지 숫자는 건드리지 않는다")
    void 하이픈_있는_주민번호만_마스킹() {
        String input = "교부번호 20260701-00042 / 환자 900101-1234567 / 금액 14,940원";

        String masked = PiiMasker.maskResidentNumber(input);

        assertThat(masked)
                .contains("20260701-00042")   // 교부번호는 뒷자리 8자리라 패턴 불일치 → 유지
                .contains("14,940원")
                .contains("******-*******")
                .doesNotContain("900101-1234567");
    }

    @Test
    @DisplayName("뒷자리 첫 숫자가 1~8이 아니면 마스킹하지 않는다")
    void 잘못된_뒷자리_미마스킹() {
        String input = "코드 123456-9876543";

        assertThat(PiiMasker.maskResidentNumber(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("null은 null을 반환한다")
    void null_처리() {
        assertThat(PiiMasker.maskResidentNumber(null)).isNull();
    }
}
