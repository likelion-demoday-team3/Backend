package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.*;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;

class LiveSessionNoticeServiceTest {
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final LiveSessionNoticeService service = new LiveSessionNoticeService(template);

    @BeforeEach
    void begin() { TransactionSynchronizationManager.initSynchronization(); }
    @AfterEach
    void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test
    void notifiesAttendeesOnlyAfterCommit() {
        Instant at = Instant.parse("2026-10-13T12:01:30Z");
        service.startingSoonAfterCommit(10L, List.of(new LiveParticipant(1L, Gender.MALE, "a")), at);
        verifyNoInteractions(template);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(template).convertAndSendToUser("1", "/queue/live/starting-soon",
                new LiveSessionNoticeService.StartingSoonMessage(10L, at));
    }

    @Test
    void rollbackDoesNotAnnounceCancellation() {
        service.cancelledAfterCommit(10L, Set.of(1L));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(template);
    }

    @Test
    void cancellationIsSentAfterCommit() {
        service.cancelledAfterCommit(10L, Set.of(1L));
        verifyNoInteractions(template);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(template).convertAndSendToUser("1", "/queue/live/cancelled",
                new LiveSessionNoticeService.CancelledMessage(10L, "INSUFFICIENT_ATTENDANCE"));
    }
}
