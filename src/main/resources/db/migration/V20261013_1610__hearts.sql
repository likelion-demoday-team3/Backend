CREATE TABLE heart (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES dating_session(id),
    sender_id BIGINT NOT NULL REFERENCES member(id),
    receiver_id BIGINT NOT NULL REFERENCES member(id),
    charged_gem INT NOT NULL CHECK (charged_gem IN (0, 1)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_heart_pair UNIQUE (session_id, sender_id, receiver_id),
    CONSTRAINT ck_heart_not_self CHECK (sender_id <> receiver_id)
);
CREATE INDEX idx_heart_receiver ON heart(session_id, receiver_id);
