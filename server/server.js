const express = require('express');
const path = require('path');
const app = express();

app.use(express.text({ type: '*/*' }));

const S = {};
const LIV = 10 * 60 * 1000;

function ny() {
  let c;
  do { c = Math.random().toString(36).slice(2, 7).toUpperCase(); } while (S[c]);
  return c;
}

function stada() {
  const nu = Date.now();
  for (const k of Object.keys(S)) {
    if (nu - S[k].skapad > LIV) delete S[k];
  }
}
setInterval(stada, 60000);

function giltigToken(t) {
  return typeof t === 'string' && /^[0-9a-fA-F-]{8,}$/.test(t);
}

app.post('/api/session', (req, res) => {
  const c = ny();
  S[c] = { token: '', at: 0, signed: false, skapad: Date.now() };
  res.json({ ok: true, code: c });
});

app.get('/api/session/:code/state', (req, res) => {
  const s = S[req.params.code];
  if (!s) { res.json({ ok: false }); return; }
  const ttl = s.token ? Math.max(0, Math.round((s.at + 30000 - Date.now()) / 1000)) : 0;
  res.json({ ok: true, token: s.token, ttl: ttl, signed: s.signed });
});

app.post('/api/session/:code/token', (req, res) => {
  let s = S[req.params.code];
  if (!s) { s = { token: '', at: 0, signed: false, skapad: Date.now() }; S[req.params.code] = s; }
  const t = String(req.body || '').trim();
  if (!giltigToken(t)) { res.json({ ok: false }); return; }
  s.token = t;
  s.at = Date.now();
  s.signed = false;
  res.json({ ok: true });
});

app.get('/api/signed', (req, res) => {
  const s = S[req.query.code];
  if (s) s.signed = true;
  res.send('<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">' +
    '<body style="font-family:sans-serif;text-align:center;padding:48px">' +
    '<h2>Klart</h2><p>Du kan stanga sidan.</p></body>');
});

app.get('/k/:code', (req, res) => {
  if (!S[req.params.code]) { res.status(404).send('okand kod'); return; }
  res.sendFile(path.join(__dirname, 'public', 'k.html'));
});

app.get('/', (req, res) => { res.send('relay ok'); });

app.listen(3000, () => console.log('relay pa :3000'));
