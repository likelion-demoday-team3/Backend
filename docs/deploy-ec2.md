# EC2 배포 가이드

백엔드를 EC2 한 대에 Docker Compose로 띄운다. 프론트는 Vercel에 따로 배포한다.

```
브라우저 ──https──▶ 프론트 (Vercel, kiwi.site)
        └─https/wss──▶ EC2 (api.kiwi.site)
                        ├─ caddy : 80/443, HTTPS 인증서 자동 발급·갱신
                        ├─ app   : Spring Boot (외부 비공개)
                        └─ db    : PostgreSQL (외부 비공개, pgdata 볼륨)
```

관련 파일: `Dockerfile`, `deploy/docker-compose.yml`, `deploy/Caddyfile`, `deploy/.env.example`, `deploy/backup-db.sh`

## 1. 도메인 (가비아)

도메인 하나로 프론트와 백엔드를 나눠 쓴다. 아래는 `kiwi.site`를 샀다고 가정한 예시다.

| 이름 | 용도 | DNS 레코드 |
| --- | --- | --- |
| `kiwi.site` (또는 `www`) | 프론트 | Vercel 프로젝트 → Settings → Domains에 추가하면 Vercel이 넣을 레코드를 알려준다 |
| `api.kiwi.site` | 백엔드 | A 레코드 `api` → EC2 탄력적 IP |

가비아: My가비아 → 도메인 관리 → DNS 정보 → DNS 관리 → 레코드 추가. 반영까지 수 분~수십 분 걸릴 수 있다.

## 2. EC2 만들기

1. 리전: 서울(ap-northeast-2), AMI: Ubuntu 24.04 LTS
2. 인스턴스: **t3.small(메모리 2GB) 이상** 권장. t3.micro(1GB)는 앱+DB를 같이 올리기에 빠듯하므로 쓰려면 아래 스왑을 꼭 추가한다
3. 스토리지: gp3 20~30GB
4. 보안 그룹 인바운드: 22(SSH, **내 IP만**), 80, 443. 5432·8080은 열지 않는다
5. 탄력적 IP를 할당해 인스턴스에 연결하고, 가비아 `api` A 레코드에 이 IP를 넣는다

## 3. 서버 준비 (EC2에 SSH 접속 후 한 번만)

```bash
# Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# 다시 접속해야 docker 그룹이 적용된다
exit
```

t3.micro라면 스왑 2GB 추가:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 4. 배포

```bash
git clone https://github.com/likelion-demoday-team3/Backend.git
cd Backend/deploy
cp .env.example .env
nano .env        # 값 채우기 (아래 표)
docker compose up -d --build
```

`.env`에서 꼭 채울 값:

| 변수 | 값 |
| --- | --- |
| `API_DOMAIN` | `api.kiwi.site` (DNS가 먼저 EC2를 가리키고 있어야 인증서가 발급된다) |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | `https://kiwi.site` (Vercel 주소. 끝에 `/` 없이) |
| `POSTGRES_PASSWORD` | `openssl rand -base64 24` |
| `JWT_SECRET`, `APP_CRYPTO_KEY` | 각각 `openssl rand -base64 32`. **한 번 정하면 바꾸지 않는다** (`APP_CRYPTO_KEY`를 바꾸면 암호화된 연락처를 읽을 수 없다) |
| `TOSS_SECRET_KEY`, `TOSS_CLIENT_KEY` | 토스페이먼츠 시크릿 키 / 클라이언트 키 (같은 쌍) |
| `SMTP_*` | 운영 프로필은 실제 메일을 보낸다. 비어 있으면 가입 인증 메일이 나가지 않는다 |

`.env`는 git에 올라가지 않는다. 값은 팀 비밀 저장소 등 안전한 곳에 따로 보관한다.

## 5. 확인

```bash
curl https://api.kiwi.site/actuator/health      # {"status":"UP"...}
docker compose ps                                # app, db, caddy 모두 Up
docker compose logs -f app                       # 앱 로그
```

인증서 발급이 안 되면 `docker compose logs caddy`를 본다. 대부분 DNS가 아직 EC2를 가리키지 않거나 80/443이 닫혀 있는 경우다.

## 6. 외부 서비스 설정

- **토스페이먼츠**: developers.tosspayments.com → API 키에서 키 쌍 확인. 실결제는 상점 계약·심사 후 라이브 키를 받는다
- **프론트(Vercel)**: API 주소 `https://api.kiwi.site`, WebSocket 주소 `wss://api.kiwi.site/ws`

## 7. 업데이트 배포

```bash
cd Backend && git pull
cd deploy && docker compose up -d --build
```

DB 데이터(pgdata 볼륨)와 인증서(caddy_data 볼륨)는 유지된다. `docker compose down -v`는 **볼륨까지 지우므로 운영에서 쓰지 않는다.**

## 8. 백업 (실사용자가 있으면 필수)

DB가 EC2 안에 있으므로 백업을 직접 해야 한다. 세 겹으로 둔다.

1. **매일 DB 덤프**: `crontab -e`에 추가 (매일 04:00 UTC = 13:00 KST)
   ```
   0 4 * * * /home/ubuntu/Backend/deploy/backup-db.sh >> /home/ubuntu/kiwi-backups/backup.log 2>&1
   ```
   `~/kiwi-backups`에 14일치를 남긴다.
2. **S3 복사**: EC2가 통째로 망가져도 남도록 S3 버킷을 만들고, EC2에 S3 쓰기 권한 IAM 역할을 붙인 뒤 `sudo apt install -y awscli`, crontab 줄 앞에 `BACKUP_S3_URI=s3://버킷/db`를 붙인다
3. **EBS 스냅샷**: EC2 콘솔 → 수명 주기 관리자(Data Lifecycle Manager)로 디스크 스냅샷을 매일 자동 생성

복구:

```bash
gunzip -c ~/kiwi-backups/kiwi-YYYYMMDDTHHMMSSZ.sql.gz | docker compose exec -T db psql -U kiwi -d kiwi
```

나중에 사용자가 늘면 DB만 RDS로 옮기고 `SPRING_DATASOURCE_*`를 RDS 주소로 바꾸면 된다 (compose의 db 서비스는 제거).

## 운영 전 남은 일

- TURN 서버: 없으면 일부 모바일·회사망에서 영상 연결이 안 될 수 있다 (`RTC_TURN_*`, production-readiness.md 참고)
- Swagger UI(`/swagger-ui`)가 공개되어 있다. 운영에서 숨길지 정한다
- 토스페이먼츠 실결제는 계약 후 받은 라이브 키로 `TOSS_SECRET_KEY`, `TOSS_CLIENT_KEY`를 바꾼다
