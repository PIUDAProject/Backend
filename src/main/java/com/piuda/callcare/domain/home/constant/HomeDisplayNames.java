package com.piuda.callcare.domain.home.constant;

// 홈 화면 표시 문구 — 홈 카드와 복약 부족 카드가 같은 값을 써야 해서 한 곳에 둔다.
// 이 값은 화면 표시 전용이다. 약물노트 그룹 조회 키로 넘기면 hospital_name = '병원 정보 없음' 비교가 되어
// 0건이 나오므로, 딥링크에는 반드시 원본 hospitalName(null 가능)을 쓴다.
public final class HomeDisplayNames {

    public static final String NO_HOSPITAL_NAME = "병원 정보 없음";

    private HomeDisplayNames() {
    }
}
