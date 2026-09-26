#!/usr/bin/env bash
# CHỈ CẦN khi bật proxy Cloudflare (đám mây cam) trước Caddy.
#
#   ./deploy/cloudflare-ips.sh          in dòng CADDY_TRUSTED_PROXIES=... để dán vào .env
#   ./deploy/cloudflare-ips.sh --ufw    in các lệnh ufw chỉ cho Cloudflare vào cổng 80/443
#
# Dải IP của Cloudflare thỉnh thoảng đổi: chạy lại script này và `docker compose ... up -d` mỗi vài tháng.
# Nếu danh sách cũ, IP người dùng sẽ bị nhận thành IP của Cloudflare (mọi người cùng một IP) và giới hạn đăng nhập sai sẽ khóa cả nhóm.
set -euo pipefail

v4="$(curl -fsS --max-time 15 https://www.cloudflare.com/ips-v4)"
v6="$(curl -fsS --max-time 15 https://www.cloudflare.com/ips-v6)"
ranges="$(printf '%s\n%s\n' "$v4" "$v6" | tr -s '[:space:]' '\n' | sed '/^$/d')"

if [ -z "$v4" ] || [ -z "$v6" ] || [ -z "$ranges" ]; then
  echo "Không tải được danh sách IP của Cloudflare" >&2
  exit 1
fi
# Nội dung này sẽ vào .env và Caddyfile nên chỉ chấp nhận đúng dạng CIDR
if printf '%s\n' "$ranges" | grep -qvE '^[0-9a-fA-F:.]+/[0-9]{1,3}$'; then
  echo "Danh sách trả về có dòng không phải CIDR, dừng lại để an toàn" >&2
  exit 1
fi

if [ "${1:-}" = "--ufw" ]; then
  printf '%s\n' "$ranges" | while read -r cidr; do
    echo "sudo ufw allow from $cidr to any port 80,443 proto tcp"
  done
else
  printf 'CADDY_TRUSTED_PROXIES=%s\n' "$(printf '%s\n' "$ranges" | paste -sd' ' -)"
fi
