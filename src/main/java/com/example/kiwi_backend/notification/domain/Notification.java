package com.example.kiwi_backend.notification.domain;

import com.example.kiwi_backend.common.notification.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 알림함의 알림 한 건. 읽으면 readAt이 채워진다 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, updatable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, updatable = false, length = 100)
    private String title;

    @Column(nullable = false, updatable = false, length = 500)
    private String body;

    @Column(updatable = false)
    private Long sessionId;

    @Column(updatable = false)
    private String link;

    /** 같은 일로 알림이 두 번 남지 않게 하는 키 */
    @Column(nullable = false, unique = true, updatable = false, length = 200)
    private String dedupeKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant readAt;

    public static Notification create(Long memberId, NotificationType type, String title, String body,
                                      Long sessionId, String link, String dedupeKey, Instant now) {
        Notification notification = new Notification();
        notification.memberId = memberId;
        notification.type = type;
        notification.title = title;
        notification.body = body;
        notification.sessionId = sessionId;
        notification.link = link;
        notification.dedupeKey = dedupeKey;
        notification.createdAt = now;
        return notification;
    }

    /** 이미 읽은 알림이면 처음 읽은 시각을 유지한다 */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
