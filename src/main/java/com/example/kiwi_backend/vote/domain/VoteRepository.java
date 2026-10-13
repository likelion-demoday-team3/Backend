package com.example.kiwi_backend.vote.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface VoteRepository extends JpaRepository<Vote, Long> {
    List<Vote> findAllBySessionIdAndVoterIdOrderByVoteRankAsc(Long sessionId, Long voterId);
    List<Vote> findAllBySessionIdAndVoteRank(Long sessionId, int voteRank);
    List<Vote> findAllBySessionIdOrderByVoterIdAscVoteRankAsc(Long sessionId);
}
