package com.piuda.callcare.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    // 알림 센터 목록: 보호자(userId)가 받은 알림 전부를 최신순으로.
    // 어르신이 아니라 보호자 기준인 이유는, 여러 어르신을 돌보는 보호자가 화면을 여러 번 열지 않아도
    // 되어야 하기 때문이다 — 목록은 어르신이 섞인 채로 시간순으로 내려간다.
    // senior를 JOIN FETCH하는 것은 "누구에 대한 알림인지"를 항목마다 표시해야 해서다(N+1 방지).
    // 페이징을 두지 않는 근거는 위 보관 기간 정리가 상한을 잡아 주기 때문이다.
    // id를 2차 정렬키로 둔 이유: 소진 스캔은 약별로 연달아 저장돼 created_at이 같은 행이 나올 수 있고,
    // 그때 순서가 흔들리면 같은 목록을 다시 열 때 항목이 뒤바뀐다.
    @Query("""
            SELECT n FROM Notification n
            JOIN FETCH n.senior s
            WHERE n.user.id = :userId
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    List<Notification> findAllWithSeniorByUserId(@Param("userId") Long userId);

    // 읽음 처리용: id만이 아니라 소유자까지 조건에 넣어 남의 알림을 읽음 처리할 수 없게 한다.
    // 소유자가 다르면 "없음"과 같은 결과(빈 Optional)가 되는데, 이것이 의도다 —
    // 403과 404를 구분하면 남의 알림 id가 존재한다는 사실이 새어 나간다.
    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);
}
