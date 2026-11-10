package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.contract.command.MemberRegistrar;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.RegisterMemberCommand;
import com.example.kiwi_backend.contract.query.ProfileQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 가입할 때 정한 학교 지역이 프로필 카드에 실린다 */
@SpringBootTest
@ActiveProfiles("test")
class ProfileRegionTest {

    @Autowired MemberRegistrar memberRegistrar;
    @Autowired ProfileQueryService profiles;
    @Autowired TransactionTemplate tx;

    @Test
    void regionIsStoredOnProfileCard() {
        Long busan = register("부산");
        Long unknown = register(null);

        assertThat(profiles.getCard(busan).region()).isEqualTo("부산");
        assertThat(profiles.getCard(unknown).region()).isNull();
    }

    private Long register(String region) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return tx.execute(status -> memberRegistrar.register(new RegisterMemberCommand(
                "region-" + unique + "@pusan.ac.kr", "지역", "hash", "부산대학교", Gender.MALE,
                LocalDate.of(2003, 1, 1), null, "region_" + unique, Instant.now(), region, null, null)));
    }
}
