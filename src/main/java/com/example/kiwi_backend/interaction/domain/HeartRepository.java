package com.example.kiwi_backend.interaction.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HeartRepository extends JpaRepository<Heart, Long> {
    Optional<Heart> findBySessionIdAndSenderIdAndReceiverId(Long sessionId, Long senderId, Long receiverId);
    List<Heart> findAllBySessionIdAndSenderId(Long sessionId, Long senderId);
    long countBySessionIdAndReceiverId(Long sessionId, Long receiverId);
}
