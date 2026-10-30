package com.example.kiwi_backend.notification.application;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.common.notification.NotificationEvent;
import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.session.application.service.SessionDecisionService;
import com.example.kiwi_backend.session.application.service.SessionLiveService;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import com.example.kiwi_backend.wallet.application.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 세션 진행·취소가 알림함에 남는지 (A파트). 실제 서비스와 DB로 확인한다 */
@SpringBootTest
@ActiveProfiles("test")
class SessionNotificationFlowTest {

    private static final AtomicLong MEMBER_IDS = new AtomicLong(110_000);

    @Autowired SessionDecisionService decisions;
    @Autowired SessionLiveService live;
    @Autowired NotificationService notifications;
    @Autowired DatingSessionRepository sessions;
    @Autowired SessionParticipantRepository participants;
    @Autowired WalletService wallet;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void lowApplicantsCancellationNotifiesEveryApplicantWithRefund() {
        Long sessionId = recruitingSession(10);
        List<Long> applicants = apply(sessionId, Gender.MALE, Gender.FEMALE);

        decisions.decide(sessionId, Instant.now());

        for (Long memberId : applicants) {
            var item = only(memberId);
            assertThat(item.type()).isEqualTo(NotificationType.SESSION_CANCELLED_LOW_APPLICANTS);
            assertThat(item.body()).contains("신청 인원이 모자라 취소됐어요").contains("참가비 ◆10개를 돌려드렸어요");
            assertThat(item.link()).isEqualTo("/mypage/reservation");
            assertThat(item.read()).isFalse();
        }
    }

    @Test
    void confirmationNotifiesApplicantsWithSessionLink() {
        Long sessionId = recruitingSession(0);
        List<Long> applicants = apply(sessionId, Gender.MALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE);

        decisions.decide(sessionId, Instant.now());

        var item = only(applicants.getFirst());
        assertThat(item.type()).isEqualTo(NotificationType.SESSION_CONFIRMED);
        assertThat(item.link()).isEqualTo("/session/" + sessionId);
        assertThat(item.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void adminCancellationNotifiesApplicants() {
        Long sessionId = recruitingSession(10);
        Long applicant = apply(sessionId, Gender.MALE).getFirst();

        decisions.cancelByAdmin(sessionId);

        var item = only(applicant);
        assertThat(item.type()).isEqualTo(NotificationType.SESSION_CANCELLED_BY_ADMIN);
        assertThat(item.body()).startsWith("운영 사정으로");
    }

    @Test
    void lobbyCloseNotifiesNoShowsOnly() {
        Long sessionId = readySessionStartedJustNow(10);
        List<Long> members = apply(sessionId,
                Gender.MALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE);
        Long absent = members.getLast();
        Set<Long> connected = new HashSet<>(members.subList(0, 5));

        live.finalizeAttendance(sessionId, connected);

        var item = only(absent);
        assertThat(item.type()).isEqualTo(NotificationType.NO_SHOW);
        assertThat(item.body()).contains("불참 처리됐어요").contains("참가비는 환불되지 않아요");
        assertThat(notifications.list(members.getFirst(), 0, 20).items()).isEmpty();
    }

    @Test
    void lowAttendanceCancellationTellsAttendeesAboutRefundAndNoShowsAboutNone() {
        Long sessionId = readySessionStartedJustNow(10);
        List<Long> members = apply(sessionId, Gender.MALE, Gender.FEMALE, Gender.FEMALE);

        live.finalizeAttendance(sessionId, Set.of(members.get(0), members.get(1)));

        assertThat(only(members.get(0)).body()).contains("참가비 ◆10개를 돌려드렸어요");
        var noShow = only(members.get(2));
        assertThat(noShow.type()).isEqualTo(NotificationType.SESSION_CANCELLED_LOW_ATTENDANCE);
        assertThat(noShow.body()).contains("참가비는 환불되지 않아요").doesNotContain("돌려드렸어요");
    }

    @Test
    void sameDedupeKeyIsStoredOnceAndReadStateIsPerMember() {
        long memberId = MEMBER_IDS.incrementAndGet();
        var event = new NotificationEvent(memberId, NotificationType.SESSION_CONFIRMED, "제목", "본문", 1L,
                "/session/1", "TEST:" + memberId);
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        transaction.executeWithoutResult(status -> events.publishEvent(event));

        var item = only(memberId);
        assertThat(notifications.unreadCount(memberId).count()).isEqualTo(1);
        assertThatThrownBy(() -> notifications.markRead(memberId + 1, item.id())).isInstanceOf(NotFoundException.class);

        assertThat(notifications.markRead(memberId, item.id()).read()).isTrue();
        assertThat(notifications.unreadCount(memberId).count()).isZero();

        transaction.executeWithoutResult(status -> events.publishEvent(new NotificationEvent(memberId,
                NotificationType.NO_SHOW, "제목", "본문", 2L, null, "TEST2:" + memberId)));
        assertThat(notifications.unreadCount(memberId).count()).isEqualTo(1);
        notifications.markAllRead(memberId);
        assertThat(notifications.unreadCount(memberId).count()).isZero();
    }

    // ===== 픽스처 =====

    private NotificationService.NotificationView only(Long memberId) {
        var items = notifications.list(memberId, 0, 20).items();
        assertThat(items).hasSize(1);
        return items.getFirst();
    }

    /** 시작 10분 전이라 바로 30분 전 판단 대상인 모집 중 세션 */
    private Long recruitingSession(int fee) {
        Instant now = Instant.now();
        DatingSession session = DatingSession.create(now.plusSeconds(600), 3, 180, 120, 300, fee, 0,
                now.minusSeconds(7200));
        session.openRecruiting(now.minusSeconds(7200));
        return sessions.saveAndFlush(session).getId();
    }

    /** 방금 시작해서 로비 마감 차례인 진행 확정 세션 */
    private Long readySessionStartedJustNow(int fee) {
        Instant now = Instant.now();
        DatingSession session = DatingSession.create(now.minusSeconds(1), 3, 180, 120, 300, fee, 0,
                now.minusSeconds(7200));
        session.openRecruiting(now.minusSeconds(7200));
        session.markReady();
        return sessions.saveAndFlush(session).getId();
    }

    private List<Long> apply(Long sessionId, Gender... genders) {
        List<Long> ids = new ArrayList<>();
        for (Gender gender : genders) {
            long memberId = MEMBER_IDS.incrementAndGet();
            participants.saveAndFlush(SessionParticipant.apply(sessionId, memberId, gender, "alias-" + memberId,
                    Instant.now()));
            wallet.createWallet(memberId);
            ids.add(memberId);
        }
        return ids;
    }
}
