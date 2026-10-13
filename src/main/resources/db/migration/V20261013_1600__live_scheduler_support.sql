CREATE TABLE live_round_idle_participant (
    id BIGSERIAL PRIMARY KEY,
    round_id BIGINT NOT NULL REFERENCES live_round(id),
    member_id BIGINT NOT NULL REFERENCES member(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_live_round_idle_round_member UNIQUE (round_id, member_id)
);

CREATE INDEX idx_session_lobby_due ON dating_session (session_status, start_time);
CREATE INDEX idx_session_first_round_due ON dating_session (session_status, live_status, phase_ends_at);
