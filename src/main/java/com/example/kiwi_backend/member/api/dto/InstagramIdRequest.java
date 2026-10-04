package com.example.kiwi_backend.member.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 인스타그램 아이디 수정. 삭제는 DELETE 요청으로 따로 받으므로 빈 값을 막는다 */
public record InstagramIdRequest(
        @NotBlank String instagramId
) {
}
