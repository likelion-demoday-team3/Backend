package com.example.kiwi_backend.live.domain.entity;

import com.example.kiwi_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "session_compatibility", uniqueConstraints = @UniqueConstraint(
        name = "uk_session_compatibility_pair",
        columnNames = {"session_id", "member_a_id", "member_b_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionCompatibility extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "member_a_id", nullable = false, updatable = false)
    private Long memberAId;

    @Column(name = "member_b_id", nullable = false, updatable = false)
    private Long memberBId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, length = 500)
    private String reason;

    public static SessionCompatibility create(Long sessionId, Long firstMemberId, Long secondMemberId,
                                                int score, String reason) {
        if (sessionId == null || firstMemberId == null || secondMemberId == null
                || firstMemberId.equals(secondMemberId)) {
            throw new IllegalArgumentException("session and two distinct members are required");
        }
        if (score < 0 || score > 100 || reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("invalid compatibility result");
        }
        SessionCompatibility result = new SessionCompatibility();
        result.sessionId = sessionId;
        result.memberAId = Math.min(firstMemberId, secondMemberId);
        result.memberBId = Math.max(firstMemberId, secondMemberId);
        result.score = score;
        result.reason = reason;
        return result;
    }
}
