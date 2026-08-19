package com.piuda.callcare.domain.calllog.repository;

import com.piuda.callcare.domain.calllog.entity.CallLog;
import com.piuda.callcare.domain.calllog.enums.CallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CallLogRepository extends JpaRepository<CallLog, Long> {

    @Query("""
            SELECT cl FROM CallLog cl
            JOIN FETCH cl.senior s
            JOIN FETCH s.user u
            WHERE cl.messageId = :messageId
            """)
    Optional<CallLog> findByMessageId(@Param("messageId") String messageId);

    // 재발신 대상: 아직 재발신하지 않았고(retryCount=0), 마지막 발신 후 재시도 간격이 지났으며, 수신되지 않은 콜.
    // PENDING을 포함하는 이유는 통화 결과 웹훅이 영영 오지 않을 수 있기 때문 — 결과 미수신도 미수신으로 본다.
    // 기존 행의 retry_count가 NULL일 수 있어 COALESCE로 방어한다.
    // calledAt에 하한을 두는 것이 핵심이다 — 이 조건이 없으면 재발신이 도입되기 전에 쌓인
    // 과거의 미수신 행(전부 retryCount=0)이 전부 대상이 되어, 배포 직후 지난 날짜의 복약 전화가 한꺼번에 나간다.
    // 날짜(callDate)가 아니라 발신 시각으로 자르는 이유는 자정 경계 때문이다 — 23:50에 건 콜의
    // 재발신 시점은 다음 날 00:00이라, callDate를 오늘로 못 박으면 그 행이 영영 재발신되지 않고
    // 통보 대상(retryCount>=1)에도 들지 못해 미수신이 통째로 유실된다.
    @Query("""
            SELECT cl FROM CallLog cl
            JOIN FETCH cl.senior s
            WHERE cl.status IN :statuses
              AND COALESCE(cl.retryCount, 0) = 0
              AND cl.calledAt BETWEEN :calledAfter AND :calledBefore
            """)
    List<CallLog> findRetryTargets(
            @Param("statuses") Collection<CallStatus> statuses,
            @Param("calledAfter") LocalDateTime calledAfter,
            @Param("calledBefore") LocalDateTime calledBefore
    );

    // 보호자·부모님 통보 대상: 재발신까지 마쳤는데도(retryCount>=1) 수신되지 않은 콜.
    // retryCount 조건이 없으면 1차 미수신에서 바로 통보가 나가 재시도가 무의미해진다.
    @Query("""
            SELECT cl FROM CallLog cl
            JOIN FETCH cl.senior s
            JOIN FETCH s.user u
            WHERE cl.status IN :statuses
              AND COALESCE(cl.retryCount, 0) >= 1
              AND cl.isNotified = false
              AND cl.calledAt <= :calledBefore
            """)
    List<CallLog> findGuardianNotificationTargets(
            @Param("statuses") Collection<CallStatus> statuses,
            @Param("calledBefore") LocalDateTime calledBefore
    );
}
