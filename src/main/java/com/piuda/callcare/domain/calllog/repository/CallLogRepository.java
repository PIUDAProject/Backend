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

    // 원래 통화 기록을 찾기 위함
    Optional<CallLog> findByMessageId(String messageId);

    @Query("""
            SELECT cl FROM CallLog cl
            JOIN FETCH cl.senior s
            JOIN FETCH s.user u
            WHERE cl.status IN :statuses
              AND cl.isNotified = false
              AND cl.calledAt <= :calledBefore
            """)
    List<CallLog> findGuardianNotificationTargets(
            @Param("statuses") Collection<CallStatus> statuses,
            @Param("calledBefore") LocalDateTime calledBefore
    );
}
