package com.example.kiwi_backend.report.application;

import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.contract.query.MemberCredentialQuery;
import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.enumerated.MemberStatus;
import com.example.kiwi_backend.member.domain.enumerated.Role;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import com.example.kiwi_backend.notification.application.NotificationService;
import com.example.kiwi_backend.report.domain.ReportReason;
import com.example.kiwi_backend.report.domain.ReportRepository;
import com.example.kiwi_backend.report.domain.ReportStatus;
import com.example.kiwi_backend.session.application.service.SessionApplicationService;
import com.example.kiwi_backend.session.domain.entity.*;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import com.example.kiwi_backend.wallet.application.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** 신고 접수 → 운영자 알림 → 운영자 확인 후 이용 정지(세션 신청만 막고 예정 세션은 취소·환불) */
@SpringBootTest
@ActiveProfiles("test")
class ReportFlowTransactionTest {
    @Autowired ReportService reportService;
    @Autowired AdminReportService adminReportService;
    @Autowired ReportRepository reports;
    @Autowired MemberRepository members;
    @Autowired MemberCredentialQuery credentials;
    @Autowired DatingSessionRepository sessions;
    @Autowired SessionParticipantRepository participants;
    @Autowired LiveRoundRepository rounds;
    @Autowired LiveRoomRepository rooms;
    @Autowired WalletService wallet;
    @Autowired NotificationService notifications;
    @Autowired SessionApplicationService applications;

    private Long sessionId;
    private Long upcomingSessionId;
    private Long halfRefundSessionId;
    private Long noRefundSessionId;
    private Long reporter;
    private Long reported;
    private Long stranger;
    private Long admin;
    private Instant now;

    @BeforeEach
    void setup() {
        now = Instant.now();
        reporter = member(Gender.MALE, false);
        reported = member(Gender.FEMALE, false);
        stranger = member(Gender.FEMALE, false);
        admin = member(Gender.MALE, true);

        // 진행 중인 세션: reporter와 reported가 1라운드에서 대화했다. stranger는 출석했지만 대화한 적 없다
        var session = DatingSession.create(now.minusSeconds(600), 3, 180, 120, 300, 12, 2, now.minusSeconds(7200));
        session.openRecruiting(now.minusSeconds(7200));
        session.markReady(); session.startProgress(); session.markRoundsStarted();
        sessionId = sessions.saveAndFlush(session).getId();
        for (Long id : new Long[]{reporter, reported, stranger}) {
            var participant = SessionParticipant.apply(sessionId, id,
                    id.equals(reporter) ? Gender.MALE : Gender.FEMALE, "alias-" + id, now.minusSeconds(3600));
            participant.attend();
            participants.saveAndFlush(participant);
        }
        var round = LiveRound.create(sessionId, 1);
        round.start(now.minusSeconds(60));
        rooms.saveAndFlush(LiveRoom.create(rounds.saveAndFlush(round), 1, reporter, reported));

        // reported가 참가비 12개씩 내고 미리 신청해 둔, 아직 시작 전인 세션 셋 (시작까지 25시간·18시간·6시간)
        wallet.createWallet(reported);
        wallet.apply(GemCommand.adminGrant(reported, 36, "test", UUID.randomUUID().toString()));
        upcomingSessionId = upcomingApplication(25);
        halfRefundSessionId = upcomingApplication(18);
        noRefundSessionId = upcomingApplication(6);
    }

    private Long upcomingApplication(int hoursBeforeStart) {
        var upcoming = DatingSession.create(now.plusSeconds(hoursBeforeStart * 3_600L), 3, 180, 120, 300, 12, 2, now);
        upcoming.openRecruiting(now);
        Long id = sessions.saveAndFlush(upcoming).getId();
        Instant appliedAt = now.minusSeconds(10);
        participants.saveAndFlush(SessionParticipant.apply(id, reported, Gender.FEMALE, "upcoming-" + id, appliedAt));
        wallet.apply(GemCommand.entryFee(reported, id, 12, appliedAt));
        return id;
    }

