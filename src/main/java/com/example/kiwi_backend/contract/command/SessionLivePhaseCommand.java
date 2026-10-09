package com.example.kiwi_backend.contract.command;

import java.time.Instant;

public interface SessionLivePhaseCommand {

    /** Moves the session to VOTING and returns its deadline. */
    Instant startVoting(Long sessionId, Instant startedAt);
}
