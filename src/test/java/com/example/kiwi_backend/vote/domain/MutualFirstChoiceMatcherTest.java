package com.example.kiwi_backend.vote.domain;
import com.example.kiwi_backend.contract.dto.MatchedPair;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class MutualFirstChoiceMatcherTest {
    @Test
    void matchesMutualFirstChoicesOnceInNormalizedOrder() {
        assertThat(MutualFirstChoiceMatcher.match(Map.of(1L, 10L, 10L, 1L, 2L, 11L, 11L, 3L)))
                .containsExactly(new MatchedPair(1L, 10L));
    }
    @Test
    void noVotesProducesNoMatches() {
        assertThat(MutualFirstChoiceMatcher.match(Map.of())).isEmpty();
    }
    @Test
    void oneSidedChoiceIsNotMatch() {
        assertThat(MutualFirstChoiceMatcher.match(Map.of(1L, 2L))).isEmpty();
    }
}
