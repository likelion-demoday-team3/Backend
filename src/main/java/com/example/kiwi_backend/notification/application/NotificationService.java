package com.example.kiwi_backend.notification.application;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.common.notification.NotificationEvent;
import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.notification.domain.Notification;
import com.example.kiwi_backend.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 알림함. 다른 모듈이 발행한 NotificationEvent를 같은 트랜잭션 안에서 저장한다
 * (취소·환불이 롤백되면 알림도 남지 않는다). 같은 dedupeKey는 한 번만 저장한다.
 *
 * 발행하는 쪽(세션 판단, 로비 마감, 연락처 교환)은 모두 세션 행을 잠근 뒤 발행하므로
 * 같은 키가 동시에 들어오는 경우는 없다. 재시도는 앞선 트랜잭션이 커밋된 뒤라 exists 확인으로 걸러진다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notifications;
    private final Clock clock;

    @EventListener
    @Transactional
    public void record(NotificationEvent event) {
        if (notifications.existsByDedupeKey(event.dedupeKey())) {
            return;
        }
        notifications.save(Notification.create(event.memberId(), event.type(), event.title(), event.body(),
                event.sessionId(), event.link(), event.dedupeKey(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public History list(Long memberId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Slice<Notification> slice = notifications.findByMemberIdOrderByCreatedAtDescIdDesc(
                memberId, PageRequest.of(safePage, safeSize));
        return new History(slice.getContent().stream().map(NotificationView::of).toList(), slice.hasNext());
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(Long memberId) {
        return new UnreadCount(notifications.countByMemberIdAndReadAtIsNull(memberId));
    }

    @Transactional
    public NotificationView markRead(Long memberId, Long notificationId) {
        Notification notification = notifications.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new NotFoundException("알림을 찾을 수 없습니다"));
        notification.markRead(clock.instant());
        return NotificationView.of(notification);
    }

    @Transactional
    public UnreadCount markAllRead(Long memberId) {
        notifications.markAllRead(memberId, clock.instant());
        return new UnreadCount(0);
    }

    public record NotificationView(Long id, NotificationType type, String title, String body,
                                   Long sessionId, String link, Instant createdAt, boolean read) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                    n.getSessionId(), n.getLink(), n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    public record History(List<NotificationView> items, boolean hasNext) {
    }

    public record UnreadCount(long count) {
    }
}
