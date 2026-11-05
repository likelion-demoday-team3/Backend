package com.example.kiwi_backend.report.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.report.application.ReportService;
import com.example.kiwi_backend.report.domain.ReportReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ReportController {

    private final ReportService reportService;

    /** 세션에서 대화한 상대 신고. 같은 상대를 다시 신고하면 기존 신고를 돌려준다 */
    @PostMapping("/sessions/{sessionId}/reports")
    public ApiResponse<ReportService.Receipt> report(@PathVariable Long sessionId,
                                                     @AuthenticationPrincipal AuthUser user,
                                                     @Valid @RequestBody ReportRequest request) {
        return ApiResponse.ok(reportService.submit(sessionId, user.memberId(), request.targetId(), request.reason()));
    }

    public record ReportRequest(@NotNull Long targetId, @NotNull ReportReason reason) { }
}
