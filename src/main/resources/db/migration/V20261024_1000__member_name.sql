-- 회원 이름. 가입할 때 받는다.
-- 기존 회원은 이름이 없으므로 nullable로 추가한다 (신규 가입은 애플리케이션에서 필수로 강제).
ALTER TABLE member ADD COLUMN name VARCHAR(100);
COMMENT ON COLUMN member.name IS '회원 이름 (가입 시 입력)';
