package com.example.kiwi_backend.live.domain.repository;

import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LiveRoomRepository extends JpaRepository<LiveRoom, Long> {

    List<LiveRoom> findAllByRoundIdOrderByTableNoAsc(Long roundId);

    Optional<LiveRoom> findByRoundIdAndTableNo(Long roundId, int tableNo);

    @Query("""
            select room
            from LiveRoom room
            where room.round.id = :roundId
              and (room.maleMemberId = :memberId or room.femaleMemberId = :memberId)
            """)
    Optional<LiveRoom> findByRoundIdAndMemberId(@Param("roundId") Long roundId,
                                                @Param("memberId") Long memberId);

    /** 이 회원이 세션에서 들어갔던 방들 (주어진 상태의 라운드만, 라운드 순) */
    @Query("""
            select room
            from LiveRoom room
              join fetch room.round round
            where round.sessionId = :sessionId
              and round.status in :statuses
              and (room.maleMemberId = :memberId or room.femaleMemberId = :memberId)
            order by round.roundNumber asc
            """)
    List<LiveRoom> findAllBySessionIdAndMemberIdAndRoundStatusIn(@Param("sessionId") Long sessionId,
                                                                 @Param("memberId") Long memberId,
                                                                 @Param("statuses") Collection<LiveRoundStatus> statuses);

    /** 두 회원이 세션에서 같은 방에 배정된 적이 있는가 (주어진 상태의 라운드만) */
    @Query("""
            select count(room) > 0
            from LiveRoom room
              join room.round round
            where round.sessionId = :sessionId
              and round.status in :statuses
              and ((room.maleMemberId = :memberId and room.femaleMemberId = :otherMemberId)
                or (room.maleMemberId = :otherMemberId and room.femaleMemberId = :memberId))
            """)
    boolean existsMetPair(@Param("sessionId") Long sessionId,
                          @Param("memberId") Long memberId,
                          @Param("otherMemberId") Long otherMemberId,
                          @Param("statuses") Collection<LiveRoundStatus> statuses);
}
