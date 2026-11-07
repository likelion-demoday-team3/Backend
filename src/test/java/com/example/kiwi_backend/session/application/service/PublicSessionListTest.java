package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.security.JwtProvider;
import com.example.kiwi_backend.session.api.dto.SessionSummaryResponse;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 홈 화면 세션 목록은 로그인 전에도 보인다. 상세·내 세션은 여전히 로그인이 필요하다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicSessionListTest {

    @Autowired SessionReadService sessionReads;
    @Autowired DatingSessionRepository sessions;
    @Autowired JwtProvider jwtProvider;
    @Autowired MockMvc mockMvc;

    @Test
    void anonymousVisitorSeesOpenSessionsButNotCallTests() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Long open = openSession(now.plus(Duration.ofDays(3)));
        long host = newMemberId();
        Long callTest = sessions.saveAndFlush(DatingSession.createCallTest(
                now.plus(Duration.ofDays(3)), host, host + 1, host + 2, now)).getId();

        assertThat(ids(sessionReads.listUpcoming(null))).contains(open).doesNotContain(callTest);
    }

    @Test
    void listIsPublicButOtherSessionEndpointsNeedLogin() throws Exception {
        Long open = openSession(Instant.now().plus(Duration.ofDays(3)));

        mockMvc.perform(get("/sessions")).andExpect(status().isOk());
        // 만료되거나 잘못된 토큰이어도 목록은 보인다
        mockMvc.perform(get("/sessions").header("Authorization", "Bearer broken-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/sessions/my")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/sessions/" + open)).andExpect(status().isUnauthorized());

        String token = jwtProvider.createAccessToken(newMemberId(), "USER");
        mockMvc.perform(get("/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private Long openSession(Instant startTime) {
        Instant createdAt = Instant.now().minus(Duration.ofDays(1));
        DatingSession session = DatingSession.create(startTime, 3, 180, 120, 300, 0, 0, createdAt);
        session.openRecruiting(createdAt);
        return sessions.saveAndFlush(session).getId();
    }

    private static long newMemberId() {
        return ThreadLocalRandom.current().nextLong(500_000_000L, 900_000_000L);
    }

    private static List<Long> ids(List<SessionSummaryResponse> items) {
        return items.stream().map(SessionSummaryResponse::sessionId).toList();
    }
}
