package com.example.kiwi_backend.member.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 전화번호 수정. 삭제는 DELETE 요청으로 따로 받으므로 빈 값을 막는다 */
public record PhoneNumberRequest(
        @NotBlank String phoneNumber
) {
}
