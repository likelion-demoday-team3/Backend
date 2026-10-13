package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveRoundStateServiceTest {

    @BeforeEach
    void begin() { TransactionSynchronizationManager.initSynchronization(); }

    @AfterEach
    void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    private final LiveRoundRepository liveRoundRepository = mock(LiveRoundRepository.class);
    private final LiveRoundBroadcastService liveRoundBroadcastService = mock(LiveRoundBroadcastService.class);

    private final LiveRoundStateService service = new LiveRoundStateService(
            liveRoundRepository,
            liveRoundBroadcastService
    );

    @Test
    void startRoundChangesStatusAndBroadcastsAssignmentsAndIdleMessages() {
        LiveRound round = LiveRound.create(10L, 1);
        Instant startedAt = Instant.parse("2026-10-06T09:00:00Z");

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));

        LiveRoundStateResult result = service.startRound(10L, 1, startedAt);

        assertThat(round.getStatus()).isEqualTo(LiveRoundStatus.IN_PROGRESS);
        assertThat(round.getStartedAt()).isEqualTo(startedAt);
        assertThat(result.status()).isEqualTo(LiveRoundStatus.IN_PROGRESS);
        assertThat(result.assignmentBroadcasted()).isTrue();

        org.mockito.Mockito.verifyNoInteractions(liveRoundBroadcastService);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(liveRoundBroadcastService).broadcastRoundAssignments(10L, 1);
        verify(liveRoundBroadcastService).broadcastIdle(10L, 1, 60);
    }

    @Test
    void completeRoundChangesStatusWithoutBroadcastingAssignments() {
        LiveRound round = LiveRound.create(10L, 1);
        round.start(Instant.parse("2026-10-06T09:00:00Z"));
        Instant endedAt = Instant.parse("2026-10-06T09:03:00Z");

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));

        LiveRoundStateResult result = service.completeRound(10L, 1, endedAt);

        assertThat(round.getStatus()).isEqualTo(LiveRoundStatus.COMPLETED);
        assertThat(round.getEndedAt()).isEqualTo(endedAt);
        assertThat(result.status()).isEqualTo(LiveRoundStatus.COMPLETED);
        assertThat(result.assignmentBroadcasted()).isFalse();

        verify(liveRoundBroadcastService, never()).broadcastRoundAssignments(10L, 1);
    }

    @Test
    void completeAndPrepareNextRoundCompletesCurrentAndReturnsBreakWhenNextRoundExists() {
        LiveRound currentRound = LiveRound.create(10L, 1);
        currentRound.start(Instant.parse("2026-10-06T09:00:00Z"));
        LiveRound nextRound = LiveRound.create(10L, 2);

        Instant endedAt = Instant.parse("2026-10-06T09:03:00Z");

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(currentRound));
        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 2))
                .thenReturn(Optional.of(nextRound));

        LiveRoundAdvanceResult result = service.completeAndPrepareNextRound(10L, 1, endedAt);

        assertThat(currentRound.getStatus()).isEqualTo(LiveRoundStatus.COMPLETED);
        assertThat(nextRound.getStatus()).isEqualTo(LiveRoundStatus.PENDING);
        assertThat(result.nextRoundExists()).isTrue();
        assertThat(result.nextRoundNumber()).isEqualTo(2);
        assertThat(result.phase()).isEqualTo(LiveRoundPhase.BREAK);
        assertThat(result.breakSeconds()).isEqualTo(60);

        verify(liveRoundBroadcastService, never()).broadcastRoundAssignments(10L, 2);
    }

    @Test
    void completeAndPrepareNextRoundReturnsFinishedWhenNextRoundDoesNotExist() {
        LiveRound currentRound = LiveRound.create(10L, 1);
        currentRound.start(Instant.parse("2026-10-06T09:00:00Z"));

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(currentRound));
        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 2))
                .thenReturn(Optional.empty());

        LiveRoundAdvanceResult result = service.completeAndPrepareNextRound(
                10L,
                1,
                Instant.parse("2026-10-06T09:03:00Z")
        );

        assertThat(currentRound.getStatus()).isEqualTo(LiveRoundStatus.COMPLETED);
        assertThat(result.nextRoundExists()).isFalse();
        assertThat(result.nextRoundNumber()).isNull();
        assertThat(result.phase()).isEqualTo(LiveRoundPhase.FINISHED);
        assertThat(result.breakSeconds()).isZero();

        verify(liveRoundBroadcastService, never()).broadcastRoundAssignments(10L, 2);
    }

    @Test
    void startRoundRejectsMissingRound() {
        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startRound(
                10L,
                1,
                Instant.parse("2026-10-06T09:00:00Z")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("round not found");
    }
}
