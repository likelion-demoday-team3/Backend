package com.example.kiwi_backend.auth;

import com.example.kiwi_backend.auth.api.dto.SignupRequest;
import com.example.kiwi_backend.common.security.JwtProvider;
import com.example.kiwi_backend.common.terms.TermsVersion;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.example.kiwi_backend.contract.command.MemberRegistrar;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.RegisterMemberCommand;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 가입할 때 필수 약관 3개에 모두 동의해야 하고, 동의 시각과 약관 버전을 남긴다. 기존 회원은 다시 동의받는다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupConsentTest {

    @Autowired Validator validator;
    @Autowired MemberRegistrar memberRegistrar;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JwtProvider jwtProvider;
    @Autowired MockMvc mockMvc;

    @Test
    void allRequiredConsentsMustBeChecked() {
        assertThat(violations(true, true, true)).isEmpty();
        assertThat(violations(false, true, true)).containsExactly("이용약관에 동의해주세요");
        assertThat(violations(true, false, true)).containsExactly("개인정보 수집·이용에 동의해주세요");
        assertThat(violations(true, true, false)).containsExactly("개인정보 제3자 제공에 동의해주세요");
    }

    @Test
    void agreementTimeAndVersionAreStored() {
        Instant agreedAt = Instant.parse("2026-11-10T03:00:00Z");
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Long memberId = tx.execute(status -> memberRegistrar.register(new RegisterMemberCommand(
                "terms-" + unique + "@yonsei.ac.kr", "동의", "hash", "연세대학교", Gender.FEMALE,
                LocalDate.of(2003, 1, 1), null, "terms_" + unique, Instant.now(), "서울",
                agreedAt, "2026-10-31")));

        var member = members.findById(memberId).orElseThrow();
        assertThat(member.getTermsAgreedAt()).isEqualTo(agreedAt);
        assertThat(member.getTermsVersion()).isEqualTo("2026-10-31");
    }

    @Test
    void existingMemberWithoutAgreementIsAskedAgainAndCanAgree() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        // 동의 기능 이전에 가입한 회원 (동의 기록 없음)
        Long memberId = tx.execute(status -> memberRegistrar.register(new RegisterMemberCommand(
                "old-" + unique + "@yonsei.ac.kr", "기존", "hash", "연세대학교", Gender.MALE,
                LocalDate.of(2003, 1, 1), null, "old_" + unique, Instant.now(), "서울", null, null)));
        String token = "Bearer " + jwtProvider.createAccessToken(memberId, "MEMBER");

        mockMvc.perform(get("/members/my").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.termsAgreementRequired").value(true));

        // 필수 동의가 하나라도 빠지면 받지 않는다
        mockMvc.perform(post("/members/my/terms-agreement").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreeTerms\":true,\"agreePrivacy\":true}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/members/my/terms-agreement").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreeTerms\":true,\"agreePrivacy\":true,\"agreeThirdParty\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/members/my").header("Authorization", token))
                .andExpect(jsonPath("$.data.termsAgreementRequired").value(false))
                .andExpect(jsonPath("$.data.termsVersion").value(TermsVersion.CURRENT));
    }

    private java.util.List<String> violations(boolean terms, boolean privacy, boolean thirdParty) {
        SignupRequest request = new SignupRequest("token", "이름", "password1234", Gender.MALE,
                LocalDate.of(2003, 1, 1), null, "kiwi_test", terms, privacy, thirdParty);
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
    }
}
