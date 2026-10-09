package com.example.kiwi_backend.live.domain.repository;

import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
