# 운영 준비 및 외부 연동

## 지금 추가된 서버 기능

- `local` / `test` 프로필에서는 학교 이메일 인증 코드를 콘솔에 출력한다.
- 다른 프로필에서는 SMTP로 인증 메일을 보낸다. 메일 제공업체는 고정하지 않는다.
- 세션 참가자에게만 `/api/live/sessions/{sessionId}/ice-servers`를 제공한다. TURN URL이 설정되면 coturn REST 방식의 짧은 수명 자격증명을 발급한다.
- 첫 라운드를 시작하기 전에 Liner로 각 대화 상대 쌍의 궁합을 계산하고 `session_compatibility` 테이블에 저장한다. 결과가 없거나 Liner가 실패하면 라운드를 시작하지 않고 다음 폴링에서 재시도한다.
- `/actuator/health` 및 Kubernetes용 liveness/readiness health endpoint를 제공한다.
- `POST /members/my/identity-verification/request`가 인증 ID를 계정에 귀속해 발급한다. 완료 후 `POST /members/my/identity-verification/confirm`에서 서버가 PortOne에 재조회하고 가입 성별·생년월일과 대조한다. 프론트에서 전송한 인증 결과나 생년월일은 신뢰하지 않는다.
- 본인인증이 끝나지 않은 회원은 세션에 신청할 수 없다.

## 환경 변수

필수 기존 값: `APP_CRYPTO_KEY`, `JWT_SECRET`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `CORS_ALLOWED_ORIGINS`.

SMTP: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, 선택 `SMTP_AUTH`, `SMTP_STARTTLS_ENABLE`. SMTP 제공업체에서 발급한 자격증명을 사용하고 저장소에 커밋하지 않는다.

궁합: `LINER_API_KEY`, 선택 `LINER_MODEL` (기본 `liner-mark-1.3`). Liner에는 연락처나 실명 대신 프로필 카드의 연애 성향/관심사만 보낸다.

WebRTC: `RTC_STUN_URLS`, `RTC_TURN_URLS`, `RTC_TURN_SHARED_SECRET`, 선택 `RTC_TURN_CREDENTIAL_TTL_SECONDS`. TURN 값은 자체 coturn 또는 선택한 TURN 호스팅 업체의 REST shared-secret 방식으로 맞춘다. TURN 미설정 시 STUN만 반환되므로 일부 모바일/회사망에서는 연결되지 않을 수 있다.

PortOne identity: `PORTONE_API_SECRET`은 서버에만 둔다. `PORTONE_STORE_ID`와 `PORTONE_IDENTITY_CHANNEL_KEY`는 PortOne 콘솔에서 확인한다. 인증 ID는 서버 endpoint가 발급하므로 프론트가 임의로 만들지 않는다.

## 프론트 연동 API

1. 로그인 상태에서 `POST /members/my/identity-verification/request` 호출. body 없음. 응답의 `data.storeId`, `data.channelKey`, `data.identityVerificationId`를 사용해 PortOne V2 `requestIdentityVerification()`을 연다.
2. 브라우저 인증 성공 시 `POST /members/my/identity-verification/confirm`에 `{ "identityVerificationId": "..." }` 전송. 서버가 PortOne REST API 결과를 확인한 뒤 성공을 응답한다. 실패/취소 시 세션 신청은 불가능하다.
3. 라이브 입장 뒤 연결 설정이 필요할 때 `GET /api/live/sessions/{sessionId}/ice-servers` 호출. 응답의 `data.iceServers`를 브라우저 `RTCPeerConnection`에 전달한다. TURN이 설정된 경우 username/credential은 만료되므로 장기 저장하지 않는다.

## 아직 외부 계약·승인이 필요한 것

- 본인인증: 실제 PortOne store/channel 및 V2 API Secret을 발급·설정하고 프론트 SDK와 end-to-end 테스트해야 한다.
- 보석 결제: PortOne 연동 중 PG는 카카오페이 예정이나 PG 승인 전이다. 실제 결제 요청/검증/취소 및 `payment` 기록과 보석 지급은 구현·활성화하지 않았다. 결제 완료는 브라우저 응답을 믿지 말고 서버에서 PortOne 결제 조회/검증 후 트랜잭션으로 지급해야 한다.
- 학교 이메일 허용 도메인 목록은 실제 학생용 주소인지 학교별로 확인해야 한다.
- 운영 배포, TLS 도메인, DB 백업/복구, SMTP/TURN/Liner 실계정 테스트, 개인정보 보유·삭제 정책 검증은 별도 완료가 필요하다.

## 로컬 실행 참고

`SPRING_PROFILES_ACTIVE=local`은 개발용 콘솔 메일 발송을 활성화한다. 운영에 `local` 또는 `test` 프로필을 사용하지 않는다. 운영 도메인에서는 WebSocket과 REST 모두 HTTPS/WSS를 사용한다.
