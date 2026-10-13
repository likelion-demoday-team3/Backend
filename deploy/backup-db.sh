#!/usr/bin/env bash
# DB 백업. EC2에서 매일 cron으로 실행한다 (docs/deploy-ec2.md 참고).
#   - BACKUP_DIR(기본 ~/kiwi-backups)에 날짜별 .sql.gz를 남기고 KEEP_DAYS(기본 14)일 지난 것은 지운다
#   - BACKUP_S3_URI(예: s3://kiwi-backups/db)가 있으면 S3에도 올린다 (EC2가 망가져도 남도록)
set -euo pipefail

cd "$(dirname "$0")"
POSTGRES_DB=$(grep -E '^POSTGRES_DB=' .env | cut -d= -f2-)
POSTGRES_USER=$(grep -E '^POSTGRES_USER=' .env | cut -d= -f2-)

BACKUP_DIR=${BACKUP_DIR:-$HOME/kiwi-backups}
KEEP_DAYS=${KEEP_DAYS:-14}
mkdir -p "$BACKUP_DIR"

file="$BACKUP_DIR/kiwi-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
docker compose exec -T db pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner | gzip > "$file"

if [ -n "${BACKUP_S3_URI:-}" ]; then
  aws s3 cp "$file" "$BACKUP_S3_URI/"
fi

find "$BACKUP_DIR" -name 'kiwi-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date -u +%FT%TZ) backup ok: $file"
