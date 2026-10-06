package com.example.kiwi_backend.member.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.member.api.dto.MyProfileResponse;
import com.example.kiwi_backend.member.api.dto.ProfileUpdateRequest;
import com.example.kiwi_backend.member.application.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 본인 프로필 카드 API.
 * 다른 사람의 카드를 조회하는 API는 없다. 세션 중 상대 카드는 A가 배정 메시지로 보낸다.
 */
@RestController
@RequestMapping("/members/my/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ApiResponse<MyProfileResponse> getMyProfile(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(profileService.getMyProfile(authUser.memberId()));
    }

    @PutMapping
    public ApiResponse<MyProfileResponse> updateProfile(@AuthenticationPrincipal AuthUser authUser,
                                                        @Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(profileService.updateProfile(authUser.memberId(), request));
    }
}
