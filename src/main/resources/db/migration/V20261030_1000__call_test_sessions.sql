ALTER TABLE dating_session
    ADD COLUMN call_test BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN call_test_host_member_id BIGINT,
    ADD COLUMN call_test_first_partner_member_id BIGINT,
    ADD COLUMN call_test_second_partner_member_id BIGINT;
