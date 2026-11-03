package com.example.kiwi_backend.live.application;

public enum LiveRoundPhase {
    ROUND,
    BREAK, // [휴식 제거] 현재는 쓰지 않는다. 다음 라운드가 바로 시작되므로 ROUND를 돌려준다
    FINISHED
}
