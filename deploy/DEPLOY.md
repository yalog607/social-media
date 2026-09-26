# Deploy ALOUTE lên VPS với Caddy và domain

Kiến trúc: người dùng → **Caddy** (HTTPS tự động, reverse proxy) → container `app` (Spring Boot) → container `postgres`.
Caddy tự xin và gia hạn chứng chỉ **Let's Encrypt** cho tên miền của bạn, bạn không phải làm gì thêm. Cả ba container chạy chung một mạng Docker; **chỉ Caddy mở cổng 80/443** ra Internet.

```
Trình duyệt ──HTTPS──▶ Caddy :443 ─▶ app:8080 ─▶ postgres:5432
                        (cùng một mạng Docker nội bộ; app và postgres không publish cổng nào)
```

Tên miền có thể quản lý DNS ở Cloudflare. Mặc định bản ghi để **DNS only (đám mây xám)**, cách đơn giản nhất. Muốn dùng thêm proxy Cloudflare (đám mây cam) thì làm theo mục 7.

> **Đã kiểm thử ở máy dev, trong container thật:** build image; profile `prod` khởi động và từ chối cấu hình sai; Caddy phục vụ HTTPS/HTTP2 và tự chuyển `http` sang `https`; đăng nhập trả cookie `Secure; HttpOnly` và chuyển hướng `https`; giới hạn đăng nhập tính theo IP thật, kẻ tấn công giả header `X-Client-IP`/`CF-Connecting-IP` không né được; cả hai chế độ (DNS only và có proxy Cloudflare); tải ảnh lên, tạo lại container mà ảnh vẫn còn; sao lưu và khôi phục cả database lẫn ảnh.
> **Chưa kiểm thử (cần tài nguyên của bạn):** xin chứng chỉ Let's Encrypt thật (cần domain trỏ về VPS), đăng nhập Google qua Firebase thật, SMTP Brevo/Resend thật. Vì vậy mục 9 có danh sách kiểm tra sau deploy.

## 0. Cần chuẩn bị
- Một VPS Ubuntu 22.04/24.04, quyền SSH và sudo. RAM 1 GB chạy được nếu có swap (mục 2), nên có 2 GB nếu lượng người dùng tăng.
- Một tên miền (DNS quản lý ở Cloudflare hoặc bất kỳ nhà cung cấp nào).
- Repo GitHub **private** chứa mã nguồn (mục 3), hoặc dùng `scp`.
- Tài khoản Firebase (chỉ để đăng nhập Google, miễn phí), Brevo hoặc Resend (mục 4 và 5).

## 1. DNS: trỏ tên miền về VPS
Ở Cloudflare (*DNS → Records*) thêm bản ghi:

| Type | Name | Content | Proxy status |
|---|---|---|---|
| `A` | `aloute` (hoặc `@` nếu dùng tên miền gốc) | IPv4 của VPS | **DNS only** (đám mây xám) |

