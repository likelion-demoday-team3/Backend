package com.example.kiwi_backend.vote.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity
@Table(name = "vote", uniqueConstraints = {
    @UniqueConstraint(name = "uk_vote_rank", columnNames = {"session_id", "voter_id", "vote_rank"}),
    @UniqueConstraint(name = "uk_vote_target", columnNames = {"session_id", "voter_id", "target_member_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long sessionId;
    @Column(nullable = false, updatable = false) private Long voterId;
    @Column(nullable = false, updatable = false) private Long targetMemberId;
    @Column(nullable = false, updatable = false) private int voteRank;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    public static Vote create(Long sessionId, Long voterId, Long target, int rank, Instant now) {
        Vote vote = new Vote();
        vote.sessionId = sessionId; vote.voterId = voterId; vote.targetMemberId = target;
        vote.voteRank = rank; vote.createdAt = now;
        return vote;
    }
}
