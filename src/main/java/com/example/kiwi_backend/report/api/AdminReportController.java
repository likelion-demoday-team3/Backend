package com.example.kiwi_backend.report.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.report.application.AdminReportService;
import com.example.kiwi_backend.report.domain.ReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 운영자용 신고 처리 API. SecurityConfig에서 /admin/** 는 ADMIN만 접근 가능 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminReportController {

    private final AdminReportService adminReportService;

    /** 신고 목록 (최신순). status를 주지 않으면 전체 */
    @GetMapping("/reports")
    public ApiResponse<List<AdminReportService.ReportView>> reports(
            @RequestParam(required = false) ReportStatus status) {
        return ApiResponse.ok(adminReportService.list(status));
    }

    /** 신고 기각 */
    @PostMapping("/reports/{reportId}/dismiss")
    public ApiResponse<Void> dismiss(@PathVariable Long reportId, @AuthenticationPrincipal AuthUser admin) {
        adminReportService.dismiss(reportId, admin.memberId());
        return ApiResponse.ok();
    }

    /** 이용 정지 (새 세션 신청 불가, 예정 세션 취소·환불, 이 회원의 확인 전 신고는 처리 완료) */
    @PostMapping("/members/{memberId}/suspend")
    public ApiResponse<AdminReportService.SuspendResult> suspend(@PathVariable Long memberId,
                                                                 @AuthenticationPrincipal AuthUser admin) {
        return ApiResponse.ok(adminReportService.suspend(memberId, admin.memberId()));
    }

    /** 이용 정지 해제 */
    @PostMapping("/members/{memberId}/reactivate")
    public ApiResponse<Void> reactivate(@PathVariable Long memberId, @AuthenticationPrincipal AuthUser admin) {
        adminReportService.reactivate(memberId, admin.memberId());
        return ApiResponse.ok();
    }
}
