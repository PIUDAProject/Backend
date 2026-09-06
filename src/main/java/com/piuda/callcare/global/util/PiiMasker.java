package com.piuda.callcare.global.util;

import java.util.regex.Pattern;

/**
 * OCR 결과를 저장하거나 외부(LLM)로 보내기 전 민감정보를 가리는 유틸.
 * <p>
 * 형식이 고정된 항목(주민등록번호, 전화번호)만 정규식으로 잡는다.
 * 환자 이름·생년월일은 형식이 없어 자동 식별이 어렵고, 저장 허용 범위라 건드리지 않는다.
 */
public final class PiiMasker {

    private static final Pattern RESIDENT_NUMBER =
            Pattern.compile("(?<!\\d)\\d{6}\\s*-?\\s*[1-8]\\d{6}(?!\\d)");
    private static final String RESIDENT_MASK = "******-*******";

    // 010-1234-5678 / 01012345678 / 02-123-4567 등
    private static final Pattern PHONE =
            Pattern.compile("(?<!\\d)(01[016789]|0[2-6]\\d?)[-\\s]?\\d{3,4}[-\\s]?\\d{4}(?!\\d)");
    private static final String PHONE_MASK = "***-****-****";

    private PiiMasker() {
    }

    /** 주민등록번호를 {@code ******-*******} 로 치환한다. */
    public static String maskResidentNumber(String text) {
        if (text == null) {
            return null;
        }
        return RESIDENT_NUMBER.matcher(text).replaceAll(RESIDENT_MASK);
    }

    /** 주민번호 + 전화번호를 마스킹한다. 외부(LLM)로 텍스트를 보내기 전에 쓴다. */
    public static String maskContact(String text) {
        if (text == null) {
            return null;
        }
        String s = RESIDENT_NUMBER.matcher(text).replaceAll(RESIDENT_MASK);
        return PHONE.matcher(s).replaceAll(PHONE_MASK);
    }
}
