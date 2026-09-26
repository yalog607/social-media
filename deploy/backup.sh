#!/usr/bin/env bash
# Sao lưu ALOUTE: database (pg_dump, khôi phục bằng pg_restore) VÀ ảnh người dùng (thư mục /app/uploads trong container app).
# Chạy từ bất kỳ đâu; ví dụ đặt cron chạy 3 giờ sáng mỗi ngày:
#   0 3 * * *  /home/deploy/aloute/deploy/backup.sh >> /home/deploy/aloute-backup.log 2>&1
# Biến tùy chọn: BACKUP_DIR (mặc định ./backups), KEEP (số bản giữ lại cho mỗi loại, mặc định 14).
# Lưu ý: nên định kỳ chép thư mục backups/ ra khỏi VPS, một bản sao lưu nằm cùng ổ đĩa không cứu được khi ổ đĩa hỏng.
set -euo pipefail

cd "$(dirname "$0")/.."
BACKUP_DIR="${BACKUP_DIR:-$PWD/backups}"
KEEP="${KEEP:-14}"
COMPOSE=(docker compose -f docker-compose.prod.yml)
mkdir -p "$BACKUP_DIR"
stamp="$(date +%Y%m%d-%H%M%S)"

# Bước nào lỗi giữa chừng thì không để lại file tạm rỗng
trap 'rm -f "$BACKUP_DIR"/*.tmp' EXIT

# Ghi ra file tạm rồi mới đổi tên, và đảm bảo không rỗng, để không bao giờ giữ lại một bản hỏng
finish() { # $1=file tạm  $2=file đích  $3=mô tả
  if [ ! -s "$1" ]; then
    rm -f "$1"
    echo "LỖI: bản sao lưu $3 rỗng" >&2
    exit 1
  fi
  mv "$1" "$2"
  chmod 600 "$2"
  echo "OK $2 ($(du -h "$2" | cut -f1))"
}

# 1) Database
db="$BACKUP_DIR/aloute-db-$stamp.dump"
"${COMPOSE[@]}" exec -T postgres pg_dump -U aloute -d aloute -Fc > "$db.tmp"
finish "$db.tmp" "$db" "database"

# 2) Ảnh người dùng (tar nén; thư mục rỗng vẫn tạo ra một file tar hợp lệ)
img="$BACKUP_DIR/aloute-uploads-$stamp.tgz"
"${COMPOSE[@]}" exec -T app tar czf - -C /app uploads > "$img.tmp"
finish "$img.tmp" "$img" "ảnh"

# 3) Giữ KEEP bản mới nhất cho từng loại
for pattern in 'aloute-db-*.dump' 'aloute-uploads-*.tgz'; do
  ls -1t "$BACKUP_DIR"/$pattern 2>/dev/null | tail -n +"$((KEEP + 1))" | xargs -r rm -f
done
