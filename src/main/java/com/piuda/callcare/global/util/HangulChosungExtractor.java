package com.piuda.callcare.global.util;

import java.util.regex.Pattern;

/**
 * 완성형 한글에서 초성만 뽑아낸다. 초성 검색(예: "ㅌㅇㄹㄴ" → "타이레놀")을 위해
 * <b>색인 시점</b>에 별도 필드로 저장할 문자열을 만드는 용도.
 * <p>
 * 검색 시점에는 사용자가 입력한 초성 문자열을 그대로 초성 필드에 매칭하면 되므로
 * 이 유틸이 필요 없다.
 */
public final class HangulChosungExtractor {

    // 유니코드 완성형 한글의 초성 19개 (U+AC00 조합 순서)
    private static final char[] CHOSUNG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private static final char HANGUL_SYLLABLE_BASE = 0xAC00; // '가'
    private static final char HANGUL_SYLLABLE_END = 0xD7A3;   // '힣'
    private static final int JUNGSUNG_COUNT = 21;
    private static final int JONGSUNG_COUNT = 28;

    // 초성 자모(U+3131~U+314E: ㄱ~ㅎ, 겹자음 포함). 모음(U+314F~)은 제외.
    private static final Pattern CHOSUNG_ONLY = Pattern.compile("[\\u3131-\\u314E]+");

    private HangulChosungExtractor() {
    }

    /**
     * 완성형 한글은 초성으로 치환하고, 그 외 문자(자모 단독·숫자·영문·기호)는 그대로 둔다.
     * 예: {@code "타이레놀정500"} → {@code "ㅌㅇㄹㄴㅈ500"}
     */
    public static String extract(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= HANGUL_SYLLABLE_BASE && c <= HANGUL_SYLLABLE_END) {
                int syllableIndex = c - HANGUL_SYLLABLE_BASE;
                int chosungIndex = syllableIndex / (JUNGSUNG_COUNT * JONGSUNG_COUNT);
                sb.append(CHOSUNG[chosungIndex]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 입력 전체가 초성 자모로만 이루어졌는지 (초성 검색 의도인지 판단할 때 사용). */
    public static boolean isChosungOnly(String text) {
        return text != null && !text.isEmpty() && CHOSUNG_ONLY.matcher(text).matches();
    }
}
