package com.example.kiwi_backend.live.application;

public record LiveScheduleResult(
//        서비스 내부 결과 확인용 DTO이다. 이 세션의 어떤 라운드/방을 생성한건지
        Long sessionId,
        int roundCount,
        int roomCount,
        int idleCount
) {
}
