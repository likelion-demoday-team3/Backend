package com.example.kiwi_backend.live.domain.repository;

import com.example.kiwi_backend.live.domain.entity.LiveRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveRoundRepository extends JpaRepository<LiveRound, Long> {

    boolean existsBySessionId(Long sessionId);

    List<LiveRound> findAllBySessionIdOrderByRoundNumberAsc(Long sessionId);

    Optional<LiveRound> findBySessionIdAndRoundNumber(Long sessionId, int roundNumber);
}
