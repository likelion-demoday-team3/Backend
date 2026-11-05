package com.example.kiwi_backend.contract.command;

/** 정지된 회원의 세션 정리. session이 구현하고 report가 호출한다 */
public interface SessionSuspensionCommand {

    /**
     * 아직 시작하지 않은 세션(모집 중·로비 대기)의 신청을 취소하고 참가비를 전액 돌려준다.
     * 이미 진행 중이거나 끝난 세션은 건드리지 않는다. 취소한 신청 수를 돌려준다.
     */
    int cancelUpcomingApplications(Long memberId);
}
