package com.example.kiwi_backend.interaction.application;

import com.example.kiwi_backend.contract.command.SessionLiveAccess;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.dto.LiveSessionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LiveSessionStateService {

    private final SessionLiveAccess access;

    @Transactional
    public State get(Long sessionId, Long memberId) {
        LiveSessionContext context = access.lock(sessionId);
        context.requireParticipant(memberId);
        return new State(sessionId, context.phase(), context.deadline(),
                access.hasVoted(sessionId, memberId), context.participants());
    }

    public record State(Long sessionId, LiveSessionContext.Phase phase, Instant deadline,
                        boolean voteSubmitted, List<LiveParticipant> participants) {
        public State {
            participants = List.copyOf(participants);
        }
    }
}
