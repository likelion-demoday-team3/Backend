package com.example.kiwi_backend.notification.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    Slice<Notification> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

    long countByMemberIdAndReadAtIsNull(Long memberId);

    /** 남의 알림이면 없는 것처럼 */
    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.memberId = :memberId and n.readAt is null")
    int markAllRead(@Param("memberId") Long memberId, @Param("now") Instant now);
}
