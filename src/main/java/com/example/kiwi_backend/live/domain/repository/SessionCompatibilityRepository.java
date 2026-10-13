package com.example.kiwi_backend.live.domain.repository;

import com.example.kiwi_backend.live.domain.entity.SessionCompatibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface SessionCompatibilityRepository extends JpaRepository<SessionCompatibility, Long> {

    List<SessionCompatibility> findAllBySessionId(Long sessionId);

    Optional<SessionCompatibility> findBySessionIdAndMemberAIdAndMemberBId(
            Long sessionId, Long memberAId, Long memberBId);
}
