#!/usr/bin/env bash
# One-shot deploy of the Iraqi chat server on a fresh Ubuntu 22.04/24.04 VPS.
# Run as root from inside the server/ folder of a git checkout:  bash deploy.sh
# Optional TLS/nginx:  DOMAIN=chat.example.com bash deploy.sh
set -euo pipefail

APP_DIR=/opt/alichat
DOMAIN="${DOMAIN:-}"

echo "==> Installing Node.js 20 + nginx"
if ! command -v node >/dev/null; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi
apt-get install -y nginx rsync

echo "==> Creating service user + directories"
id alichat >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin alichat
mkdir -p "$APP_DIR/server"
rsync -a --delete --exclude node_modules --exclude data ./ "$APP_DIR/server/"
[ -f "$APP_DIR/server/.env" ] || cp "$APP_DIR/server/.env.example" "$APP_DIR/server/.env"
mkdir -p "$APP_DIR/server/data"

echo "==> Installing dependencies"
cd "$APP_DIR/server"
npm ci --omit=dev || npm install --omit=dev
chown -R alichat:alichat "$APP_DIR"

echo "==> Installing systemd service"
cp "$APP_DIR/server/alichat.service" /etc/systemd/system/alichat.service
systemctl daemon-reload
systemctl enable --now alichat
sleep 2
systemctl --no-pager status alichat | head -n 6 || true

if [ -n "$DOMAIN" ]; then
  echo "==> Configuring nginx for $DOMAIN"
  sed "s/SERVER_NAME/$DOMAIN/g" "$APP_DIR/server/nginx.conf.sample" > /etc/nginx/sites-available/alichat
  ln -sf /etc/nginx/sites-available/alichat /etc/nginx/sites-enabled/alichat
  nginx -t && systemctl reload nginx
  echo "==> For HTTPS run:  apt-get install -y certbot python3-certbot-nginx && certbot --nginx -d $DOMAIN"
fi

PORT=$(grep -E '^PORT=' "$APP_DIR/server/.env" | cut -d= -f2)
echo "==> Done. Health: curl http://127.0.0.1:${PORT}/health"
