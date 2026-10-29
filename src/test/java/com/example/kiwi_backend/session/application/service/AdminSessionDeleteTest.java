package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.session.api.dto.SessionDeleteResponse;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 운영자 삭제: 신청 기록이 없으면 지우고, 있으면 환불 후 취소한다 (실제 DB 외래키까지 확인) */
@SpringBootTest
@ActiveProfiles("test")
class AdminSessionDeleteTest {

    private static final AtomicLong MEMBER_IDS = new AtomicLong(90_000);

    @Autowired AdminSessionService service;
    @Autowired DatingSessionRepository sessions;
    @Autowired SessionParticipantRepository participants;

    @Test
    void sessionWithoutApplicationsIsDeleted() {
        Long sessionId = recruitingSession();

        SessionDeleteResponse response = service.delete(sessionId);

        assertThat(response).isEqualTo(SessionDeleteResponse.deleted(sessionId));
        assertThat(sessions.findById(sessionId)).isEmpty();
    }

    @Test
    void sessionWithApplicantsIsCancelledInsteadOfDeleted() {
        Long sessionId = recruitingSession();
        apply(sessionId, Gender.MALE);
        apply(sessionId, Gender.FEMALE);

        SessionDeleteResponse response = service.delete(sessionId);

        assertThat(response).isEqualTo(SessionDeleteResponse.cancelled(sessionId, 2));
        assertThat(sessions.findById(sessionId)).get()
                .extracting(DatingSession::getStatus).isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    void sessionWhoseApplicantsAllWithdrewIsStillKeptAsCancelled() {
        Long sessionId = recruitingSession();
        SessionParticipant withdrawn = apply(sessionId, Gender.MALE);
        withdrawn.cancel(Instant.now());
        participants.saveAndFlush(withdrawn);

        SessionDeleteResponse response = service.delete(sessionId);

        assertThat(response).isEqualTo(SessionDeleteResponse.cancelled(sessionId, 0));
        assertThat(sessions.findById(sessionId)).isPresent();
    }

    @Test
    void cancelledOrStartedSessionCannotBeDeleted() {
        Long cancelled = recruitingSession();
        service.delete(cancelled);   // 신청 없음 → 삭제됨
        Long cancelledWithApplicant = recruitingSession();
        apply(cancelledWithApplicant, Gender.MALE);
        service.delete(cancelledWithApplicant);   // → 취소됨

        assertThatThrownBy(() -> service.delete(cancelledWithApplicant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 취소된 세션입니다");

        Long inProgress = recruitingSession();
        DatingSession session = sessions.findById(inProgress).orElseThrow();
        session.markReady();
        session.startProgress();
        sessions.saveAndFlush(session);

        assertThatThrownBy(() -> service.delete(inProgress))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("로비 마감 전까지만 삭제할 수 있습니다");
    }

    private Long recruitingSession() {
        Instant now = Instant.now();
        DatingSession session = DatingSession.create(now.plusSeconds(7200), 3, 180, 120, 300, 0, 0, now);
        session.openRecruiting(now);
        return sessions.saveAndFlush(session).getId();
    }

    private SessionParticipant apply(Long sessionId, Gender gender) {
        long memberId = MEMBER_IDS.incrementAndGet();
        return participants.saveAndFlush(
                SessionParticipant.apply(sessionId, memberId, gender, "alias-" + memberId, Instant.now()));
    }
}
