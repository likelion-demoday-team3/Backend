package com.example.kiwi_backend.session.api.controller;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.session.api.dto.*;
import com.example.kiwi_backend.session.application.service.SessionApplicationService;
import com.example.kiwi_backend.session.application.service.SessionReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 회원용 세션 API */
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionReadService sessionReadService;
    private final SessionApplicationService sessionApplicationService;

    /** 신청 가능한 세션 목록 */
    @GetMapping
    public ApiResponse<List<SessionSummaryResponse>> list() {
        return ApiResponse.ok(sessionReadService.listUpcoming());
    }

    /** 내가 신청했던 세션들 */
    @GetMapping("/my")
    public ApiResponse<List<MySessionResponse>> mySessions(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(sessionReadService.mySessions(authUser.memberId()));
    }

    /** 세션 상세 (내 신청 상태 포함) */
    @GetMapping("/{sessionId}")
    public ApiResponse<SessionDetailResponse> detail(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long sessionId) {
        return ApiResponse.ok(sessionReadService.detail(sessionId, authUser.memberId()));
    }

    /** 신청 (참가비 차감) */
    @PostMapping("/{sessionId}/participation")
    public ApiResponse<ApplyResponse> apply(@AuthenticationPrincipal AuthUser authUser,
                                            @PathVariable Long sessionId) {
        return ApiResponse.ok(sessionApplicationService.apply(authUser.memberId(), sessionId));
    }

    /** 본인 취소 (시각별 환불) */
    @DeleteMapping("/{sessionId}/participation")
    public ApiResponse<CancelResponse> cancel(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long sessionId) {
        return ApiResponse.ok(sessionApplicationService.cancel(authUser.memberId(), sessionId));
    }
}
