package com.example.kiwi_backend.auth.api.dto;

import com.example.kiwi_backend.contract.dto.Gender;
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
        String instagramId
) {
}
