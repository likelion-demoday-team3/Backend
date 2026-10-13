package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionLivePhaseCommand;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveVotingStartServiceTest {

    private final ObjectProvider<SessionLivePhaseCommand> phaseCommandProvider = mock(ObjectProvider.class);
    private final ObjectProvider<SessionQueryService> sessionQueryServiceProvider = mock(ObjectProvider.class);
    private final SessionLivePhaseCommand phaseCommand = mock(SessionLivePhaseCommand.class);
    private final SessionQueryService sessionQueryService = mock(SessionQueryService.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final LiveVotingStartService service = new LiveVotingStartService(
            phaseCommandProvider, sessionQueryServiceProvider, messagingTemplate);

    @Test
    void startsVotingAndNotifiesOnlyAttendees() {
        Instant startedAt = Instant.parse("2026-10-09T12:30:00Z");
        Instant endsAt = startedAt.plusSeconds(120);
        when(phaseCommandProvider.getIfAvailable()).thenReturn(phaseCommand);
        when(sessionQueryServiceProvider.getIfAvailable()).thenReturn(sessionQueryService);
        when(sessionQueryService.getLiveParticipants(10L)).thenReturn(List.of(
                new LiveParticipant(101L, Gender.MALE, "test-alias"),
                new LiveParticipant(201L, Gender.FEMALE, "test-alias")));
        when(phaseCommand.startVoting(10L, startedAt)).thenReturn(endsAt);

        service.startVoting(10L, startedAt);

        LiveVotingStartedMessage expected = new LiveVotingStartedMessage(10L, endsAt);
        verify(messagingTemplate).convertAndSendToUser("101", LiveVotingStartService.VOTING_STARTED_DESTINATION, expected);
        verify(messagingTemplate).convertAndSendToUser("201", LiveVotingStartService.VOTING_STARTED_DESTINATION, expected);
    }

    @Test
    void doesNotAnnounceVotingWithoutSessionImplementation() {
        assertThatThrownBy(() -> service.startVoting(10L, Instant.parse("2026-10-09T12:30:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("세션 투표 전환 구현체가 필요합니다");
        verifyNoInteractions(messagingTemplate);
    }
}
