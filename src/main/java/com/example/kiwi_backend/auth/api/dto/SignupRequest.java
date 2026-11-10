package com.example.kiwi_backend.auth.api.dto;

import com.example.kiwi_backend.contract.dto.Gender;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 가입 요청. 연락처는 둘 중 하나 이상 필수인데, 그 검사와 형식 검사는 Member 엔티티가 한다.
 * 나이 제한(한국 나이 20~28세)도 Member 엔티티가 검사한다.
 */
public record SignupRequest(
        @NotBlank String signupToken, //여기서 이메일을 꺼내서 member의 email로 저장
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotNull Gender gender,
        @NotNull @Past LocalDate birthDate,
        String phoneNumber,
        String instagramId,
        /** [필수] 이용약관 동의 */
        @AssertTrue(message = "이용약관에 동의해주세요") boolean agreeTerms,
        /** [필수] 개인정보 수집·이용 동의 */
        @AssertTrue(message = "개인정보 수집·이용에 동의해주세요") boolean agreePrivacy,
        /** [필수] 매칭·연락처 교환·투표 결과 열람 시 다른 회원에게 개인정보 제공 동의 */
        @AssertTrue(message = "개인정보 제3자 제공에 동의해주세요") boolean agreeThirdParty
) {
}
