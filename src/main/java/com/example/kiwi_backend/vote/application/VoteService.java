package com.example.kiwi_backend.vote.application;

import com.example.kiwi_backend.common.realtime.UserMessageEvent;
import com.example.kiwi_backend.contract.command.*;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.vote.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoteService {
    private final SessionLiveAccess access;
    private final VoteRepository votes;
    private final MatchResultRepository matches;
    private final GemWallet wallet;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final ResultWindowAccess resultWindow;

    @Transactional
    public Submission submit(Long sessionId, Long memberId, List<Long> targets) {
        var context = access.lock(sessionId);
        context.requireParticipant(memberId);
        if (targets == null || targets.size() > 3 || targets.stream().anyMatch(Objects::isNull)
                || new HashSet<>(targets).size() != targets.size()) {
            throw new IllegalArgumentException("Choose zero to three distinct participants");
        }
        targets.forEach(target -> context.requireOppositePair(memberId, target));
        if (access.hasVoted(sessionId, memberId)) {
            var saved = ownTargets(sessionId, memberId);
            if (!saved.equals(targets)) throw new IllegalStateException("Submitted votes cannot be changed");
            return new Submission(true, saved);
        }
        var now = clock.instant();
        if (context.phase() != LiveSessionContext.Phase.VOTING || context.deadline() == null
                || !now.isBefore(context.deadline())) throw new IllegalStateException("Voting is closed");
        for (int i = 0; i < targets.size(); i++) {
            votes.save(Vote.create(sessionId, memberId, targets.get(i), i + 1, now));
        }
        access.recordVote(sessionId, memberId, now);
        if (context.completionRewardGem() > 0) {
            wallet.apply(GemCommand.completionRefund(memberId, sessionId, context.completionRewardGem()));
        }
        return new Submission(true, List.copyOf(targets));
    }

    @Transactional
    public Submission ownVote(Long sessionId, Long memberId) {
        access.lock(sessionId).requireParticipant(memberId);
        return new Submission(access.hasVoted(sessionId, memberId), ownTargets(sessionId, memberId));
    }

    private List<Long> ownTargets(Long sessionId, Long memberId) {
        return votes.findAllBySessionIdAndVoterIdOrderByVoteRankAsc(sessionId, memberId)
                .stream().map(Vote::getTargetMemberId).toList();
    }

    @Transactional
    public void close(Long sessionId) {
        var context = access.lock(sessionId);
        var now = clock.instant();
        if (context.phase() != LiveSessionContext.Phase.VOTING) return;
        if (context.deadline() == null || now.isBefore(context.deadline())) return;
        Map<Long, Long> firstChoices = votes.findAllBySessionIdAndVoteRank(sessionId, 1).stream()
                .collect(Collectors.toMap(Vote::getVoterId, Vote::getTargetMemberId));
        var pairs = MutualFirstChoiceMatcher.match(firstChoices);
        matches.saveAll(pairs.stream().map(pair -> MatchResult.create(sessionId, pair, now)).toList());
        access.finishVoting(sessionId, now);
        events.publishEvent(new MatchingCompletedEvent(sessionId, pairs));
        for (var participant : context.participants()) {
            events.publishEvent(new UserMessageEvent(participant.memberId(), "/queue/live/results-ready",
                    new Summary(sessionId, pairs.size())));
        }
    }

    @Transactional
    public Summary summary(Long sessionId, Long memberId) {
        resultWindow.requireResultOpen(sessionId, memberId);
        var context = access.lock(sessionId);
        context.requireParticipant(memberId);
        if (context.phase() != LiveSessionContext.Phase.RESULT_AND_SHARE) {
            throw new IllegalStateException("Results are not ready");
        }
        return new Summary(sessionId, matches.countBySessionId(sessionId));
    }

    public record Submission(boolean submitted, List<Long> rankedMemberIds) { }
    public record Summary(Long sessionId, long coupleCount) { }
}
