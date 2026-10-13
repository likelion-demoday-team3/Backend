ALTER TABLE member
    ADD COLUMN portone_identity_verification_id VARCHAR(40),
    ADD COLUMN pending_portone_identity_verification_id VARCHAR(40);

CREATE UNIQUE INDEX uk_member_portone_identity_verification_id
    ON member (portone_identity_verification_id)
    WHERE portone_identity_verification_id IS NOT NULL;

CREATE UNIQUE INDEX uk_member_pending_portone_identity_verification_id
    ON member (pending_portone_identity_verification_id)
    WHERE pending_portone_identity_verification_id IS NOT NULL;
