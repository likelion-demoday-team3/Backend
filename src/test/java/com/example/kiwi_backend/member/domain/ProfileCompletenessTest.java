package com.example.kiwi_backend.member.domain;

import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.entity.Profile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 세션 신청 조건: 문장 항목 4개를 모두 쓰고 관심사를 하나 이상 넣어야 카드 작성 완료 */
class ProfileCompletenessTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @Test
    void newProfileIsNotComplete() {
        assertThat(profile().isComplete()).isFalse();
    }

    @Test
    void completeWhenAllFourTextsAndAtLeastOneInterest() {
        Profile profile = profile();
        profile.update("웃음이 많은 사람", "맛집 탐방", "카톡 자주", "천천히 알아가는 편");
        profile.replaceInterests(List.of("러닝"));

        assertThat(profile.isComplete()).isTrue();
    }

    @Test
    void missingAnyTextOrAllInterestsIsNotComplete() {
        Profile noInterest = profile();
        noInterest.update("웃음이 많은 사람", "맛집 탐방", "카톡 자주", "천천히 알아가는 편");
        assertThat(noInterest.isComplete()).isFalse();

        Profile blankText = profile();
        blankText.update("웃음이 많은 사람", "맛집 탐방", "   ", "천천히 알아가는 편");   // 공백은 빈 값으로 저장
        blankText.replaceInterests(List.of("러닝"));
        assertThat(blankText.isComplete()).isFalse();

        Profile clearedInterests = profile();
        clearedInterests.update("웃음이 많은 사람", "맛집 탐방", "카톡 자주", "천천히 알아가는 편");
        clearedInterests.replaceInterests(List.of("러닝"));
        clearedInterests.replaceInterests(List.of());
        assertThat(clearedInterests.isComplete()).isFalse();
    }

    private static Profile profile() {
        Member member = Member.register("a@yonsei.ac.kr", "테스트", "hash", "연세대학교", Gender.MALE,
                LocalDate.of(2003, 1, 1), null, "kiwi_test", Instant.parse("2026-10-30T00:00:00Z"), TODAY);
        return Profile.create(member);
    }
}
