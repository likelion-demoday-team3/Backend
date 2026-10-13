package com.example.kiwi_backend.interaction.application;

import com.example.kiwi_backend.contract.command.*;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.interaction.domain.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeartServiceTest {
    private final SessionLiveAccess access = mock(SessionLiveAccess.class);
    private final HeartRepository hearts = mock(HeartRepository.class);
    private final GemWallet wallet = mock(GemWallet.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Instant now = Instant.parse("2026-10-13T12:00:00Z");
    private final HeartService service = new HeartService(access, hearts, wallet,
            Clock.fixed(now, ZoneOffset.UTC), events);
    private final List<LiveParticipant> people = List.of(new LiveParticipant(1L, Gender.MALE, "a"),
            new LiveParticipant(2L, Gender.FEMALE, "b"), new LiveParticipant(3L, Gender.FEMALE, "c"),
            new LiveParticipant(4L, Gender.MALE, "d"));

    @BeforeEach
    void setup() {
        when(access.lock(10L)).thenReturn(new LiveSessionContext(10L, LiveSessionContext.Phase.HEART,
                now.plusSeconds(1), 2, people));
    }

    @Test
    void firstHeartIsFree() {
        assertThat(service.send(10L, 1L, 2L).chargedGem()).isZero();
        verifyNoInteractions(wallet);
        verify(hearts).saveAndFlush(any(Heart.class));
    }

    @Test
    void secondHeartCostsExactlyOneGem() {
        when(hearts.findAllBySessionIdAndSenderId(10L, 1L)).thenReturn(List.of(Heart.create(10L, 1L, 2L, 0)));
        assertThat(service.send(10L, 1L, 3L).chargedGem()).isEqualTo(1);
        var captor = ArgumentCaptor.forClass(GemCommand.class);
        verify(wallet).apply(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(GemTransactionType.HEART_SEND);
        assertThat(captor.getValue().amount()).isEqualTo(1);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("HEART:s10:f1:t3");
    }

    @Test
    void duplicateDoesNotChargeOrPublishAgain() {
        when(hearts.findBySessionIdAndSenderIdAndReceiverId(10L, 1L, 2L))
                .thenReturn(Optional.of(Heart.create(10L, 1L, 2L, 0)));
        assertThat(service.send(10L, 1L, 2L).alreadySent()).isTrue();
        verifyNoInteractions(wallet, events);
        verify(hearts, never()).saveAndFlush(any());
    }

    @Test
    void insufficientBalanceDoesNotSaveOrNotify() {
        when(hearts.findAllBySessionIdAndSenderId(10L, 1L)).thenReturn(List.of(Heart.create(10L, 1L, 2L, 0)));
        when(wallet.apply(any())).thenThrow(new IllegalStateException("insufficient"));
        assertThatThrownBy(() -> service.send(10L, 1L, 3L)).hasMessage("insufficient");
        verify(hearts, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void deadlineIsExclusive() {
        when(access.lock(10L)).thenReturn(new LiveSessionContext(10L, LiveSessionContext.Phase.HEART, now, 2, people));
        assertThatThrownBy(() -> service.send(10L, 1L, 2L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(wallet, events);
    }

    @Test
    void rejectsSelfSameGenderAndOutsider() {
        assertThatThrownBy(() -> service.send(10L, 1L, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(10L, 1L, 4L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(10L, 1L, 999L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(wallet, events);
    }
}
