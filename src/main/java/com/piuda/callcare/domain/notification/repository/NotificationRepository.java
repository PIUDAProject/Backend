package com.piuda.callcare.domain.notification.repository;

import java.time.LocalDateTime;

import com.piuda.callcare.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 보관 기간(30일)이 지난 알림 정리. 읽음 여부는 보지 않는다 — 명세의 기준은 "보관 기간"이지
    // "확인 여부"가 아니라서, 안 읽은 알림도 30일이 지나면 함께 지운다.
    // 건별 삭제는 행 수만큼 DELETE가 나가므로 벌크 UPDATE/DELETE로 한 번에 처리한다.
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :threshold")
    int deleteAllCreatedBefore(@Param("threshold") LocalDateTime threshold);
}
