package com.example.kiwi_backend.session.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 투표 마감 시각은 관리자가 세션을 만들 때 정한 투표 시간(votingDurationSeconds)을 따른다 */
class DatingSessionVotingTest {

    @Test
    void votingEndsAfterConfiguredDuration() {
        Instant now = Instant.parse("2026-11-03T12:00:00Z");
        DatingSession session = DatingSession.create(now.minusSeconds(3600), 3, 600, 180, 180, 0, 0,
                now.minusSeconds(7200));
        session.openRecruiting(now.minusSeconds(7200));
        session.markReady();
        session.startProgress();
        session.markRoundsStarted();
        Instant heartsEndAt = session.startHearts(now.minusSeconds(60));

        Instant votingEndsAt = session.startVoting(heartsEndAt);

        assertThat(votingEndsAt).isEqualTo(heartsEndAt.plusSeconds(180));
        assertThat(session.getPhaseEndsAt()).isEqualTo(votingEndsAt);
        // 두 번 불려도 처음 정한 마감 시각 그대로
        assertThat(session.startVoting(heartsEndAt.plusSeconds(5))).isEqualTo(votingEndsAt);
    }
}
