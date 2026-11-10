-- 가입할 때 필수 약관(이용약관·개인정보 수집·이용·제3자 제공)에 동의한 시각과 약관 버전.
-- 이 기능 이전에 가입한 회원은 비어 있다 (나중에 다시 동의받을 때 채운다).
ALTER TABLE member ADD COLUMN terms_agreed_at TIMESTAMPTZ;
ALTER TABLE member ADD COLUMN terms_version VARCHAR(20);
COMMENT ON COLUMN member.terms_agreed_at IS '필수 약관(이용약관·개인정보 수집·이용·제3자 제공) 동의 시각';
COMMENT ON COLUMN member.terms_version IS '동의한 약관 버전 (약관 시행일, 예: 2026-10-31)';
