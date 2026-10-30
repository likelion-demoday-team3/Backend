package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.command.SessionAttendanceCommand;
import com.example.kiwi_backend.contract.command.SessionLivePhaseCommand;
import com.example.kiwi_backend.contract.command.SessionLiveStatusCommand;
import com.example.kiwi_backend.contract.dto.AttendanceResult;
import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.LiveStatus;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.policy.ProceedPolicy;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 로비 마감(출석 확정)과 라운드 시작 기록. A(live)가 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionLiveService implements SessionAttendanceCommand, SessionLiveStatusCommand, SessionLivePhaseCommand {

    private final DatingSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final GemWallet gemWallet;
    private final Clock clock;

    @Override
    @Transactional
    public AttendanceResult finalizeAttendance(Long sessionId, Set<Long> connectedMemberIds) {
        DatingSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));

        // [제안 1] 이미 다른 호출이 처리한 세션(READY가 아님)이면 아무것도 하지 않고 표시만 돌려준다.
        //          세션 행을 잠근 뒤에 상태를 보므로, 동시에 두 번 불려도 한 번만 처리된다.
        if (session.getStatus() != SessionStatus.READY) {
            return AttendanceResult.alreadyProcessed(sessionId);
        }

        Instant now = clock.instant();
        if (!session.isDueForLobbyClose(now)) {
            throw new IllegalStateException("로비 마감 시각 전에는 출석을 확정할 수 없습니다");
        }

        // 접속중인 신청자 배열
        Set<Long> connected = connectedMemberIds == null ? Set.of() : connectedMemberIds;
        // 신청자 배열 (접속 중이던 아니던 모든 신청자 포함)
        List<SessionParticipant> applied =
                participantRepository.findAllBySessionIdAndStatus(sessionId, ParticipantStatus.APPLIED);

        // 참가자 배열 (접속 중 + 신청 완료 만족)
        // 접속자 중 신청자 → 출석, 나머지 신청자 → 노쇼.
        // 신청자가 아닌 접속자는 무시한다 (isParticipant 검사를 통과해야 로비에 들어오므로 원래 없어야 함).
        List<SessionParticipant> attended = new ArrayList<>();
        for (SessionParticipant participant : applied) {
            if (connected.contains(participant.getMemberId())) {
                participant.attend(); // ATTENDED로 설정
                attended.add(participant);
            } else {
                participant.markNoShow(); //NO_SHOW로 설정
            }
        }

        int male = (int) attended.stream().filter(p -> p.getGender() == Gender.MALE).count();
        int female = attended.size() - male;

        // 접속자 0명도 이 규칙을 그대로 따른다: 진행 조건 미달 → 세션 취소, 전원 노쇼라 환불 없음
        if (ProceedPolicy.canProceed(male, female)) {
            session.startProgress();
            log.info("로비 마감, 진행 sessionId={} male={} female={} firstRoundAt={}",
                    sessionId, male, female, session.getPhaseEndsAt());
            // [제안 3] 별칭까지 담아서 돌려준다
            List<LiveParticipant> attendees = attended.stream()
                    .map(p -> new LiveParticipant(p.getMemberId(), p.getGender(), p.getAlias()))
                    .toList();
            return AttendanceResult.proceeded(sessionId, attendees, session.getPhaseEndsAt());
        }

        session.cancel();
        refundAll(session, attended);
        log.info("로비 마감, 출석 미달 취소 sessionId={} male={} female={} noShow={}",
                sessionId, male, female, applied.size() - attended.size());
        return AttendanceResult.cancelled(sessionId);
    }

    @Override
    @Transactional
    public void markRoundsStarted(Long sessionId) {
        DatingSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        if (session.getLiveStatus() != LiveStatus.WAITING
                || session.getPhaseEndsAt() == null
                || clock.instant().isBefore(session.getPhaseEndsAt())) {
            throw new IllegalStateException("First round is not due or has already started");
        }
        session.markRoundsStarted();
    }

    @Override
    @Transactional
    public Instant startVoting(Long sessionId, Instant startedAt) {
        DatingSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        Instant votingEndsAt = session.startVoting(startedAt);
        log.info("투표 시작 sessionId={} votingEndsAt={}", sessionId, votingEndsAt);
        return votingEndsAt;
    }

    /** 출석 미달 취소: 출석자만 100% 환불 (노쇼는 환불 없음). 세션 → 지갑 순서로 잠근다 */
    private void refundAll(DatingSession session, List<SessionParticipant> attended) {
        int fee = session.getEntryFeeGem();
        if (fee <= 0) {
            return;
        }
        for (SessionParticipant participant : attended) {
            gemWallet.apply(GemCommand.entryFeeRefund(
                    participant.getMemberId(), session.getId(), fee, participant.getAppliedAt()));
        }
    }
}
