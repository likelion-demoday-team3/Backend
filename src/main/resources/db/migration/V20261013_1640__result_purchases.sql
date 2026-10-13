CREATE TABLE result_purchase (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES dating_session(id),
    buyer_id BIGINT NOT NULL REFERENCES member(id),
    product VARCHAR(30) NOT NULL,
    target_key BIGINT NOT NULL DEFAULT 0,
    charged_gem INT NOT NULL CHECK (charged_gem > 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_result_purchase UNIQUE (session_id, buyer_id, product, target_key)
);
