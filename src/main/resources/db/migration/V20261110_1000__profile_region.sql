-- 프로필 카드에 학교 지역(시·도)을 보여준다. 가입 때 학교 메일 도메인으로 정한다 (university-domains.csv).
ALTER TABLE profile ADD COLUMN region VARCHAR(20);
COMMENT ON COLUMN profile.region IS '카드 표시용 학교 지역 (시·도, 예: 서울). 가입 때 학교 메일 도메인으로 정한다';

-- 지금까지 가입할 수 있었던 학교는 모두 서울이다
UPDATE profile
SET region = '서울'
WHERE region IS NULL
  AND university IN ('연세대학교', '서강대학교', '이화여자대학교', '홍익대학교');
