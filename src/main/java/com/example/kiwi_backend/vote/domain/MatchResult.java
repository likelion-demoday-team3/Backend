package com.example.kiwi_backend.vote.domain;
import com.example.kiwi_backend.contract.dto.MatchedPair;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity
@Table(name = "match_result", uniqueConstraints = @UniqueConstraint(name = "uk_match_result",
        columnNames = {"session_id", "member_a_id", "member_b_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long sessionId;
    @Column(name = "member_a_id", nullable = false, updatable = false) private Long memberAId;
    @Column(name = "member_b_id", nullable = false, updatable = false) private Long memberBId;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    public static MatchResult create(Long sessionId, MatchedPair pair, Instant now) {
        MatchResult match = new MatchResult();
        match.sessionId = sessionId; match.memberAId = pair.memberAId(); match.memberBId = pair.memberBId();
        match.createdAt = now;
        return match;
    }
}
