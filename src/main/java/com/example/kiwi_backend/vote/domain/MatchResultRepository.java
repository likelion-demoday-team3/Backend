package com.example.kiwi_backend.vote.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
    List<MatchResult> findAllBySessionId(Long sessionId);
    List<MatchResult> findAllBySessionIdIn(Collection<Long> sessionIds);
    long countBySessionId(Long sessionId);
}
