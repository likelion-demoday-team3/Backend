package com.example.kiwi_backend.live.application;

import java.time.Instant;

public record LiveVotingStartedMessage(Long sessionId, Instant votingEndsAt) {
}
