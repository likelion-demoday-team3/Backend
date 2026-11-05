-- 세션 중 신고. 운영자가 확인하고 이용 정지하거나 기각한다
CREATE TABLE report (
    id                 BIGSERIAL PRIMARY KEY,
    session_id         BIGINT      NOT NULL REFERENCES dating_session (id),
    reporter_id        BIGINT      NOT NULL REFERENCES member (id),
    reported_member_id BIGINT      NOT NULL REFERENCES member (id),
    reason             VARCHAR(40) NOT NULL,
    report_status      VARCHAR(20) NOT NULL,
    resolved_at        TIMESTAMPTZ,
    resolved_by        BIGINT REFERENCES member (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_report_pair UNIQUE (session_id, reporter_id, reported_member_id),
    CONSTRAINT ck_report_not_self CHECK (reporter_id <> reported_member_id)
);

-- 운영자 화면: 상태별 최신순, 회원별 누적 신고 수
CREATE INDEX idx_report_status_created ON report (report_status, created_at DESC);
CREATE INDEX idx_report_reported_member ON report (reported_member_id);

COMMENT ON TABLE report IS '세션 중 신고. 같은 세션에서 같은 상대는 한 번만 신고할 수 있다';
COMMENT ON COLUMN report.report_status IS 'PENDING(확인 전), ACTIONED(이용 정지로 처리), DISMISSED(기각)';
