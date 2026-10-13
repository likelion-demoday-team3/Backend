package com.example.kiwi_backend.contract.command;

import com.example.kiwi_backend.contract.dto.LiveSessionContext;
import java.time.Instant;
import java.util.List;

public interface SessionLiveAccess {
    /** Must be called inside the caller's transaction. Locks session before wallet or interaction rows. */
    LiveSessionContext lock(Long sessionId);
    Instant startHearts(Long sessionId, Instant now);
    List<Long> findHeartSessionsDue(Instant now);
    List<Long> findVotingSessionsDue(Instant now);
    boolean hasVoted(Long sessionId, Long memberId);
    void recordVote(Long sessionId, Long memberId, Instant now);
    void finishVoting(Long sessionId, Instant now);
}
