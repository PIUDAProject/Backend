package com.piuda.callcare.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HangulChosungExtractor 단위 테스트")
class HangulChosungExtractorTest {

    @ParameterizedTest
    @CsvSource({
            "타이레놀,          ㅌㅇㄹㄴ",
            "타이레놀정500,     ㅌㅇㄹㄴㅈ500",
            "겔포스현탁액,       ㄱㅍㅅㅎㅌㅇ",
            "글루타티온(환원형),  ㄱㄹㅌㅌㅇ(ㅎㅇㅎ)",
            "ABC정,             ABCㅈ"
    })
    @DisplayName("완성형 한글은 초성으로, 그 외 문자는 그대로")
    void extract(String input, String expected) {
        assertThat(HangulChosungExtractor.extract(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("겹받침 음절도 초성만 추출한다")
    void extract_with_jongsung() {
        assertThat(HangulChosungExtractor.extract("닭")).isEqualTo("ㄷ");
        assertThat(HangulChosungExtractor.extract("값")).isEqualTo("ㄱ");
    }

    @Test
    @DisplayName("null/빈 문자열은 그대로 반환")
    void extract_null_or_empty() {
        assertThat(HangulChosungExtractor.extract(null)).isNull();
        assertThat(HangulChosungExtractor.extract("")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ㅌㅇㄹㄴ", "ㄱ", "ㄲㅄ"})
    @DisplayName("초성 자모로만 이루어진 입력은 초성 검색 의도로 판단")
    void isChosungOnly_true(String input) {
        assertThat(HangulChosungExtractor.isChosungOnly(input)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"타이레", "ㅏㅣ", "타ㅇ", "abc", "ㅌㅇ ㄹㄴ", ""})
    @DisplayName("완성형 한글·모음·공백·영문이 섞이면 초성 검색 의도가 아님")
    void isChosungOnly_false(String input) {
        assertThat(HangulChosungExtractor.isChosungOnly(input)).isFalse();
    }

    @Test
    @DisplayName("null은 초성 검색 의도가 아님")
    void isChosungOnly_null() {
        assertThat(HangulChosungExtractor.isChosungOnly(null)).isFalse();
    }
}
