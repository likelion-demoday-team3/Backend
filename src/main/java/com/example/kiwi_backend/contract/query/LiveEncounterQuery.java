package com.example.kiwi_backend.contract.query;

/**
 * 라이브 대화 기록 조회. live가 구현하고 interaction·vote가 호출한다.
 * 하트·투표는 실제로 같은 방에서 대화한 상대에게만 허용하기 위해 쓴다.
 */
public interface LiveEncounterQuery {

    /**
     * 두 회원이 이 세션에서 같은 방에 배정된 적이 있는가.
     * 이미 시작했거나 끝난 라운드만 센다 (미리 짜인 PENDING 라운드의 방은 아직 만나지 않은 것).
     */
    boolean haveMet(Long sessionId, Long memberId, Long otherMemberId);
}
