package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.command.SessionSuspensionCommand;
import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.policy.RefundPolicy;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/** 이용 정지된 회원이 신청해 둔, 아직 시작하지 않은 세션을 취소한다. 참가비는 본인 취소와 같은 환불 규정을 따른다 */
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
    public Result cancelUpcomingApplications(Long memberId) {
        Instant now = clock.instant();
        int cancelled = 0;
        int refundedGem = 0;
        for (SessionParticipant participant : participantRepository.findAllByMemberIdOrderByAppliedAtDesc(memberId)) {
            if (participant.getStatus() != ParticipantStatus.APPLIED) {
                continue;
            }
            DatingSession session = sessionRepository.findByIdForUpdate(participant.getSessionId()).orElse(null);
            if (session == null || !UPCOMING.contains(session.getStatus())) {
                continue;
            }
            participant.cancel(now);
            // 본인 취소와 같은 환불 규정 (24시간 전 100%, 12시간 전 50%, 그 이후 0%).
            // 같은 신청은 한 번만 환불되도록 신청 시각으로 키를 만든다
            int refund = RefundPolicy.refundAmount(session.getEntryFeeGem(), session.getStartTime(), now);
            if (refund > 0) {
                gemWallet.apply(GemCommand.entryFeeRefund(memberId, session.getId(), refund,
                        participant.getAppliedAt()));
            }
            cancelled++;
            refundedGem += refund;
            log.info("이용 정지로 신청 취소 sessionId={} memberId={} refund={}", session.getId(), memberId, refund);
        }
        return new Result(cancelled, refundedGem);
    }
}
