const express = require('express');
const path = require('path');
const app = express();

app.use(express.text({ type: '*/*' }));

const S = {};

function ny() {
  let c;
  do { c = Math.random().toString(36).slice(2, 7).toUpperCase(); } while (S[c]);
  return c;
}

app.post('/api/session', (req, res) => {
  const c = ny();
  S[c] = { token: '', at: 0 };
  res.json({ ok: true, code: c });
});

app.get('/api/session/:code/state', (req, res) => {
  const s = S[req.params.code];
  if (!s) { res.json({ ok: false }); return; }
  const ttl = s.token ? Math.max(0, Math.round((s.at + 30000 - Date.now()) / 1000)) : 0;
  res.json({ ok: true, token: s.token, ttl: ttl });
});

app.post('/api/session/:code/token', (req, res) => {
  let s = S[req.params.code];
  if (!s) { s = { token: '', at: 0 }; S[req.params.code] = s; }
  s.token = String(req.body || '').trim();
  s.at = Date.now();
  res.json({ ok: true });
});

app.get('/api/signed', (req, res) => {
  res.send('<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">' +
    '<body style="font-family:sans-serif;text-align:center;padding:48px">' +
    '<h2>Klart</h2><p>Du kan stanga sidan.</p></body>');
});

app.get('/k/:code', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'k.html'));
});

app.get('/', (req, res) => { res.send('relay ok'); });

app.listen(3000, () => console.log('relay pa :3000'));
