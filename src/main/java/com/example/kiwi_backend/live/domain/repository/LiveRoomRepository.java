package com.example.kiwi_backend.live.domain.repository;

import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveRoomRepository extends JpaRepository<LiveRoom, Long> {

    List<LiveRoom> findAllByRoundIdOrderByTableNoAsc(Long roundId);

    Optional<LiveRoom> findByRoundIdAndTableNo(Long roundId, int tableNo);
}
