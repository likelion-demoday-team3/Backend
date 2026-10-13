package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.common.realtime.UserMessageEvent;
import com.example.kiwi_backend.contract.command.*;
import com.example.kiwi_backend.contract.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveVotingStartServiceTest {
    private final SessionLiveAccess access = mock(SessionLiveAccess.class);
    private final SessionLivePhaseCommand command = mock(SessionLivePhaseCommand.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final LiveVotingStartService service = new LiveVotingStartService(access, command, events);
    private final Instant now = Instant.parse("2026-10-13T12:30:00Z");

    @Test
    void startsVotingAtHeartDeadline() {
        when(access.lock(10L)).thenReturn(new LiveSessionContext(10L, LiveSessionContext.Phase.HEART, now, 2,
                List.of(new LiveParticipant(1L, Gender.MALE, "a"))));
        when(command.startVoting(10L, now)).thenReturn(now.plusSeconds(120));
        service.startVoting(10L, now);
        verify(events).publishEvent(new UserMessageEvent(1L, "/queue/live/voting-started",
                new LiveVotingStartedMessage(10L, now.plusSeconds(120))));
    }

    @Test
    void rejectsEarlyTransition() {
        when(access.lock(10L)).thenReturn(new LiveSessionContext(10L, LiveSessionContext.Phase.HEART,
                now.plusSeconds(1), 2, List.of()));
        assertThatThrownBy(() -> service.startVoting(10L, now)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(command, events);
    }

    @Test
    void repeatedPollDoesNotAnnounceAgain() {
        when(access.lock(10L)).thenReturn(new LiveSessionContext(10L, LiveSessionContext.Phase.VOTING,
                now.plusSeconds(120), 2, List.of()));
        service.startVoting(10L, now);
        verifyNoInteractions(command, events);
    }
}
