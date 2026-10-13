package com.example.kiwi_backend.realtime.dto;

import java.util.List;

public record IceServer(List<String> urls, String username, String credential) {
    public IceServer {
        urls = List.copyOf(urls);
    }
}
