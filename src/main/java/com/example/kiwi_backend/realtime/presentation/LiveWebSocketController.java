package com.example.kiwi_backend.realtime.presentation;

import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.live.application.LiveLobbyStartScheduler;
import com.example.kiwi_backend.realtime.dto.LiveJoinRequest;
import com.example.kiwi_backend.realtime.dto.SignalRequest;
import com.example.kiwi_backend.realtime.service.LiveJoinService;
import com.example.kiwi_backend.realtime.service.LiveLobbyPresenceService;
import com.example.kiwi_backend.realtime.service.RealtimeSignalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;

@Controller
@RequiredArgsConstructor
public class LiveWebSocketController {
//    HTTP 요청이 아니라 WebSocket 메시지를 받는 컨트롤러

    private final SimpMessagingTemplate messagingTemplate;
    private final RealtimeSignalService realtimeSignalService;
    private final LiveJoinService liveJoinService;
    private final LiveLobbyPresenceService lobbyPresenceService;
    private final LiveLobbyStartScheduler lobbyStartScheduler;
    private final ObjectProvider<SessionQueryService> sessionQueryServiceProvider;

    @MessageMapping("/live/join")
    public void join(@Valid LiveJoinRequest request, Principal principal,
                     @Header("simpSessionId") String websocketSessionId) {
        Long memberId = requireMemberId(principal);
//        보낸 사람을 로그인 정보에서 가져온다. (프론트가 정보를 보내지 X)

        var response = liveJoinService.join(request.sessionId(), memberId);
        SessionQueryService sessionQueryService = sessionQueryServiceProvider.getIfAvailable();
        if (sessionQueryService == null) {
            throw new IllegalStateException("SessionQueryService 구현체가 필요합니다");
        }
        Instant startTime = sessionQueryService.getStartTime(request.sessionId());
        if (startTime == null) {
            throw new IllegalStateException("세션 시작 시각을 찾을 수 없습니다");
        }
        Instant openAt = startTime.minus(Duration.ofMinutes(5));
        Instant closeAt = startTime.plus(Duration.ofMinutes(1));
        lobbyPresenceService.join(request.sessionId(), memberId, websocketSessionId, openAt, closeAt);
        lobbyStartScheduler.scheduleIfNeeded(request.sessionId(), closeAt);

        messagingTemplate.convertAndSendToUser(
                memberId.toString(),
                "/queue/live/joined",
                response
        );
//        입장 확인 메시지를 보낸다
    }

    @MessageMapping("/live/signal")
    public void signal(@Valid SignalRequest request, Principal principal) {
        Long fromMemberId = requireMemberId(principal);
        realtimeSignalService.sendSignal(request, fromMemberId);
    }

    private Long requireMemberId(Principal principal) {
//        authUser가 진짜 있는지 확인하고, memberId를 꺼낸다
        if (!(principal instanceof AuthUser authUser) || authUser.memberId() == null) {
            throw new IllegalArgumentException("authenticated member is required");
        }
        return authUser.memberId();
//        로그인 정보가 없으면 에러고 있으면 memberId 반환
    }
}
