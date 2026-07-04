package com.piuda.callcare.global.util;

/**
 * 로그에 전화번호를 남길 때 개인정보 노출을 막기 위한 마스킹 유틸.
 */
public final class PhoneMaskUtil {

    private PhoneMaskUtil() {
    }

    public static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "****";
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
