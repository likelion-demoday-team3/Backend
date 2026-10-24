package com.example.kiwi_backend.member.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이름 수정. 이름은 비울 수 없으므로 삭제 요청은 받지 않는다 */
public record NameRequest(
        @NotBlank @Size(max = 100) String name
) {
}
