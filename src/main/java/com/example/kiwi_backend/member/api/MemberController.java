package com.example.kiwi_backend.member.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.member.api.dto.InstagramIdRequest;
import com.example.kiwi_backend.member.api.dto.MyInfoResponse;
import com.example.kiwi_backend.member.api.dto.PasswordChangeRequest;
import com.example.kiwi_backend.member.api.dto.PhoneNumberRequest;
import com.example.kiwi_backend.member.application.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 본인 계정 API. 모든 요청은 로그인한 본인에 대해서만 동작한다 */
@RestController
@RequestMapping("/members/my")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public ApiResponse<MyInfoResponse> getMyInfo(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(memberService.getMyInfo(authUser.memberId()));
    }

    @PutMapping("/phone")
    public ApiResponse<Void> changePhoneNumber(@AuthenticationPrincipal AuthUser authUser,
                                               @Valid @RequestBody PhoneNumberRequest request) {
        memberService.changePhoneNumber(authUser.memberId(), request.phoneNumber());
        return ApiResponse.ok();
    }

    @DeleteMapping("/phone")
    public ApiResponse<Void> removePhoneNumber(@AuthenticationPrincipal AuthUser authUser) {
        memberService.removePhoneNumber(authUser.memberId());
        return ApiResponse.ok();
    }

    @PutMapping("/instagram")
    public ApiResponse<Void> changeInstagramId(@AuthenticationPrincipal AuthUser authUser,
                                               @Valid @RequestBody InstagramIdRequest request) {
        memberService.changeInstagramId(authUser.memberId(), request.instagramId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/instagram")
    public ApiResponse<Void> removeInstagramId(@AuthenticationPrincipal AuthUser authUser) {
        memberService.removeInstagramId(authUser.memberId());
        return ApiResponse.ok();
    }

    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthUser authUser,
                                            @Valid @RequestBody PasswordChangeRequest request) {
        memberService.changePassword(authUser.memberId(), request.currentPassword(), request.newPassword());
        return ApiResponse.ok();
    }

    @DeleteMapping
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthUser authUser) {
        memberService.withdraw(authUser.memberId());
        return ApiResponse.ok();
    }
}
