package com.example.kiwi_backend.member.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 프로필 카드 수정. 길이 제한은 엔티티에도 있지만,
 * 여기서 먼저 걸러서 사용자에게 어느 칸이 문제인지 바로 알려준다.
 */
public record ProfileUpdateRequest(
        @Size(max = 200) String idealType,
        @Size(max = 200) String datingStyle,
        @Size(max = 200) String contactStyle,
        @Size(max = 200) String loveStyle,
        @Size(max = 5) List<@NotBlank @Size(max = 20) String> interests
) {
}
