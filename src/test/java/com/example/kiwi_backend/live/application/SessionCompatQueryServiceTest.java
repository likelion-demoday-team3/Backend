package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.entity.SessionCompatibility;
import com.example.kiwi_backend.live.domain.repository.SessionCompatibilityRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionCompatQueryServiceTest {

    private final SessionCompatibilityRepository repository = mock(SessionCompatibilityRepository.class);
    private final SessionCompatQueryService service = new SessionCompatQueryService(repository);

    @Test
    void findsPairRegardlessOfMemberOrder() {
        when(repository.findBySessionIdAndMemberAIdAndMemberBId(4L, 10L, 20L))
                .thenReturn(Optional.of(SessionCompatibility.create(4L, 10L, 20L, 80, "대화 관심사가 비슷합니다.")));

        var summary = service.getCompatibility(4L, 20L, 10L);

        assertThat(summary.score()).isEqualTo(80);
        assertThat(summary.reason()).contains("관심사");
    }

    @Test
    void failsClearlyWhenResultIsNotReady() {
        when(repository.findBySessionIdAndMemberAIdAndMemberBId(4L, 10L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCompatibility(4L, 10L, 20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("궁합 결과가 아직 준비되지 않았습니다");
    }
}
