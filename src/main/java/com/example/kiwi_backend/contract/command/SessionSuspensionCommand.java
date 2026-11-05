package com.example.kiwi_backend.contract.command;

/** 정지된 회원의 세션 정리. session이 구현하고 report가 호출한다 */
public interface SessionSuspensionCommand {

    /**
     * 아직 시작하지 않은 세션(모집 중·로비 대기)의 신청을 취소한다.
     * 참가비는 본인 취소와 같은 환불 규정을 따른다 (24시간 전 100%, 12시간 전 50%, 그 이후 0%).
     * 이미 진행 중이거나 끝난 세션은 건드리지 않는다.
     */
    Result cancelUpcomingApplications(Long memberId);

    /** cancelled: 취소한 신청 수, refundedGem: 돌려준 보석 합계 */
    record Result(int cancelled, int refundedGem) {
    }
}
