package com.example.kiwi_backend.live.application;

import java.util.List;

public record LiveRoundPlan(
        int roundNumber,
        List<LiveRoomPlan> rooms
) {
    public LiveRoundPlan {
        rooms = List.copyOf(rooms);
    }
}
