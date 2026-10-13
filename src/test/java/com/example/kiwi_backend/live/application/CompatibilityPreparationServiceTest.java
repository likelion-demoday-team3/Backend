package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.dto.PartnerProfileCard;
import com.example.kiwi_backend.contract.query.ProfileQueryService;
import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.entity.SessionCompatibility;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import com.example.kiwi_backend.live.domain.repository.SessionCompatibilityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CompatibilityPreparationServiceTest {

    @Test
    void sendsOnlyProfileCardFieldsToLinerAndPersistsValidatedPairResult() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var compatRepository = mock(SessionCompatibilityRepository.class);
        var roundRepository = mock(LiveRoundRepository.class);
        var roomRepository = mock(LiveRoomRepository.class);
        var profiles = mock(ProfileQueryService.class);
        var round = LiveRound.create(7L, 1);
        ReflectionTestUtils.setField(round, "id", 3L);
        var room = LiveRoom.create(round, 1, 11L, 21L);
        when(roundRepository.findAllBySessionIdOrderByRoundNumberAsc(7L)).thenReturn(List.of(round));
        when(roomRepository.findAllByRoundIdOrderByTableNoAsc(3L)).thenReturn(List.of(room));
        when(compatRepository.findAllBySessionId(7L)).thenReturn(List.of());
        when(profiles.getCards(argThat(ids -> ids.containsAll(List.of(11L, 21L)))))
                .thenReturn(Map.of(
                        11L, new PartnerProfileCard(11L, "연세대학교", "다정한 사람", "산책", "자주 연락", "표현형", List.of("영화")),
                        21L, new PartnerProfileCard(21L, "서강대학교", "유머 있는 사람", "전시", "필요할 때", "대화형", List.of("영화"))));
        server.expect(requestTo("https://platform.liner.com/api/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer liner-test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("idealType")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("memberId"))))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"results\\":[{\\"index\\":0,\\"score\\":81,\\"reason\\":\\"관심사가 같아 대화가 이어지기 좋습니다.\\"}]}"}}]}
                        """, APPLICATION_JSON));

        var service = new CompatibilityPreparationService(builder, compatRepository, roundRepository,
                roomRepository, profiles, new ObjectMapper(), Clock.systemUTC(), Runnable::run);
        ReflectionTestUtils.setField(service, "apiKey", "liner-test-key");
        ReflectionTestUtils.setField(service, "model", "liner-mark-1.3");

        service.requestPreparation(7L);

        verify(compatRepository).save(argThat(result -> result.getSessionId().equals(7L)
                && result.getMemberAId().equals(11L) && result.getMemberBId().equals(21L)
                && result.getScore() == 81));
        server.verify();
    }
}
