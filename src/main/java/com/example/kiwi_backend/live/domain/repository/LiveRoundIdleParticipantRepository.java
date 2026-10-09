package com.example.kiwi_backend.live.domain.repository;

import com.example.kiwi_backend.live.domain.entity.LiveRoundIdleParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveRoundIdleParticipantRepository extends JpaRepository<LiveRoundIdleParticipant, Long> {

    List<LiveRoundIdleParticipant> findAllByRoundIdOrderByMemberIdAsc(Long roundId);

    boolean existsByRoundIdAndMemberId(Long roundId, Long memberId);
}
