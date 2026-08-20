package com.piuda.callcare.domain.drugconflict.event;

/**
 * 알릴 만한 충돌이 분석에서 잡혔음을 알리는 이벤트.
 * <p>
 * 발행 대상은 두 가지뿐이다 — <b>새로 저장된 조합</b>과 <b>등급이 올라간 조합</b>.
 * 이미 알린 조합이 같은 등급으로 다시 매칭되는 것은 새 정보가 아니므로 발행하지 않는다
 * (리포트를 열 때마다 재분석이 도는 구조라 이 구분이 없으면 조회할 때마다 푸시가 나간다).
 *
 * @param drugConflictId 저장·갱신된 충돌 행
 * @param escalated      등급 상승으로 인한 발행이면 true, 신규 탐지면 false
 */
public record DrugConflictDetectedEvent(Long drugConflictId, boolean escalated) {
}
