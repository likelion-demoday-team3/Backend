package com.example.kiwi_backend.live.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionCompatibilityTest {

    @Test
    void storesPairInCanonicalMemberOrder() {
        var compatibility = SessionCompatibility.create(5L, 20L, 10L, 72, "관심사가 비슷합니다.");
        assertThat(compatibility.getMemberAId()).isEqualTo(10L);
        assertThat(compatibility.getMemberBId()).isEqualTo(20L);
    }

    @Test
    void rejectsInvalidAiOutput() {
        assertThatThrownBy(() -> SessionCompatibility.create(5L, 10L, 20L, 101, "사유"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SessionCompatibility.create(5L, 10L, 20L, 70, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
