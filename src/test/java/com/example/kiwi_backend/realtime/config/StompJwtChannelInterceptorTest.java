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
}
