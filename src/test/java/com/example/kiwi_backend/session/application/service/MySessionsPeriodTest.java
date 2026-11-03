package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.security.JwtProvider;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.session.api.dto.MySessionResponse;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 마이페이지 예약 확인의 기간 선택: 세션 시작 시각 기준으로 from~to(양 끝 포함)만 준다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MySessionsPeriodTest {

    @Autowired SessionReadService sessionReads;
    @Autowired DatingSessionRepository sessions;
    @Autowired SessionParticipantRepository participants;
    @Autowired JwtProvider jwtProvider;
    @Autowired MockMvc mockMvc;

    @Test
    void filtersBySessionStartTimeInclusive() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long memberId = newMemberId();
        Long old = participate(memberId, now.minus(Duration.ofDays(200)));
        Long recent = participate(memberId, now.minus(Duration.ofDays(10)));
        Long upcoming = participate(memberId, now.plus(Duration.ofDays(1)));
        Instant recentStart = sessions.findById(recent).orElseThrow().getStartTime();

        assertThat(ids(sessionReads.mySessions(memberId))).containsExactlyInAnyOrder(old, recent, upcoming);
        assertThat(ids(sessionReads.mySessions(memberId, now.minus(Duration.ofDays(180)), null)))
                .containsExactlyInAnyOrder(recent, upcoming);
        assertThat(ids(sessionReads.mySessions(memberId, recentStart, recentStart))).containsExactly(recent);
        assertThat(ids(sessionReads.mySessions(memberId, null, now))).containsExactlyInAnyOrder(old, recent);
    }

    @Test
    void rejectsFromAfterTo() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> sessionReads.mySessions(1L, now, now.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endpointParsesIsoFromAndTo() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long memberId = newMemberId();
        participate(memberId, now.minus(Duration.ofDays(200)));
        Long recent = participate(memberId, now.minus(Duration.ofDays(10)));
        String token = jwtProvider.createAccessToken(memberId, "USER");

        mockMvc.perform(get("/sessions/my")
                        .header("Authorization", "Bearer " + token)
                        .param("from", now.minus(Duration.ofDays(30)).toString())
                        .param("to", now.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sessionId").value(recent));
    }

    private Long participate(long memberId, Instant startTime) {
        Instant createdAt = startTime.minus(Duration.ofDays(2));
        DatingSession session = DatingSession.create(startTime, 3, 180, 120, 300, 0, 0, createdAt);
        session.openRecruiting(createdAt);
        Long sessionId = sessions.saveAndFlush(session).getId();
        participants.saveAndFlush(
                SessionParticipant.apply(sessionId, memberId, Gender.MALE, "alias", createdAt.plusSeconds(60)));
        return sessionId;
    }

    private static long newMemberId() {
        return ThreadLocalRandom.current().nextLong(500_000_000L, 900_000_000L);
    }

    private static List<Long> ids(List<MySessionResponse> items) {
        return items.stream().map(MySessionResponse::sessionId).toList();
    }
}
