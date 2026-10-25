package com.example.kiwi_backend.vote.api;
import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.vote.application.MatchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/match-history")
public class MatchHistoryController {
    private final MatchHistoryService history;
    @GetMapping
    public ApiResponse<List<MatchHistoryService.Item>> history(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(history.history(user.memberId()));
    }
}
