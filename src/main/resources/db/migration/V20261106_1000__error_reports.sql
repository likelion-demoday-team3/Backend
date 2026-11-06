-- 오류 제보. 사용자가 직접 보낸 제보(USER)와 앱이 자동으로 모은 에러(AUTO)를 함께 둔다.
-- 운영자가 확인하고 해결 처리한다
CREATE TABLE error_report (
    id                  BIGSERIAL PRIMARY KEY,
    kind                VARCHAR(10)   NOT NULL,
    member_id           BIGINT REFERENCES member (id),
    message             VARCHAR(2000) NOT NULL,
    page_url            VARCHAR(500),
    user_agent          VARCHAR(500),
    detail              TEXT,
    fingerprint         VARCHAR(64),
    occurrence_count    INT           NOT NULL DEFAULT 1,
    last_occurred_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    error_report_status VARCHAR(20)   NOT NULL,
    resolved_at         TIMESTAMPTZ,
    resolved_by         BIGINT REFERENCES member (id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- 운영자 화면: 상태별 최근 발생순
CREATE INDEX idx_error_report_status_last ON error_report (error_report_status, last_occurred_at DESC);
-- 같은 자동 에러가 다시 나면 새로 만들지 않고 횟수만 올린다
CREATE INDEX idx_error_report_fingerprint ON error_report (fingerprint, error_report_status);
-- 회원별 최근 제보 수 (짧은 시간에 너무 많이 보내지 않게)
CREATE INDEX idx_error_report_member_created ON error_report (member_id, created_at DESC);

COMMENT ON TABLE error_report IS '오류 제보. USER(사용자 직접 제보), AUTO(앱이 자동 수집한 에러)';
COMMENT ON COLUMN error_report.fingerprint IS 'AUTO 에러를 묶는 키. 같은 키의 확인 전 제보가 있으면 occurrence_count만 올린다';
COMMENT ON COLUMN error_report.error_report_status IS 'OPEN(확인 전), RESOLVED(해결)';
