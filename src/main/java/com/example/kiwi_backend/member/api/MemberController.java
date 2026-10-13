package com.example.kiwi_backend.member.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.member.api.dto.InstagramIdRequest;
import com.example.kiwi_backend.member.api.dto.IdentityVerificationRequest;
import com.example.kiwi_backend.member.api.dto.IdentityVerificationResponse;
import com.example.kiwi_backend.member.api.dto.IdentityVerificationStartResponse;
import com.example.kiwi_backend.member.api.dto.MyInfoResponse;
import com.example.kiwi_backend.member.api.dto.PasswordChangeRequest;
import com.example.kiwi_backend.member.api.dto.PhoneNumberRequest;
import com.example.kiwi_backend.member.application.MemberService;
import com.example.kiwi_backend.member.application.PortOneIdentityVerificationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 본인 계정 API. 모든 요청은 로그인한 본인에 대해서만 동작한다 */
@RestController
@RequestMapping("/members/my")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MemberController {

    private final MemberService memberService;
    private final PortOneIdentityVerificationService identityVerificationService;

    @PostMapping("/identity-verification/request")
    public ApiResponse<IdentityVerificationStartResponse> startIdentityVerification(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(identityVerificationService.start(authUser.memberId()));
    }

    @PostMapping("/identity-verification/confirm")
    public ApiResponse<IdentityVerificationResponse> verifyIdentity(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody IdentityVerificationRequest request) {
        return ApiResponse.ok(identityVerificationService.verify(
                authUser.memberId(), request.identityVerificationId()));
    }

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
