#!/data/data/com.termux/files/usr/bin/bash
# Startar webblasare, tunnel och server. Haller dem vid liv.
cd "$(dirname "$0")"
termux-wake-lock 2>/dev/null

BROWSER="$HOME/hs"
TLOG="$HOME/tunnel.log"

starta_browser() {
  curl -s --max-time 2 http://127.0.0.1:9222/json/version >/dev/null 2>&1 && return
  echo "startar webblasare..."
  nohup headless_shell --no-sandbox --disable-gpu --disable-dev-shm-usage \
    --remote-debugging-address=127.0.0.1 --remote-debugging-port=9222 \
    --remote-allow-origins='*' --user-data-dir="$BROWSER" \
    about:blank > "$HOME/chrome.log" 2>&1 &
  sleep 6
}

starta_tunnel() {
  pgrep -f "cloudflared tunnel" >/dev/null 2>&1 && return
  echo "startar tunnel..."
  : > "$TLOG"
  nohup cloudflared tunnel --url http://localhost:3000 --protocol http2 \
    --no-autoupdate > "$TLOG" 2>&1 &
  sleep 12
}

hitta_url() {
  grep -ao 'https://[a-z0-9-]*\.trycloudflare\.com' "$TLOG" 2>/dev/null | tail -1
}

starta_browser
starta_tunnel
PUBLIC_URL="$(hitta_url)"
echo "KUNDLANK-BAS: $PUBLIC_URL"

while true; do
  starta_browser
  if ! pgrep -f "cloudflared tunnel" >/dev/null 2>&1; then
    starta_tunnel
    PUBLIC_URL="$(hitta_url)"
    echo "NY KUNDLANK-BAS: $PUBLIC_URL"
  fi
  [ -d node_modules ] || npm install --no-audit --no-fund
  PUBLIC_URL="$PUBLIC_URL" node server.js
  echo "servern dog - startar om"
  sleep 3
done
