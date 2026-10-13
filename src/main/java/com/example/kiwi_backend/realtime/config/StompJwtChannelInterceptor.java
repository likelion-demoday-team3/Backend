package com.example.kiwi_backend.realtime.config;

import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.common.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final Set<String> CLIENT_SEND_DESTINATIONS = Set.of(
            "/app/live/join",
            "/app/live/signal"
    );
    private static final Set<String> USER_SUBSCRIPTIONS = Set.of(
            "/user/queue/live/joined",
            "/user/queue/live/signal",
            "/user/queue/live/round-assigned",
            "/user/queue/live/round-idle",
            "/user/queue/live/voting-started",
            "/user/queue/live/starting-soon",
            "/user/queue/live/cancelled",
            "/user/queue/live/hearts-started",
            "/user/queue/live/heart-received",
            "/user/queue/live/results-ready",
            "/user/queue/live/contact-received",
            "/user/queue/live/contact-requested",
            "/user/queue/live/contact-request-answered"
    );

    private final JwtProvider jwtProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && accessor.getCommand() == StompCommand.CONNECT) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new IllegalArgumentException("WebSocket access token is required");
            }
            accessor.setUser(jwtProvider.parseAccessToken(authorization.substring(7).trim()));
        }
        if (accessor != null && (accessor.getCommand() == StompCommand.SEND
                || accessor.getCommand() == StompCommand.SUBSCRIBE)) {
            Principal principal = accessor.getUser();
            String destination = accessor.getDestination();
            if (!(principal instanceof AuthUser user) || user.memberId() == null) {
                throw new IllegalArgumentException("authenticated member is required");
            }
            boolean allowed = accessor.getCommand() == StompCommand.SEND
                    ? CLIENT_SEND_DESTINATIONS.contains(destination)
                    : USER_SUBSCRIPTIONS.contains(destination);
            if (!allowed) {
                throw new IllegalArgumentException("WebSocket destination is not allowed");
            }
        }
        return message;
    }
}
