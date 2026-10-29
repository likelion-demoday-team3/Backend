package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.dto.PartnerProfileCard;
import com.example.kiwi_backend.contract.dto.CompatibilitySummary;
import com.example.kiwi_backend.contract.query.CompatQueryService;
import com.example.kiwi_backend.contract.query.ProfileQueryService;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.entity.LiveRoundIdleParticipant;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundIdleParticipantRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveRoundBroadcastServiceTest {

    private final LiveRoundRepository liveRoundRepository = mock(LiveRoundRepository.class);
    private final LiveRoomRepository liveRoomRepository = mock(LiveRoomRepository.class);
    private final LiveRoundIdleParticipantRepository liveRoundIdleParticipantRepository =
            mock(LiveRoundIdleParticipantRepository.class);
    private final ProfileQueryService profileQueryService = mock(ProfileQueryService.class);
    private final ObjectProvider<CompatQueryService> compatQueryServiceProvider = mock(ObjectProvider.class);
    private final CompatQueryService compatQueryService = mock(CompatQueryService.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final ObjectProvider<SessionQueryService> sessionQueryServiceProvider = mock(ObjectProvider.class);
    private final SessionQueryService sessionQueryService = mock(SessionQueryService.class);

    private final LiveRoundBroadcastService service = new LiveRoundBroadcastService(
            liveRoundRepository,
            liveRoomRepository,
            liveRoundIdleParticipantRepository,
            profileQueryService,
            compatQueryServiceProvider,
            messagingTemplate,
            sessionQueryServiceProvider
    );

    @Test
    void broadcastRoundAssignmentsSendsMessageToBothParticipantsInEachRoom() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom firstRoom = persistedRoom(100L, round, 1, 101L, 201L);
        LiveRoom secondRoom = persistedRoom(101L, round, 2, 102L, 202L);

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));
        when(liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(1L))
                .thenReturn(List.of(firstRoom, secondRoom));
        when(profileQueryService.getCards(Set.of(101L, 201L, 102L, 202L))).thenReturn(Map.of(
                101L, card(101L), 201L, card(201L), 102L, card(102L), 202L, card(202L)
        ));
        when(compatQueryServiceProvider.getIfAvailable()).thenReturn(compatQueryService);
        when(compatQueryService.getCompatibility(10L, 101L, 201L))
                .thenReturn(new CompatibilitySummary(82, "대화 취향이 비슷합니다"));
        when(compatQueryService.getCompatibility(10L, 102L, 202L))
                .thenReturn(new CompatibilitySummary(74, "관심사가 겹칩니다"));
        when(sessionQueryServiceProvider.getIfAvailable()).thenReturn(sessionQueryService);
        when(sessionQueryService.getLiveParticipants(10L)).thenReturn(List.of(
                new LiveParticipant(101L, Gender.MALE, "졸린 수달"),
                new LiveParticipant(201L, Gender.FEMALE, "배고픈 판다"),
                new LiveParticipant(102L, Gender.MALE, "신난 펭귄"),
                new LiveParticipant(202L, Gender.FEMALE, "용감한 다람쥐")
        ));

        LiveRoundBroadcastResult result = service.broadcastRoundAssignments(10L, 1);

        assertThat(result.sessionId()).isEqualTo(10L);
        assertThat(result.roundNumber()).isEqualTo(1);
        assertThat(result.roomCount()).isEqualTo(2);
        assertThat(result.recipientCount()).isEqualTo(4);

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LiveRoundAssignmentMessage> messageCaptor =
                ArgumentCaptor.forClass(LiveRoundAssignmentMessage.class);

        verify(messagingTemplate, org.mockito.Mockito.times(4)).convertAndSendToUser(
                userCaptor.capture(),
                destinationCaptor.capture(),
                messageCaptor.capture()
        );

        assertThat(userCaptor.getAllValues()).containsExactly("101", "201", "102", "202");
        assertThat(destinationCaptor.getAllValues())
                .containsOnly(LiveRoundBroadcastService.ROUND_ASSIGNED_DESTINATION);
        assertThat(messageCaptor.getAllValues())
                .extracting(LiveRoundAssignmentMessage::partnerMemberId)
                .containsExactly(201L, 101L, 202L, 102L);
        assertThat(messageCaptor.getAllValues())
                .extracting(message -> message.partnerProfileCard().memberId())
                .containsExactly(201L, 101L, 202L, 102L);
        assertThat(messageCaptor.getAllValues())
                .extracting(LiveRoundAssignmentMessage::partnerAlias)
                .containsExactly("배고픈 판다", "졸린 수달", "용감한 다람쥐", "신난 펭귄");
        assertThat(messageCaptor.getAllValues())
                .extracting(message -> message.compatibility().score())
                .containsExactly(82, 82, 74, 74);
    }

    @Test
    void callTestBroadcastUsesPlaceholderCompatibilityWithoutQueryingLinerResults() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1)).thenReturn(Optional.of(round));
        when(liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(1L)).thenReturn(List.of(room));
        when(profileQueryService.getCards(Set.of(101L, 201L))).thenReturn(Map.of(
                101L, card(101L), 201L, card(201L)));
        when(sessionQueryServiceProvider.getIfAvailable()).thenReturn(sessionQueryService);
        when(sessionQueryService.isCallTestSession(10L)).thenReturn(true);
        when(sessionQueryService.getLiveParticipants(10L)).thenReturn(List.of(
                new LiveParticipant(101L, Gender.MALE, "host"),
                new LiveParticipant(201L, Gender.FEMALE, "partner")));

        service.broadcastRoundAssignments(10L, 1);

        ArgumentCaptor<LiveRoundAssignmentMessage> messageCaptor =
                ArgumentCaptor.forClass(LiveRoundAssignmentMessage.class);
        verify(messagingTemplate, org.mockito.Mockito.times(2)).convertAndSendToUser(
                anyString(), anyString(), messageCaptor.capture());
        assertThat(messageCaptor.getAllValues())
                .extracting(LiveRoundAssignmentMessage::compatibility)
                .containsOnly(new CompatibilitySummary(0, "통화 연결 테스트"));
        verifyNoInteractions(compatQueryServiceProvider, compatQueryService);
    }

    @Test
    void broadcastRoundAssignmentsRejectsRoundWithoutRooms() {
        LiveRound round = startedRound(10L, 1L, 1);

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));
        when(liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(1L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.broadcastRoundAssignments(10L, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("round has no rooms");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void broadcastRoundAssignmentsRejectsPendingRound() {
        LiveRound round = persistedRound(10L, 1L, 1);

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.broadcastRoundAssignments(10L, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("in-progress round만 배정 알림을 보낼 수 있습니다");

        verifyNoInteractions(liveRoomRepository, messagingTemplate);
    }

    @Test
    void broadcastRoundAssignmentsRejectsMissingCardBeforeSendingAnything() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1)).thenReturn(Optional.of(round));
        when(liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(1L)).thenReturn(List.of(room));
        when(profileQueryService.getCards(Set.of(101L, 201L))).thenReturn(Map.of(101L, card(101L)));

        assertThatThrownBy(() -> service.broadcastRoundAssignments(10L, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("라운드 참가자 프로필 카드가 없습니다");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void broadcastRoundAssignmentsRejectsMissingCompatibilityBeforeSendingAnything() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1)).thenReturn(Optional.of(round));
        when(liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(1L)).thenReturn(List.of(room));
        when(profileQueryService.getCards(Set.of(101L, 201L)))
                .thenReturn(Map.of(101L, card(101L), 201L, card(201L)));
        when(compatQueryServiceProvider.getIfAvailable()).thenReturn(compatQueryService);

        assertThatThrownBy(() -> service.broadcastRoundAssignments(10L, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("라운드 참가자 궁합 결과가 없습니다");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void broadcastRoundAssignmentsRejectsMissingCompatImplementation() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1)).thenReturn(Optional.of(round));
        when(liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(1L)).thenReturn(List.of(room));
        when(profileQueryService.getCards(Set.of(101L, 201L)))
                .thenReturn(Map.of(101L, card(101L), 201L, card(201L)));

        assertThatThrownBy(() -> service.broadcastRoundAssignments(10L, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CompatQueryService 구현체가 필요합니다");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void broadcastIdleSendsMessageToIdleParticipants() {
        LiveRound round = startedRound(10L, 1L, 1);

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));
        when(liveRoundIdleParticipantRepository.findAllByRoundIdOrderByMemberIdAsc(1L))
                .thenReturn(List.of(
                        LiveRoundIdleParticipant.create(round, 301L),
                        LiveRoundIdleParticipant.create(round, 302L)
                ));

        service.broadcastIdle(10L, 1, 60);

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LiveRoundIdleMessage> messageCaptor =
                ArgumentCaptor.forClass(LiveRoundIdleMessage.class);

        verify(messagingTemplate, org.mockito.Mockito.times(2)).convertAndSendToUser(
                userCaptor.capture(),
                destinationCaptor.capture(),
                messageCaptor.capture()
        );

        assertThat(userCaptor.getAllValues()).containsExactly("301", "302");
        assertThat(destinationCaptor.getAllValues())
                .containsOnly(LiveRoundBroadcastService.ROUND_IDLE_DESTINATION);
        assertThat(messageCaptor.getAllValues())
                .extracting(LiveRoundIdleMessage::breakSeconds)
                .containsExactly(60, 60);
    }

    @Test
    void broadcastIdleRejectsPendingRound() {
        LiveRound round = persistedRound(10L, 1L, 1);

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.broadcastIdle(10L, 1, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("in-progress round만 대기 알림을 보낼 수 있습니다");

        verifyNoInteractions(messagingTemplate);
    }

    private LiveRound persistedRound(Long sessionId, Long roundId, int roundNumber) {
        LiveRound round = LiveRound.create(sessionId, roundNumber);
        ReflectionTestUtils.setField(round, "id", roundId);
        return round;
    }

    private LiveRound startedRound(Long sessionId, Long roundId, int roundNumber) {
        LiveRound round = persistedRound(sessionId, roundId, roundNumber);
        round.start(Instant.parse("2026-10-05T09:00:00Z"));
        return round;
    }

    private LiveRoom persistedRoom(Long roomId, LiveRound round, int tableNo, Long maleMemberId, Long femaleMemberId) {
        LiveRoom room = LiveRoom.create(round, tableNo, maleMemberId, femaleMemberId);
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    private PartnerProfileCard card(Long memberId) {
        return new PartnerProfileCard(memberId, "학교", "이상형", "데이트 스타일", "연락 스타일", "연애 성향", List.of("영화"));
    }
}
