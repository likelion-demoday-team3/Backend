package com.example.kiwi_backend.contract.command;

/** 회원 이용 제한. member가 구현하고 report(운영자 신고 처리)가 호출한다 */
public interface MemberAccountCommand {

    /**
     * 이용 정지. 정지된 회원은 새 세션 신청을 할 수 없다 (로그인·마이페이지는 그대로).
     * 활성 회원이 아니거나 운영자 계정이면 IllegalStateException.
     */
    void suspend(Long memberId);

    /** 정지 해제. 정지된 회원이 아니면 IllegalStateException */
    void reactivate(Long memberId);
}
