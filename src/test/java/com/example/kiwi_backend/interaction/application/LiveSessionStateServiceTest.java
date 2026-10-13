package com.example.kiwi_backend.interaction.application;

import com.example.kiwi_backend.contract.command.SessionLiveAccess;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.dto.LiveSessionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveSessionStateServiceTest {

    private final SessionLiveAccess access = mock(SessionLiveAccess.class);
    private final LiveSessionStateService service = new LiveSessionStateService(access);

    @Test
    void returnsOnlyLiveStageDeadlineVoteStatusAndAttendeeRoster() {
        var deadline = Instant.parse("2026-10-13T10:02:00Z");
        var roster = List.of(
                new LiveParticipant(10L, Gender.MALE, "StarA"),
                new LiveParticipant(20L, Gender.FEMALE, "StarB")
        );
        when(access.lock(5L)).thenReturn(new LiveSessionContext(5L,
                LiveSessionContext.Phase.VOTING, deadline, 2, roster));
        when(access.hasVoted(5L, 10L)).thenReturn(true);

        var state = service.get(5L, 10L);

        assertThat(state.sessionId()).isEqualTo(5L);
        assertThat(state.phase()).isEqualTo(LiveSessionContext.Phase.VOTING);
        assertThat(state.deadline()).isEqualTo(deadline);
        assertThat(state.voteSubmitted()).isTrue();
        assertThat(state.participants()).containsExactlyElementsOf(roster);
        verify(access).hasVoted(5L, 10L);
    }

    @Test
    void rejectsMembersWhoAreNotAttendees() {
        when(access.lock(5L)).thenReturn(new LiveSessionContext(5L,
                LiveSessionContext.Phase.HEART, null, 2,
                List.of(new LiveParticipant(10L, Gender.MALE, "StarA"))));

        assertThatThrownBy(() -> service.get(5L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Attended participant required");
    }
}