- Chỉ thêm bản ghi `AAAA` nếu bạn chắc IPv6 của VPS hoạt động. Có `AAAA` mà IPv6 hỏng thì Let's Encrypt có thể xác thực thất bại.
- **Kiểm tra trước khi chạy Caddy** (Let's Encrypt giới hạn số lần xác thực thất bại):
  ```bash
  dig +short aloute.example.com     # phải in ra đúng IP VPS
  ```
  Nếu DNS vừa đổi, đợi vài phút cho lan truyền.

## 2. VPS: chuẩn bị máy

### 2.1. Chuyển từ đăng nhập bằng mật khẩu sang SSH key
Mật khẩu SSH bị bot dò liên tục từ lúc VPS có IP công khai, nên làm bước này **trước tiên**. Làm đúng thứ tự và **giữ nguyên một cửa sổ SSH đang đăng nhập** cho tới khi xong bước 4, để nếu lỡ tay vẫn sửa được.

1. **Tạo key trên máy Windows của bạn** (PowerShell; Windows 10/11 có sẵn OpenSSH):
   ```powershell
   ssh-keygen -t ed25519 -C "aloute-vps"
   ```
   Nhấn Enter để lưu ở đường dẫn mặc định và **nên đặt passphrase**. File `id_ed25519` (khóa riêng) không được gửi cho bất kỳ ai, kể cả qua chat; chỉ `id_ed25519.pub` (khóa công khai) mới đưa lên VPS.
2. **Chép khóa công khai lên tài khoản `deploy`** (lệnh này hỏi mật khẩu của `deploy` một lần):
   ```powershell
   type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh deploy@<IP-VPS> "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
   ```
3. **Kiểm tra đăng nhập bằng key** ở một cửa sổ mới, ép không cho dùng mật khẩu:
   ```powershell
   ssh -o PasswordAuthentication=no deploy@<IP-VPS>
   ```
   Vào được mà không bị hỏi mật khẩu VPS (chỉ hỏi passphrase của key nếu bạn có đặt) thì mới sang bước 4. Trên VPS chạy `id`: phải thấy `uid=1000(deploy)` và nhóm `sudo`. Cần uid 1000 để container đọc được file khóa Firebase.
4. **Tắt đăng nhập bằng mật khẩu và đăng nhập root qua SSH**:
   ```bash
   sudo tee /etc/ssh/sshd_config.d/00-hardening.conf >/dev/null <<'EOF'
   PasswordAuthentication no
   KbdInteractiveAuthentication no
   PermitRootLogin no
   EOF
   sudo sshd -t && sudo systemctl restart ssh     # sshd -t báo lỗi thì DỪNG, chưa restart
   sudo sshd -T | grep -E '^(passwordauthentication|kbdinteractiveauthentication|permitrootlogin)'
   ```
   Ba dòng cuối phải in `no`. Tên file bắt đầu bằng `00-` là có chủ ý: OpenSSH dùng giá trị đọc được **đầu tiên**, và nhiều VPS có sẵn file `50-cloud-init.conf` chứa `PasswordAuthentication yes` sẽ đè lên một file tên `99-...`.
   Sau đó mở một cửa sổ thứ ba, `ssh deploy@<IP-VPS>` vẫn phải vào được. Thử `ssh root@<IP-VPS>` phải bị từ chối. Xong mới đóng các cửa sổ cũ.
5. **Củng cố phần còn lại của mật khẩu**: `deploy` vẫn cần mật khẩu cho `sudo`, hãy đặt dài và khó đoán (`sudo passwd deploy`), và đổi cả mật khẩu root. Xem đã có ai dò mật khẩu chưa: `sudo journalctl -u ssh --since "7 days ago" | grep -c "Failed password"`. Con số lớn là bình thường với VPS công khai và là lý do cần làm bước này.

> Lỡ tự khóa mình bên ngoài? Trang quản trị của nhà cung cấp VPS thường có **Console/VNC** đăng nhập thẳng vào máy, dùng nó để sửa lại file `00-hardening.conf`.
> VPS mới chưa có user `deploy`: `adduser deploy && usermod -aG sudo deploy` (user thường đầu tiên có uid 1000), rồi làm từ bước 1.

### 2.2. Cài đặt nền tảng
Đăng nhập bằng `deploy` (đã dùng key), rồi:

```bash
sudo apt update && sudo apt -y upgrade
sudo apt -y install ca-certificates curl git ufw dnsutils

# Docker Engine + compose plugin
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker deploy      # đăng xuất rồi đăng nhập lại để có hiệu lực
id -u                               # phải in ra 1000

# Swap 2 GB (quan trọng khi RAM 1 GB: bước build Maven cần nhiều bộ nhớ)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Tường lửa: SSH + web. 80 cần cho Let's Encrypt và chuyển http -> https, 443/udp cho HTTP/3.
sudo ufw default deny incoming && sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp
sudo ufw enable
```

Nhiều nhà cung cấp VPS còn có tường lửa riêng trên trang quản trị (security group): mở cả 22, 80, 443 (TCP) và 443 (UDP) ở đó.

> Docker bỏ qua `ufw` với cổng đã publish. Vì vậy **chỉ Caddy được có `ports:`** trong `docker-compose.prod.yml`. Tuyệt đối không thêm `ports:` cho `app` hay `postgres`: app tin header `X-Client-IP` do Caddy ghi đè, nếu lộ cổng của app thì kẻ xấu có thể tự gửi header đó để né giới hạn đăng nhập, còn lộ cổng Postgres thì nguy hiểm hơn nhiều.

## 3. Đưa mã nguồn lên VPS
**Cách A (khuyên dùng): GitHub private.** Ở máy bạn, trong thư mục `aloute/` (nơi có `pom.xml`):

```bash
git init && git add . && git commit -m "ALOUTE giai đoạn 1"
git branch -M main
git remote add origin git@github.com:<tên-bạn>/aloute.git
git push -u origin main
```

Trên VPS tạo *deploy key* chỉ đọc:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/aloute_deploy -N ""
cat ~/.ssh/aloute_deploy.pub     # dán vào GitHub repo → Settings → Deploy keys (KHÔNG tick "Allow write")
printf 'Host github.com\n  IdentityFile ~/.ssh/aloute_deploy\n' >> ~/.ssh/config
git clone git@github.com:<tên-bạn>/aloute.git ~/aloute
```

**Cách B: không dùng git.** Ở máy bạn (PowerShell), nén rồi gửi lên:

```powershell
tar --exclude=target --exclude=.git --exclude=uploads --exclude=secrets --exclude=.env -czf aloute.tar.gz -C .. aloute
scp aloute.tar.gz deploy@<IP-VPS>:~/
```
Trên VPS: `tar xzf aloute.tar.gz` (ra thư mục `~/aloute`).

## 4. Đăng nhập Google (Firebase Authentication)
Firebase **chỉ dùng để xác minh danh tính** khi bấm nút Google/Facebook. Ảnh người dùng lưu ngay trên VPS (mục 4.1), nên **không cần Cloud Storage và không cần gói Blaze hay thẻ thanh toán**. Đăng nhập bằng Google/Facebook miễn phí trên gói Spark, kể cả khi có nhiều người dùng (theo bảng giá của Firebase); chỉ đăng nhập bằng số điện thoại/SMS hoặc nâng cấp Identity Platform mới có phí và giới hạn riêng. Cứ giữ Authentication ở mức mặc định.

1. Tạo project ở console.firebase.google.com (giữ gói **Spark**).
2. *Build → Authentication → Get started → Sign-in method*: bật **Google** (chọn email hỗ trợ). Facebook cần thêm một ứng dụng ở Meta for Developers, có thể làm sau.
3. *Authentication → Settings → Authorized domains*: **thêm domain của bạn** (ví dụ `aloute.example.com`). Thiếu bước này, nút Google báo `auth/unauthorized-domain`.
4. *Project settings → General → Your apps → thêm Web app*, lấy `apiKey`, `authDomain`, `projectId` (các giá trị này công khai, không phải bí mật) điền vào `FIREBASE_WEB_*` trong `.env`.
5. *Project settings → Service accounts → Generate new private key*, rồi chép file JSON lên VPS:
   ```bash
   mkdir -p ~/aloute/secrets && chmod 700 ~/aloute/secrets
   # ở máy bạn:  scp firebase-key.json deploy@<IP-VPS>:~/aloute/secrets/firebase-adminsdk.json
   chmod 600 ~/aloute/secrets/firebase-adminsdk.json
   ```
   Trong `.env` đặt `FIREBASE_CREDENTIALS=/run/secrets/firebase-adminsdk.json` (đường dẫn **trong container**).

Chưa muốn dùng đăng nhập Google? Để trống mọi biến `FIREBASE_*`. Nút Google/Facebook hiện trạng thái tắt, phần còn lại (đăng ký, đăng nhập, ảnh...) vẫn chạy đủ.

### 4.1. Ảnh người dùng lưu trên VPS
- Avatar và ảnh bìa được lưu trong thư mục `/app/uploads` của container `app`, gắn vào Docker volume `uploads`. **Tạo lại container không làm mất ảnh**, nhưng `docker compose down -v` hoặc xóa volume thì mất.
- Mỗi ảnh tối đa 5 MB, chỉ nhận JPG/PNG/GIF/WEBP (kiểm tra bằng nội dung thật của file, không nhận SVG). Tên file do server sinh và không bao giờ đổi nội dung, nên trình duyệt được phép cache tới một năm.
- Khi khởi động, app kiểm tra thư mục này ghi được; nếu không (thường do lỗi quyền volume) app từ chối chạy và báo rõ.
- Ảnh chiếm dung lượng đĩa của VPS, hãy thỉnh thoảng xem còn bao nhiêu: `df -h /` và `docker system df -v`. Sao lưu ảnh đã nằm trong `deploy/backup.sh` (mục 8).
- **Ảnh/video trong bài đăng** cũng nằm trong volume này: tối đa 4 ảnh (mỗi ảnh ≤ 8 MB) hoặc 1 video MP4/WEBM ≤ 25 MB mỗi bài. Ảnh được xóa EXIF/vị trí và thu nhỏ về tối đa 1920 px; video giữ nguyên (chưa xóa metadata). Caddy cho phép request tới 40 MB (`request_body max_size` trong `deploy/Caddyfile`). Video ngốn đĩa nhanh nhất, nên theo dõi `df -h /` thường xuyên khi có nhiều người dùng.

## 5. Email quên mật khẩu (Brevo hoặc Resend)
Cả hai đều cần xác minh domain để email không vào spam:

- **Brevo**: *Senders, Domains & IPs → Domains → Add a domain*, rồi thêm đúng các bản ghi DNS Brevo hiển thị. *SMTP & API → SMTP → Generate a new SMTP key*. `MAIL_USER` là **SMTP login** hiện ở trang đó (thường dạng `xxxx@smtp-brevo.com`), `MAIL_PASSWORD` là SMTP key vừa tạo.
- **Resend**: *Domains → Add Domain*, thêm bản ghi DNS. *API Keys → Create* (quyền Sending access). `MAIL_HOST=smtp.resend.com`, `MAIL_USER=resend`, `MAIL_PASSWORD=<API key>`.

Trong Cloudflare DNS, các bản ghi này phải để **DNS only (đám mây xám)**, không proxy. Dùng cổng **587** (STARTTLS), profile `prod` đã bật sẵn. `ALOUTE_MAIL_FROM` phải thuộc domain đã xác minh.

> App cố ý **không báo lỗi gửi mail ra giao diện** (để không lộ email nào tồn tại). Nếu thư không tới, xem log: `docker compose -f docker-compose.prod.yml logs app | grep "Không gửi được email"`.

## 6. Cấu hình và chạy

```bash
cd ~/aloute
cp .env.prod.example .env && chmod 600 .env
openssl rand -base64 48     # dán vào ALOUTE_JWT_SECRET
openssl rand -base64 24     # dán vào DB_PASSWORD
nano .env                   # điền DOMAIN, ACME_EMAIL, ALOUTE_BASE_URL, SMTP, Firebase, admin (mục 6.1)
```

Hai biến này phải khớp nhau: `DOMAIN=aloute.example.com` (không có `https://`, để Caddy xin chứng chỉ) và `ALOUTE_BASE_URL=https://aloute.example.com` (để app tạo link trong email).

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps                 # app phải "healthy"
docker compose -f docker-compose.prod.yml logs -f caddy      # thấy "certificate obtained successfully"
docker compose -f docker-compose.prod.yml logs -f app        # thấy "Started AlouteApplication"
```

Lần build đầu mất vài phút. Nếu `app` dừng ngay khi khởi động với dòng *"Cấu hình production chưa an toàn"*, đó là chốt chặn an toàn đang liệt kê chính xác biến nào sai, sửa `.env` rồi chạy lại.

> Đang thử nghiệm và sợ chạm giới hạn của Let's Encrypt? Tạm thêm vào đầu khối `{ ... }` ở `deploy/Caddyfile` dòng `acme_ca https://acme-staging-v02.api.letsencrypt.org/directory` (chứng chỉ thử nghiệm, trình duyệt sẽ cảnh báo), rồi bỏ đi khi chạy thật.

### 6.1. Tài khoản admin
- Lần đầu, đặt `ALOUTE_SEED_PASSWORD` (≥ 12 ký tự) và `ALOUTE_SEED_ADMIN_EMAIL` (**email thật của bạn**). App tạo admin với username `admin`.
- Đăng nhập tại `https://<domain>/login` bằng email đó, vào *Cài đặt* đổi mật khẩu.
- **Xóa dòng `ALOUTE_SEED_PASSWORD` khỏi `.env`** rồi áp dụng lại: `docker compose -f docker-compose.prod.yml up -d` (lệnh `restart` không nạp lại `.env`).

## 7. (Tùy chọn) Bật proxy Cloudflare
Thêm lớp bảo vệ của Cloudflare (chống DDoS, ẩn IP VPS, WAF, Bot Fight Mode) phía trước Caddy:

1. Lấy dải IP Cloudflare và dán vào `.env`:
   ```bash
   ./deploy/cloudflare-ips.sh        # in ra dòng CADDY_TRUSTED_PROXIES=...
   ```
   Biến này cho Caddy biết chỉ tin `CF-Connecting-IP` khi kết nối đến từ đúng những dải này. Áp dụng: `docker compose -f docker-compose.prod.yml up -d`.
2. Cloudflare → DNS: chuyển bản ghi `A` sang **Proxied** (đám mây cam).
3. Cloudflare → SSL/TLS → chọn chế độ **Full (strict)** (Cloudflare kiểm tra chứng chỉ Let's Encrypt của Caddy).
4. (Nên làm) Chỉ cho Cloudflare vào cổng web, để không ai gọi thẳng vào IP VPS được:
   ```bash
   sudo ufw delete allow 80/tcp && sudo ufw delete allow 443/tcp && sudo ufw delete allow 443/udp
   ./deploy/cloudflare-ips.sh --ufw | bash
   ```
5. Dải IP của Cloudflare thỉnh thoảng đổi: **chạy lại bước 1 mỗi vài tháng**. Nếu danh sách cũ, IP người dùng bị nhận thành IP Cloudflare, mọi người dùng chung một IP và giới hạn đăng nhập sai sẽ khóa cả nhóm cùng lúc.
6. Cloudflare → Security → Bots → bật **Bot Fight Mode**. Rate limiting rule cho `/login` và `/forgot-password` tùy gói (gói Free bị giới hạn cấu hình); app đã có giới hạn riêng: 5 lần sai mỗi cặp IP + tài khoản và 20 lần mỗi IP trong 15 phút.

Muốn quay lại DNS only: chuyển bản ghi về đám mây xám, bỏ giá trị `CADDY_TRUSTED_PROXIES`, mở lại cổng 80/443 ở `ufw`.

## 8. Vận hành

**Cập nhật phiên bản mới** (Flyway tự nâng cấp database, chỉ đi một chiều nên **sao lưu trước**):
```bash
cd ~/aloute && ./deploy/backup.sh && git pull
docker compose -f docker-compose.prod.yml up -d --build
docker image prune -f
```

**Sao lưu tự động** (`crontab -e`, chạy 3 giờ sáng mỗi ngày, giữ 14 bản mới nhất trong `~/aloute/backups`):
```
0 3 * * * /home/deploy/aloute/deploy/backup.sh >> /home/deploy/aloute-backup.log 2>&1
```
Mỗi lần chạy tạo hai file trong `backups/`: `aloute-db-<thời-điểm>.dump` (database) và `aloute-uploads-<thời-điểm>.tgz` (ảnh người dùng), giữ 14 bản mới nhất cho mỗi loại. Nên định kỳ chép thư mục `backups/` ra ngoài VPS (máy bạn hoặc kho lưu trữ đám mây): một bản sao lưu nằm cùng ổ đĩa không cứu được khi ổ đĩa hỏng. Chứng chỉ nằm trong volume `caddy_data`: đừng xóa volume này (mất là phải xin lại và có thể chạm giới hạn của Let's Encrypt).

**Thử khôi phục** (làm thử ít nhất một lần, một bản sao lưu chưa từng khôi phục thì chưa chắc dùng được):
```bash
C="docker compose -f docker-compose.prod.yml exec -T postgres"
$C createdb -U aloute aloute_restore_test
$C pg_restore -U aloute -d aloute_restore_test --no-owner < backups/aloute-db-<thời-điểm>.dump
$C psql -U aloute -d aloute_restore_test -c "select count(*) from users"
$C dropdb -U aloute aloute_restore_test
```
Khôi phục thật: `docker compose -f docker-compose.prod.yml stop app`, xóa và tạo lại database `aloute`, chạy `pg_restore` vào đó, rồi `start app`.

**Khôi phục ảnh** (ví dụ khi lỡ mất volume `uploads`): app phải đang chạy, rồi

```bash
docker compose -f docker-compose.prod.yml exec -T app tar xzf - -C /app < backups/aloute-uploads-<thời-điểm>.tgz
```

**Xem log / khởi động lại**:
```bash
docker compose -f docker-compose.prod.yml logs --tail=200 app
docker compose -f docker-compose.prod.yml restart app      # người dùng vẫn đăng nhập vì phiên nằm trong JWT
```

## 9. Kiểm tra sau khi deploy
1. `https://<domain>` mở được, có ổ khóa; gõ `http://` thì tự chuyển sang `https://`.
2. Đăng ký tài khoản, đăng nhập, đăng xuất. DevTools → Application → Cookies: `ALOUTE_TOKEN` có **HttpOnly** và **Secure**.
3. Cố tình sai mật khẩu 5 lần: bị chặn. Từ một mạng khác (điện thoại dùng 4G) vẫn đăng nhập được, tức là app phân biệt đúng IP người dùng.
4. *Quên mật khẩu*: thư tới hộp thư thật, link dùng được một lần.
5. Đổi ảnh đại diện: ảnh hiển thị (địa chỉ bắt đầu bằng `/uploads/avatars/`). Chạy `docker compose -f docker-compose.prod.yml up -d --force-recreate app`, tải lại trang: ảnh vẫn còn.
6. Đăng nhập bằng Google thành công ở domain thật.
7. `docker compose -f docker-compose.prod.yml restart app` rồi tải lại trang: vẫn đăng nhập, dữ liệu còn nguyên.
8. Chạy `./deploy/backup.sh` và thử khôi phục như mục 8 (cả database lẫn ảnh).
9. Thử cổng lạ từ máy bên ngoài, ví dụ `nc -zv <IP-VPS> 8080` và `nc -zv <IP-VPS> 5432`: phải **không** kết nối được.

## 10. Khắc phục sự cố
| Triệu chứng | Nguyên nhân thường gặp và cách xử lý |
|---|---|
| Trình duyệt báo chứng chỉ không hợp lệ / Caddy log có lỗi *obtaining certificate* | DNS chưa trỏ đúng về VPS (`dig +short <domain>`), cổng 80/443 bị chặn (cả `ufw` lẫn tường lửa của nhà cung cấp VPS), hoặc có bản ghi `AAAA` sai. Xem `docker compose ... logs caddy`. |
| Caddy log báo *too many failed authorizations* | Đã thử sai quá nhiều lần với Let's Encrypt. Đợi khoảng một giờ, hoặc dùng `acme_ca` của môi trường staging khi thử nghiệm (mục 6). |
| Trang báo 502 Bad Gateway | `app` chưa sẵn sàng hoặc đang lỗi: `docker compose ... ps`, `logs app`. Caddy tự thử lại tới 5 giây khi app khởi động lại. |
| `app` thoát ngay, log có "Cấu hình production chưa an toàn" | Chốt chặn cấu hình đang liệt kê biến sai, sửa `.env` theo từng dòng rồi `up -d`. |
| `app` từ chối chạy với dòng *Thư mục lưu ảnh ... không ghi được* | Lỗi quyền của thư mục ảnh. Với volume mặc định thì hiếm gặp; nếu bạn đã đổi sang thư mục của máy chủ (bind mount) thì `sudo chown -R 1000:1000 <thư-mục>`. |
| `app` báo lỗi đọc file Firebase / `Permission denied` | File khóa phải nằm ở `secrets/`, chủ là user uid 1000 (`id -u` = 1000) và `chmod 600`; đường dẫn trong `.env` là đường dẫn *trong container* (`/run/secrets/...`). |
| Ai đó đăng nhập sai vài lần là **mọi người** bị khóa | App không nhận được IP thật. Nếu bật proxy Cloudflare: `CADDY_TRUSTED_PROXIES` đang trống hoặc danh sách IP đã cũ (chạy lại `./deploy/cloudflare-ips.sh`). Kiểm tra thêm `app` không bị publish cổng. |
| Nút Google báo `auth/unauthorized-domain` | Chưa thêm domain vào Firebase Authentication → Authorized domains. |
| Đăng nhập xong bị đá ra ngay | Cookie `Secure` cần HTTPS: truy cập bằng `https://`, và `ALOUTE_BASE_URL` phải là `https://...`. |
| Thư quên mật khẩu không tới | Xem log tìm "Không gửi được email"; kiểm tra `MAIL_*`, domain đã xác minh, `ALOUTE_MAIL_FROM` thuộc domain đó, bản ghi DNS để *DNS only*. |
| Build bị `Killed` / hết bộ nhớ | Thiếu RAM: kiểm tra swap (`free -h`), hoặc build ở máy khác rồi đẩy image lên registry. |
| Muốn xem database | `docker compose -f docker-compose.prod.yml exec postgres psql -U aloute -d aloute` |

## 11. Chưa làm (để sau)
Lưu ảnh lên object storage (S3, Cloudflare R2, Backblaze B2) khi cần nhiều dung lượng, tự động deploy bằng GitHub Actions + GHCR, giám sát và cảnh báo khi web sập (Uptime Kuma), Content-Security-Policy nghiêm ngặt, chạy nhiều bản `app` cùng lúc (giới hạn đăng nhập hiện nằm trong bộ nhớ của từng tiến trình), log truy cập của Caddy (cần cân nhắc quyền riêng tư vì có IP người dùng).
