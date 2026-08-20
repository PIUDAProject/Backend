package com.piuda.callcare.domain.medication.event;

/**
 * 어르신의 약 구성이 바뀌었음을 알리는 이벤트(등록·수정).
 * <p>
 * 충돌은 시간이 지나서 생기는 사건이 아니라 <b>약 구성이 바뀌는 순간</b>에 생기는 사건이므로,
 * 그 시점에 재분석을 돌려 새 충돌을 감지한다. 리포트 진입 시에만 분석하면 보호자가 화면을
 * 열지 않는 한 위험한 조합이 있어도 푸시가 나가지 않는다.
 * <p>
 * <b>직접 호출이 아니라 이벤트인 이유</b>는 순환 참조 때문이다 — drugconflict 도메인은 이미
 * medication을 참조하므로, medication이 drugconflict를 직접 부르면 양방향이 된다.
 *
 * @param seniorId 약 구성이 바뀐 어르신
 */
public record MedicationChangedEvent(Long seniorId) {
}
