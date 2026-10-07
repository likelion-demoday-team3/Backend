package com.example.kiwi_backend.live.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.live.api.dto.LiveRoundAdvanceResponse;
import com.example.kiwi_backend.live.api.dto.LiveRoundStateResponse;
import com.example.kiwi_backend.live.application.LiveRoundStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/live/sessions/{sessionId}/rounds/{roundNumber}")
@RequiredArgsConstructor
public class LiveRoundControlController {

    private final LiveRoundStateService liveRoundStateService;

    @PostMapping("/start")
    public ApiResponse<LiveRoundStateResponse> startRound(@PathVariable Long sessionId,
                                                          @PathVariable int roundNumber) {
        return ApiResponse.ok(LiveRoundStateResponse.from(
                liveRoundStateService.startRound(sessionId, roundNumber, Instant.now())
        ));
    }

    @PostMapping("/complete")
    public ApiResponse<LiveRoundStateResponse> completeRound(@PathVariable Long sessionId,
                                                             @PathVariable int roundNumber) {
        return ApiResponse.ok(LiveRoundStateResponse.from(
                liveRoundStateService.completeRound(sessionId, roundNumber, Instant.now())
        ));
    }

    @PostMapping("/advance")
    public ApiResponse<LiveRoundAdvanceResponse> advanceRound(@PathVariable Long sessionId,
                                                              @PathVariable int roundNumber) {
        return ApiResponse.ok(LiveRoundAdvanceResponse.from(
                liveRoundStateService.completeAndPrepareNextRound(sessionId, roundNumber, Instant.now())
        ));
    }
}
