package com.piuda.callcare.global.util;

import java.util.regex.Pattern;

/**
 * OCR 결과를 저장하기 전 민감정보를 가리는 유틸.
 * <p>
 * 주민등록번호만 대상으로 한다. 형식이 고정("6자리-7자리", 뒷자리 첫 숫자 1~8)이라
 * 정규식으로 안전하게 잡힌다. 환자 이름·생년월일은 형식이 없어 자동 식별이 어렵고,
 * 저장 허용 범위라 건드리지 않는다.
 */
public final class PiiMasker {

    private static final Pattern RESIDENT_NUMBER = Pattern.compile("\\d{6}\\s*-\\s*[1-8]\\d{6}");
    private static final String MASK = "******-*******";

    private PiiMasker() {
    }

    public static String maskResidentNumber(String text) {
        if (text == null) {
            return null;
        }
        return RESIDENT_NUMBER.matcher(text).replaceAll(MASK);
    }
}
