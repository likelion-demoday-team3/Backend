package com.example.kiwi_backend.realtime.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
//    실시간 서버 설정

    private final StompJwtChannelInterceptor stompJwtChannelInterceptor;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompJwtChannelInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
//        서버가 클라이언트에게 보내는 주소. /topic = 여러 명에게 방송, /queue = 특정 한 명에게 개인 메시지
        registry.setApplicationDestinationPrefixes("/app");
//        클라이언트가 서버에게 메시지를 보낼 때 /app 으로 시작하게 한다, 즉 프론트가 보낼 때만 /app이 붙는다
        registry.setUserDestinationPrefix("/user");
//        특정 유저에게만 보내는 개인 메시지는 /user 를 기준으로 처리한다
//        프론트 12번 유저는 /user/queue/live/signal를 구독 중이어야 서버에게 /queue/live/signal 를 받을 수 있다.
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
//                프론트는 ws://서버주소/ws 로 연결해야한다.
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
