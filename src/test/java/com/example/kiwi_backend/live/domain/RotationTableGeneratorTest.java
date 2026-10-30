package com.example.kiwi_backend.live.domain;

import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RotationTableGeneratorTest {

    private final RotationTableGenerator generator = new RotationTableGenerator();

    @Test
    void generateCreatesAllPairsWithoutDuplicatesWhenCountsAreBalanced() {
        List<LiveParticipant> participants = List.of(
                new LiveParticipant(1L, Gender.MALE, "test-alias"),
                new LiveParticipant(2L, Gender.MALE, "test-alias"),
                new LiveParticipant(3L, Gender.MALE, "test-alias"),
                new LiveParticipant(10L, Gender.FEMALE, "test-alias"),
                new LiveParticipant(11L, Gender.FEMALE, "test-alias"),
                new LiveParticipant(12L, Gender.FEMALE, "test-alias")
        );

        RotationTable table = generator.generate(1L, participants);

        assertThat(table.rounds()).hasSize(3);
        assertThat(table.rounds()).allSatisfy(round -> {
            assertThat(round.pairs()).hasSize(3);
            assertThat(round.idleMemberIds()).isEmpty();
            assertThat(round.pairs())
                    .extracting(MatchPair::tableNo)
                    .containsExactly(1, 2, 3);
        });

        Set<String> uniquePairs = new HashSet<>();
        table.rounds().forEach(round -> round.pairs().forEach(pair ->
                uniquePairs.add(pair.maleMemberId() + "-" + pair.femaleMemberId())
        ));

        assertThat(uniquePairs).hasSize(9);
    }

    @Test
    void generateTracksIdleParticipantsWhenCountsAreUnbalanced() {
        int maleCount = 4;
        int femaleCount = 3;
        int expectedPairCount = maleCount * femaleCount;

        List<LiveParticipant> participants = List.of(
                new LiveParticipant(1L, Gender.MALE, "test-alias"),
                new LiveParticipant(2L, Gender.MALE, "test-alias"),
                new LiveParticipant(3L, Gender.MALE, "test-alias"),
                new LiveParticipant(4L, Gender.MALE, "test-alias"),
                new LiveParticipant(10L, Gender.FEMALE, "test-alias"),
                new LiveParticipant(11L, Gender.FEMALE, "test-alias"),
                new LiveParticipant(12L, Gender.FEMALE, "test-alias")
        );

        RotationTable table = generator.generate(1L, participants);

        assertThat(table.rounds()).hasSize(4);
        assertThat(table.rounds()).allSatisfy(round -> {
            assertThat(round.pairs()).hasSize(3);
            assertThat(round.idleMemberIds()).hasSize(1);
            assertThat(round.pairs())
                    .extracting(MatchPair::tableNo)
                    .containsExactly(1, 2, 3);
        });

        Set<String> uniquePairs = new HashSet<>();
        table.rounds().forEach(round -> round.pairs().forEach(pair ->
                uniquePairs.add(pair.maleMemberId() + "-" + pair.femaleMemberId())
        ));

        assertThat(uniquePairs).hasSize(expectedPairCount);
    }

    @Test
    void generateRejectsSessionWhenParticipantCountIsLessThanFive() {
        List<LiveParticipant> participants = List.of(
                new LiveParticipant(1L, Gender.MALE, "test-alias"),
                new LiveParticipant(2L, Gender.MALE, "test-alias")
        );

        assertThatThrownBy(() -> generator.generate(1L, participants))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("at least 5 participants are required");
    }

    @Test
    void generateRejectsSessionWithoutBothGenders() {
        List<LiveParticipant> participants = List.of(
                new LiveParticipant(1L, Gender.MALE, "test-alias"),
                new LiveParticipant(2L, Gender.MALE, "test-alias"),
                new LiveParticipant(3L, Gender.MALE, "test-alias"),
                new LiveParticipant(4L, Gender.MALE, "test-alias"),
                new LiveParticipant(5L, Gender.MALE, "test-alias")
        );

        assertThatThrownBy(() -> generator.generate(1L, participants))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("both male and female participants are required");
    }

    @Test
    void generateRejectsSessionWhenGenderCountDifferenceIsGreaterThanOne() {
        List<LiveParticipant> participants = List.of(
                new LiveParticipant(1L, Gender.MALE, "test-alias"),
                new LiveParticipant(2L, Gender.MALE, "test-alias"),
                new LiveParticipant(3L, Gender.MALE, "test-alias"),
                new LiveParticipant(4L, Gender.MALE, "test-alias"),
                new LiveParticipant(10L, Gender.FEMALE, "test-alias")
        );

        assertThatThrownBy(() -> generator.generate(1L, participants))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("gender count difference must be 1 or less");
    }

    @Test
    void generateRejectsDuplicateParticipants() {
        List<LiveParticipant> participants = List.of(
                new LiveParticipant(1L, Gender.MALE, "test-alias"),
                new LiveParticipant(1L, Gender.MALE, "test-alias"),
                new LiveParticipant(2L, Gender.MALE, "test-alias"),
                new LiveParticipant(10L, Gender.FEMALE, "test-alias"),
                new LiveParticipant(11L, Gender.FEMALE, "test-alias")
        );

        assertThatThrownBy(() -> generator.generate(1L, participants))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate participants are not allowed");
    }
}
