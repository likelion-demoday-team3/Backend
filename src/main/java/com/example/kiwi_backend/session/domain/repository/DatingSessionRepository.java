package com.example.kiwi_backend.session.domain.repository;

import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.LiveStatus;
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
     * 세션 행을 잠근다. 같은 세션에 대한 신청·취소·진행 판단·로비 마감이 한 번에 하나씩 처리된다.
     * 잠금 순서는 항상 세션 → 지갑.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DatingSession s where s.id = :id")
    Optional<DatingSession> findByIdForUpdate(@Param("id") Long id);

    /**
     * 상태가 status이고 시작 시각이 threshold 이전인 세션.
     *   30분 전 판단: (RECRUITING, now + 30분)
     *   로비 마감은 findIdsDueForLobbyClose에서 별도로 조회한다.
     */
    @Query("select s.id from DatingSession s where s.status = :status and s.startTime <= :threshold")
    List<Long> findIdsByStatusAndStartTimeAtOrBefore(@Param("status") SessionStatus status,
                                                     @Param("threshold") Instant threshold);

    /** 테스트 세션과 일반 세션의 서로 다른 로비 마감 시각을 적용한다. */
    @Query("""
        select s.id from DatingSession s
        where s.status = :status
          and (s.startTime <= :regularThreshold
               or (s.callTest = true and s.startTime <= :testThreshold))
        """)
    List<Long> findIdsDueForLobbyClose(@Param("status") SessionStatus status,
                                       @Param("regularThreshold") Instant regularThreshold,
                                       @Param("testThreshold") Instant testThreshold);

    /** 1라운드를 시작할 차례인 세션: 진행 중이고, 아직 라운드 시작 전(WAITING)이고, 1라운드 시각이 지남 */
    @Query("""
        select s.id from DatingSession s
        where s.status = :status
          and s.liveStatus = :waitingStatus
          and s.phaseEndsAt <= :now
        """)
    List<Long> findIdsDueForFirstRound(@Param("status") SessionStatus status,
                                       @Param("waitingStatus") LiveStatus waitingStatus,
                                       @Param("now") Instant now);

    /** 목록 화면용: 아직 시작 전인 세션 */
    List<DatingSession> findAllByStatusInAndStartTimeAfterOrderByStartTimeAsc(Collection<SessionStatus> statuses,
                                                                             Instant now);
}
