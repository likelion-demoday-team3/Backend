package com.example.kiwi_backend.vote.api;
import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.vote.application.VoteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions/{sessionId}")
public class VoteController {
    private final VoteService votes;
    @PostMapping("/votes")
    public ApiResponse<VoteService.Submission> submit(@PathVariable Long sessionId,
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody SubmitVote request) {
        return ApiResponse.ok(votes.submit(sessionId, user.memberId(), request.rankedMemberIds()));
    }
    @GetMapping("/votes/me")
    public ApiResponse<VoteService.Submission> own(@PathVariable Long sessionId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(votes.ownVote(sessionId, user.memberId()));
    }
    @GetMapping("/results/summary")
    public ApiResponse<VoteService.Summary> summary(@PathVariable Long sessionId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(votes.summary(sessionId, user.memberId()));
    }
    public record SubmitVote(@NotNull @Size(max = 3) List<@NotNull Long> rankedMemberIds) { }
}
