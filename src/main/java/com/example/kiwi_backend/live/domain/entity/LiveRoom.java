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
        name = "live_room",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_live_room_round_table", columnNames = {"round_id", "table_no"}),
                @UniqueConstraint(name = "uk_live_room_round_male", columnNames = {"round_id", "male_member_id"}),
                @UniqueConstraint(name = "uk_live_room_round_female", columnNames = {"round_id", "female_member_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false, updatable = false)
    private LiveRound round;

    @Column(name = "table_no", nullable = false, updatable = false)
    private int tableNo;

    @Column(name = "male_member_id", nullable = false, updatable = false)
    private Long maleMemberId;

    @Column(name = "female_member_id", nullable = false, updatable = false)
    private Long femaleMemberId;

    public static LiveRoom create(LiveRound round, int tableNo, Long maleMemberId, Long femaleMemberId) {
        if (round == null) {
            throw new IllegalArgumentException("round must not be null");
        }
        if (tableNo < 1) {
            throw new IllegalArgumentException("tableNo must be positive");
        }
        if (maleMemberId == null) {
            throw new IllegalArgumentException("maleMemberId must not be null");
        }
        if (femaleMemberId == null) {
            throw new IllegalArgumentException("femaleMemberId must not be null");
        }
        if (maleMemberId.equals(femaleMemberId)) {
            throw new IllegalArgumentException("maleMemberId and femaleMemberId must be different");
        }

        LiveRoom room = new LiveRoom();
        room.round = round;
        room.tableNo = tableNo;
        room.maleMemberId = maleMemberId;
        room.femaleMemberId = femaleMemberId;
        return room;
    }
}
