package com.example.kiwi_backend.common.realtime;

public record UserMessageEvent(Long memberId, String destination, Object payload) { }
