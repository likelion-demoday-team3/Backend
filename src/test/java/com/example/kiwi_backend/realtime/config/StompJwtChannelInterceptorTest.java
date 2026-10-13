package com.example.kiwi_backend.realtime.config;

import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.common.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompJwtChannelInterceptorTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final StompJwtChannelInterceptor interceptor = new StompJwtChannelInterceptor(jwtProvider);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void connectAuthenticatesAndAttachesUserToOriginalMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer token-123");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        AuthUser user = new AuthUser(101L, "USER");
        when(jwtProvider.parseAccessToken("token-123")).thenReturn(user);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        assertThat(accessor.getUser()).isSameAs(user);
        verify(jwtProvider).parseAccessToken("token-123");
    }

    @Test
    void connectWithoutTokenFails() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WebSocket access token is required");
    }

    @Test
    void authenticatedClientCanSendOnlyToApplicationHandlers() {
        Message<byte[]> message = clientMessage(StompCommand.SEND, "/app/live/join", new AuthUser(101L, "USER"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void authenticatedClientCannotSendDirectlyToBrokerTopics() {
        Message<byte[]> message = clientMessage(StompCommand.SEND, "/topic/live/round-assigned", new AuthUser(101L, "USER"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WebSocket destination is not allowed");
    }

    @Test
    void authenticatedClientCanSubscribeToOwnUserQueue() {
        Message<byte[]> message = clientMessage(StompCommand.SUBSCRIBE, "/user/queue/live/round-assigned", new AuthUser(101L, "USER"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void clientCannotSubscribeToTopicOrRawQueue() {
        for (String destination : new String[]{"/topic/live/results", "/queue/live/results-ready", "/user/102/queue/live/results-ready"}) {
            Message<byte[]> message = clientMessage(StompCommand.SUBSCRIBE, destination, new AuthUser(101L, "USER"));
            assertThatThrownBy(() -> interceptor.preSend(message, channel))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("WebSocket destination is not allowed");
        }
    }

    @Test
    void sendWithoutAuthenticatedPrincipalFails() {
        Message<byte[]> message = clientMessage(StompCommand.SEND, "/app/live/join", null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authenticated member is required");
    }

    private Message<byte[]> clientMessage(StompCommand command, String destination, AuthUser user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
