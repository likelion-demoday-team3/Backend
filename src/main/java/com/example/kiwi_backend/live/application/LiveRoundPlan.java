package com.example.kiwi_backend.live.application;

import java.util.List;

public record LiveRoundPlan(
        int roundNumber,
        List<LiveRoomPlan> rooms,
        List<Long> idleMemberIds
) {
    public LiveRoundPlan(int roundNumber, List<LiveRoomPlan> rooms) {
        this(roundNumber, rooms, List.of());
    }

    public LiveRoundPlan {
        if (rooms == null) {
            throw new IllegalArgumentException("rooms must not be null");
        }
        if (idleMemberIds == null) {
            throw new IllegalArgumentException("idleMemberIds must not be null");
        }
        rooms = List.copyOf(rooms);
        idleMemberIds = List.copyOf(idleMemberIds);
    }
}
