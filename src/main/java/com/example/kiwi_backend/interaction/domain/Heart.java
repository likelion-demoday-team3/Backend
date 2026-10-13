package com.example.kiwi_backend.interaction.domain;

import com.example.kiwi_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "heart", uniqueConstraints = @UniqueConstraint(name = "uk_heart_pair",
        columnNames = {"session_id", "sender_id", "receiver_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Heart extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false) private Long sessionId;
    @Column(nullable = false, updatable = false) private Long senderId;
    @Column(nullable = false, updatable = false) private Long receiverId;
    @Column(nullable = false, updatable = false) private int chargedGem;

    public static Heart create(Long sessionId, Long senderId, Long receiverId, int chargedGem) {
        Heart heart = new Heart();
        heart.sessionId = sessionId;
        heart.senderId = senderId;
        heart.receiverId = receiverId;
        heart.chargedGem = chargedGem;
        return heart;
    }
}
