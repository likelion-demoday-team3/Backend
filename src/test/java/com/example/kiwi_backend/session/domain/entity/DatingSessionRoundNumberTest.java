package com.example.kiwi_backend.session.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 세션 상세의 currentRoundNumber는 라운드가 시작될 때마다 올라가고, 새로고침·재입장 때 라운드 번호를 되살리는 데 쓴다 */
class DatingSessionRoundNumberTest {

    private DatingSession inProgressSession() {
        Instant now = Instant.parse("2026-11-03T12:00:00Z");
        DatingSession session = DatingSession.create(now.minusSeconds(3600), 3, 600, 180, 180, 0, 0,
                now.minusSeconds(7200));
        session.openRecruiting(now.minusSeconds(7200));
        session.markReady();
        session.startProgress();
        session.markRoundsStarted();
        return session;
    }

    @Test
    void roundNumberFollowsStartedRounds() {
        DatingSession session = inProgressSession();
        assertThat(session.getCurrentRoundNumber()).isZero();

        session.markRoundInProgress(1);
        session.markRoundInProgress(2);

        assertThat(session.getCurrentRoundNumber()).isEqualTo(2);
    }

    @Test
    void roundNumberNeverGoesBackwards() {
        DatingSession session = inProgressSession();
        session.markRoundInProgress(3);

        // 같은 라운드나 앞 라운드로 다시 불려도 그대로 둔다
        session.markRoundInProgress(3);
        session.markRoundInProgress(1);

        assertThat(session.getCurrentRoundNumber()).isEqualTo(3);
    }

    @Test
    void rejectsNonPositiveRoundNumber() {
        DatingSession session = inProgressSession();

        assertThatThrownBy(() -> session.markRoundInProgress(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
