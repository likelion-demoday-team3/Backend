package com.example.kiwi_backend.live.domain.entity;

import com.example.kiwi_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "live_round_idle_participant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_live_round_idle_round_member", columnNames = {"round_id", "member_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveRoundIdleParticipant extends BaseTimeEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false, updatable = false)
    private LiveRound round;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    public static LiveRoundIdleParticipant create(LiveRound round, Long memberId) {
        if (round == null) {
            throw new IllegalArgumentException("round must not be null");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("memberId must not be null");
        }

        LiveRoundIdleParticipant idleParticipant = new LiveRoundIdleParticipant();
        idleParticipant.round = round;
        idleParticipant.memberId = memberId;
        return idleParticipant;
    }
}
