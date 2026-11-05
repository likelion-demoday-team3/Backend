package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.command.SessionSuspensionCommand;
import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/** 이용 정지된 회원이 신청해 둔, 아직 시작하지 않은 세션을 취소하고 참가비를 전액 돌려준다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSuspensionService implements SessionSuspensionCommand {

    /** 아직 시작하지 않은 세션. IN_PROGRESS 이후는 이미 진행된 것이라 건드리지 않는다 */
    private static final Set<SessionStatus> UPCOMING = Set.of(
            SessionStatus.SCHEDULED, SessionStatus.RECRUITING, SessionStatus.READY);

    private final DatingSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final GemWallet gemWallet;
    private final Clock clock;

    @Override
    @Transactional
    public int cancelUpcomingApplications(Long memberId) {
        Instant now = clock.instant();
        int cancelled = 0;
        for (SessionParticipant participant : participantRepository.findAllByMemberIdOrderByAppliedAtDesc(memberId)) {
            if (participant.getStatus() != ParticipantStatus.APPLIED) {
                continue;
            }
            DatingSession session = sessionRepository.findByIdForUpdate(participant.getSessionId()).orElse(null);
            if (session == null || !UPCOMING.contains(session.getStatus())) {
                continue;
            }
            participant.cancel(now);
            // 본인 취소와 달리 마감 임박 감액 없이 전액 환불. 같은 신청은 한 번만 환불되도록 신청 시각으로 키를 만든다
            if (session.getEntryFeeGem() > 0) {
                gemWallet.apply(GemCommand.entryFeeRefund(memberId, session.getId(), session.getEntryFeeGem(),
                        participant.getAppliedAt()));
            }
            cancelled++;
            log.info("이용 정지로 신청 취소 sessionId={} memberId={}", session.getId(), memberId);
        }
        return cancelled;
    }
}
