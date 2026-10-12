package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.contract.dto.GemResult;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.MemberSummary;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.session.api.dto.ApplyResponse;
import com.example.kiwi_backend.session.api.dto.CancelResponse;
import com.example.kiwi_backend.session.application.support.AliasGenerator;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.policy.RefundPolicy;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * 세션 신청과 본인 취소.
 *
 * 동시성: 세션 행을 먼저 잠그고(같은 세션의 신청·취소·진행 판단이 한 줄로 처리됨) 그다음 자리를 센다.
 * 잠금 순서는 항상 세션 → 지갑. 반대 순서로 잠그는 코드가 생기면 교착 상태가 날 수 있다.
 *
 * 참가비 차감은 같은 트랜잭션이라, 잔액 부족으로 실패하면 신청도 함께 취소된다.
 */
@Service
@RequiredArgsConstructor
public class SessionApplicationService {

    private final DatingSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final MemberQueryService memberQueryService;
    private final GemWallet gemWallet;
    private final AliasGenerator aliasGenerator;
    private final Clock clock;

    @Transactional
    public ApplyResponse apply(Long memberId, Long sessionId) {
        MemberSummary member = memberQueryService.getSummary(memberId);
        if (!member.active()) {
            throw new IllegalStateException("이용할 수 없는 계정입니다");
        }

        DatingSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));

        Instant now = clock.instant();
        if (!session.isApplicationOpen(now)) {
            throw new IllegalStateException("신청 기간이 아닙니다");
        }

        Optional<SessionParticipant> existing = participantRepository.findBySessionIdAndMemberId(sessionId, memberId);
        if (existing.isPresent() && existing.get().isActive()) {
            throw new IllegalStateException("이미 신청한 세션입니다");
        }

        Gender gender = member.gender();
        long applied = participantRepository.countBySessionIdAndGenderAndStatus(
                sessionId, gender, ParticipantStatus.APPLIED);
        if (applied >= session.getCapacityPerGender()) {
            throw new IllegalStateException((gender == Gender.MALE ? "남성" : "여성") + " 자리가 마감됐어요");
        }

        SessionParticipant participant;
        if (existing.isPresent()) {
            participant = existing.get();
            participant.reapply(now);
        } else {
            String alias = aliasGenerator.generate(participantRepository.findAliasesBySessionId(sessionId));
            participant = participantRepository.save(
                    SessionParticipant.apply(sessionId, memberId, gender, alias, now));
        }

        int fee = session.getEntryFeeGem();
        Integer balanceAfter = null;
        if (fee > 0) {
            GemResult result = gemWallet.apply(
                    GemCommand.entryFee(memberId, sessionId, fee, participant.getAppliedAt()));
            balanceAfter = result.balanceAfter();
        }

        return new ApplyResponse(sessionId, participant.getAlias(), fee, balanceAfter);
    }

    @Transactional
    public CancelResponse cancel(Long memberId, Long sessionId) {
        DatingSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));

        Instant now = clock.instant();
        if (!session.isApplicationOpen(now)) {
            throw new IllegalStateException("취소할 수 있는 기간이 지났습니다");
        }

        SessionParticipant participant = participantRepository.findBySessionIdAndMemberId(sessionId, memberId)
                .filter(p -> p.getStatus() == ParticipantStatus.APPLIED)
                .orElseThrow(() -> new NotFoundException("신청 내역이 없습니다"));

        participant.cancel(now);

        int refund = RefundPolicy.refundAmount(session.getEntryFeeGem(), session.getStartTime(), now);
        if (refund > 0) {
            gemWallet.apply(GemCommand.entryFeeRefund(memberId, sessionId, refund, participant.getAppliedAt()));
        }

        return new CancelResponse(sessionId, refund);
    }
}