    @Test
    void reportNotifiesAdminsOnceAndRepeatedReportReturnsTheSameOne() {
        var first = reportService.submit(sessionId, reporter, reported, ReportReason.VERBAL_ABUSE);
        var again = reportService.submit(sessionId, reporter, reported, ReportReason.RULE_VIOLATION);

        assertThat(first.alreadyReported()).isFalse();
        assertThat(again.alreadyReported()).isTrue();
        assertThat(again.reportId()).isEqualTo(first.reportId());
        assertThat(notifications.list(admin, 0, 20).items()).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(NotificationType.REPORT_SUBMITTED);
            assertThat(item.link()).isEqualTo("/admin/reports");
            assertThat(item.body()).contains("alias-" + reported).contains("사이버 언어폭력을 해요.");
        });
        // 일반 회원은 신고 알림을 받지 않는다
        assertThat(notifications.list(reporter, 0, 20).items()).isEmpty();
    }

    @Test
    void onlyPartnersYouTalkedWithCanBeReported() {
        assertThatThrownBy(() -> reportService.submit(sessionId, reporter, stranger, ReportReason.RULE_VIOLATION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reportService.submit(sessionId, reporter, reporter, ReportReason.RULE_VIOLATION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(reports.findAll()).noneMatch(report -> report.getSessionId().equals(sessionId));
    }

    @Test
    void suspendBlocksNewApplicationsRefundsUpcomingSessionsAndResolvesReports() {
        var receipt = reportService.submit(sessionId, reporter, reported, ReportReason.SEXUAL_HARASSMENT);
        assertThat(wallet.getBalance(reported).gemBalance()).isZero();

        var result = adminReportService.suspend(reported, admin);

        assertThat(result.cancelledApplications()).isEqualTo(3);
        assertThat(result.resolvedReports()).isEqualTo(1);
        assertThat(members.findById(reported).orElseThrow().getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        // 시작 전 세션은 모두 취소하고, 참가비는 본인 취소와 같은 규정으로 돌려준다
        // 25시간 전 100%(12) + 18시간 전 50%(6) + 6시간 전 0% = 18
        for (Long id : new Long[]{upcomingSessionId, halfRefundSessionId, noRefundSessionId}) {
            assertThat(participants.findBySessionIdAndMemberId(id, reported).orElseThrow().getStatus())
                    .isEqualTo(ParticipantStatus.CANCELLED);
        }
        assertThat(result.refundedGem()).isEqualTo(18);
        assertThat(wallet.getBalance(reported).gemBalance()).isEqualTo(18);
        // 이미 진행 중인 세션 참가는 건드리지 않는다
        assertThat(participants.findBySessionIdAndMemberId(sessionId, reported).orElseThrow().getStatus())
                .isEqualTo(ParticipantStatus.ATTENDED);
        // 신고는 처리 완료로, 운영자 목록에는 정지 상태와 누적 신고 수가 보인다
        assertThat(reports.findById(receipt.reportId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.ACTIONED);
        assertThat(adminReportService.list(ReportStatus.PENDING)).noneMatch(view -> view.sessionId().equals(sessionId));
        var view = adminReportService.list(null).stream()
                .filter(item -> item.reportId().equals(receipt.reportId())).findFirst().orElseThrow();
        assertThat(view.reported().status()).isEqualTo("SUSPENDED");
        assertThat(view.reported().alias()).isEqualTo("alias-" + reported);
        assertThat(view.reporter().memberId()).isEqualTo(reporter);
        assertThat(view.reportedTotalCount()).isEqualTo(1);
        assertThat(view.reasonLabel()).isEqualTo("성희롱을 했어요.");
        // 정지 알림
        assertThat(notifications.list(reported, 0, 20).items()).anySatisfy(item -> {
            assertThat(item.type()).isEqualTo(NotificationType.ACCOUNT_SUSPENDED);
            assertThat(item.body()).contains("세션 3개는 취소됐어요").contains("보석 18개를 돌려드렸어요");
        });

        // 세션 신청만 막는다: 로그인은 되고, 새 신청은 거절된다
        String email = members.findById(reported).orElseThrow().getEmail();
        assertThat(credentials.findByEmail(email).orElseThrow().loginAllowed()).isTrue();
        assertThatThrownBy(() -> applications.apply(reported, upcomingSessionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이용할 수 없는 계정입니다");

        // 정지 해제
        adminReportService.reactivate(reported, admin);
        assertThat(members.findById(reported).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(notifications.list(reported, 0, 20).items())
                .anyMatch(item -> item.type() == NotificationType.ACCOUNT_REACTIVATED);
    }

    @Test
    void dismissOnlyPendingReportsAndAdminsCannotBeSuspended() {
        var receipt = reportService.submit(sessionId, reporter, reported, ReportReason.LEFT_EARLY);

        adminReportService.dismiss(receipt.reportId(), admin);

        assertThat(reports.findById(receipt.reportId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThatThrownBy(() -> adminReportService.dismiss(receipt.reportId(), admin))
                .isInstanceOf(IllegalStateException.class);
        assertThat(members.findById(reported).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThatThrownBy(() -> adminReportService.suspend(admin, admin)).isInstanceOf(IllegalStateException.class);
        Long otherAdmin = member(Gender.FEMALE, true);
        assertThatThrownBy(() -> adminReportService.suspend(otherAdmin, admin)).isInstanceOf(IllegalStateException.class);
    }

    private Long member(Gender gender, boolean isAdmin) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member member = Member.register("report-" + unique + "@yonsei.ac.kr", "테스트", "hash", "연세대학교", gender,
                LocalDate.of(2003, 1, 1), null, "kiwi_" + unique, now, LocalDate.now());
        if (isAdmin) {
            ReflectionTestUtils.setField(member, "role", Role.ADMIN);
        }
        return members.saveAndFlush(member).getId();
    }
}
