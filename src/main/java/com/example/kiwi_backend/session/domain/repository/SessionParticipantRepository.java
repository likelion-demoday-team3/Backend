package com.example.kiwi_backend.session.domain.repository;

import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    Optional<SessionParticipant> findBySessionIdAndMemberId(Long sessionId, Long memberId);

    long countBySessionIdAndGenderAndStatus(Long sessionId, Gender gender, ParticipantStatus status);

    List<SessionParticipant> findAllBySessionIdAndStatus(Long sessionId, ParticipantStatus status);

    List<SessionParticipant> findAllByMemberIdOrderByAppliedAtDesc(Long memberId);

    /** 별칭 중복을 피하기 위해 세션에서 이미 쓰인 별칭 (취소한 사람 것도 포함, 유니크 제약 때문) */
    @Query("select p.alias from SessionParticipant p where p.sessionId = :sessionId")
    Set<String> findAliasesBySessionId(@Param("sessionId") Long sessionId);

    /** 목록 화면에서 세션마다 따로 세지 않고 한 번에 성별 인원을 센다 */
    @Query("""
            select p.sessionId as sessionId, p.gender as gender, count(p) as count
            from SessionParticipant p
            where p.sessionId in :sessionIds and p.status = :status
            group by p.sessionId, p.gender
            """)
    List<GenderCount> countByGender(@Param("sessionIds") Collection<Long> sessionIds,
                                    @Param("status") ParticipantStatus status);
}
