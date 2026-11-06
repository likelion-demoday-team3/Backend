package com.example.kiwi_backend.errorreport.domain;

/** 오류 제보 종류 */
public enum ErrorReportKind {
    /** 사용자가 마이페이지에서 직접 적어 보낸 제보 */
    USER,
    /** 앱(프론트)이 자동으로 모은 에러 (화면 오류, 서버 오류 응답 등) */
    AUTO
}
