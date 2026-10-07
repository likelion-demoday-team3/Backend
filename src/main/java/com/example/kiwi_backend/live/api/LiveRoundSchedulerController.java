package com.example.kiwi_backend.live.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.live.api.dto.LiveRoundSchedulerResponse;
import com.example.kiwi_backend.live.application.LiveRoundSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/live/sessions/{sessionId}/scheduler")
@RequiredArgsConstructor
public class LiveRoundSchedulerController {
//    프론트가 자동 진행할 수 있는 API

    private final LiveRoundSchedulerService liveRoundSchedulerService;

    @PostMapping("/start")
    public ApiResponse<LiveRoundSchedulerResponse> startScheduler(@PathVariable Long sessionId) {
        return ApiResponse.ok(LiveRoundSchedulerResponse.from(
                liveRoundSchedulerService.startSessionRounds(sessionId)
        ));
    }
}
