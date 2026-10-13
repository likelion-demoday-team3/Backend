package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionLiveAccess;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveHeartPhaseServiceTest {
    @Test
    void requiresEveryRoundToBeComplete() {
        var access = mock(SessionLiveAccess.class);
        var rounds = mock(LiveRoundRepository.class);
        var events = mock(ApplicationEventPublisher.class);
        when(access.lock(10L)).thenReturn(new LiveSessionContext(10L, LiveSessionContext.Phase.IN_ROUND,
                null, 2, List.of()));
        when(rounds.findAllBySessionIdOrderByRoundNumberAsc(10L)).thenReturn(List.of(LiveRound.create(10L, 1)));
        var service = new LiveHeartPhaseService(access, rounds, events);
        assertThatThrownBy(() -> service.startHearts(10L, Instant.now())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(events);
    }
}
