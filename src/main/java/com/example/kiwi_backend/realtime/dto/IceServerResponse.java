package com.example.kiwi_backend.realtime.dto;

import java.util.List;

public record IceServerResponse(List<IceServer> iceServers, int credentialTtlSeconds) {
    public IceServerResponse {
        iceServers = List.copyOf(iceServers);
    }
}
