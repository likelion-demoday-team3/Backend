package com.example.kiwi_backend.interaction.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PostSessionWindowRepository extends JpaRepository<PostSessionWindow, Long> {
    Optional<PostSessionWindow> findBySessionIdAndMemberId(Long sessionId, Long memberId);
}
