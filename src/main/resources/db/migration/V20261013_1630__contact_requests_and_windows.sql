CREATE TABLE contact_request (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES dating_session(id),
    requester_id BIGINT NOT NULL REFERENCES member(id),
    target_id BIGINT NOT NULL REFERENCES member(id),
    contact_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    charged_gem INT NOT NULL DEFAULT 0 CHECK (charged_gem IN (0, 1)),
    created_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    CONSTRAINT uk_contact_request_pair UNIQUE (session_id, requester_id, target_id),
    CONSTRAINT ck_contact_request_not_self CHECK (requester_id <> target_id)
);
CREATE INDEX idx_contact_request_target ON contact_request(target_id, created_at DESC);
CREATE INDEX idx_contact_request_sender ON contact_request(requester_id, created_at DESC);
CREATE TABLE post_session_window (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES dating_session(id),
    member_id BIGINT NOT NULL REFERENCES member(id),
    results_closed_at TIMESTAMPTZ,
    contacts_closed_at TIMESTAMPTZ,
    CONSTRAINT uk_post_session_window UNIQUE (session_id, member_id),
    CONSTRAINT ck_contact_window_after_result CHECK (contacts_closed_at IS NULL OR results_closed_at IS NOT NULL)
);
