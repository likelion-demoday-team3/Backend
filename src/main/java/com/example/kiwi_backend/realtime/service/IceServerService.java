package com.example.kiwi_backend.realtime.service;

import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.realtime.dto.IceServer;
import com.example.kiwi_backend.realtime.dto.IceServerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IceServerService {

    private final SessionQueryService sessionQueryService;
    private final Clock clock;

    @Value("${app.rtc.stun-urls:}")
    private String stunUrls;

    @Value("${app.rtc.turn-urls:}")
    private String turnUrls;

    @Value("${app.rtc.turn-shared-secret:}")
    private String turnSharedSecret;

    @Value("${app.rtc.credential-ttl-seconds:3600}")
    private int credentialTtlSeconds;

    public IceServerResponse getForMember(Long sessionId, Long memberId) {
        if (sessionId == null || memberId == null || !sessionQueryService.isParticipant(sessionId, memberId)) {
            throw new IllegalArgumentException("라이브 세션 참가자만 통화 연결 정보를 받을 수 있습니다");
        }
        if (credentialTtlSeconds < 60) {
            throw new IllegalStateException("TURN credential TTL must be at least 60 seconds");
        }

        List<IceServer> servers = new ArrayList<>();
        List<String> configuredStunUrls = urls(stunUrls);
        List<String> configuredTurnUrls = urls(turnUrls);
        if (!configuredStunUrls.isEmpty()) {
            servers.add(new IceServer(configuredStunUrls, null, null));
        }
        if (!configuredTurnUrls.isEmpty()) {
            if (turnSharedSecret == null || turnSharedSecret.isBlank()) {
                throw new IllegalStateException("TURN shared secret must be configured when TURN URLs are set");
            }
            String username = (clock.instant().getEpochSecond() + credentialTtlSeconds) + ":" + memberId;
            servers.add(new IceServer(configuredTurnUrls, username, turnCredential(username, turnSharedSecret)));
        }
        return new IceServerResponse(servers, configuredTurnUrls.isEmpty() ? 0 : credentialTtlSeconds);
    }

    private List<String> urls(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String turnCredential(String username, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create TURN credentials", e);
        }
    }
}
