package com.example.kiwi_backend.session.domain.repository;

import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DatingSessionRepository extends JpaRepository<DatingSession, Long> {

    /**
     * 세션 행을 잠근다. 같은 세션에 대한 신청·취소·진행 판단이 한 번에 하나씩 처리된다.
     * "마지막 한 자리"에 두 명이 동시에 신청해도 한 명만 들어간다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DatingSession s where s.id = :id")
    Optional<DatingSession> findByIdForUpdate(@Param("id") Long id);

    /** 진행 판단할 차례가 된 세션 (모집 중이고 시작 시각이 기준 시각 이전) */
    @Query("select s.id from DatingSession s where s.status = :status and s.startTime <= :threshold")
    List<Long> findIdsByStatusAndStartTimeAtOrBefore(@Param("status") SessionStatus status,
                                                     @Param("threshold") Instant threshold);

    /** 목록 화면용: 아직 시작 전인 세션 */
    List<DatingSession> findAllByStatusInAndStartTimeAfterOrderByStartTimeAsc(Collection<SessionStatus> statuses,
                                                                             Instant now);
}
