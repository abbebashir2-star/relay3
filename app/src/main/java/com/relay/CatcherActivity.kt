package com.relay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.net.HttpURLConnection
import java.net.URL

class CatcherActivity : Activity() {

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val p = getSharedPreferences("relay", 0)
        val str = intent?.data?.toString() ?: ""
        val m = Regex("[?&]autostarttoken=([0-9a-fA-F-]{36})").find(str)
        val t = if (m == null) null else m.groupValues[1]
        val c = p.getString("code", "") ?: ""
        val b = (p.getString("base", "") ?: "").trim().trimEnd('/')
        val kund = p.getBoolean("kund", true)

        if (t != null && c.isNotEmpty() && b.isNotEmpty()) {
            Thread {
                try {
                    val con = URL(b + "/api/session/" + c + "/token").openConnection() as HttpURLConnection
                    con.requestMethod = "POST"
                    con.doOutput = true
                    con.setRequestProperty("Content-Type", "text/plain")
                    con.connectTimeout = 8000
                    con.readTimeout = 8000
                    con.outputStream.write(t.toByteArray())
                    con.outputStream.flush()
                    con.responseCode
                    con.disconnect()
                } catch (x: Throwable) {}
            }.start()
        }

        if (!kund && t != null) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://app.bankid.com/?autostarttoken=" + t + "&redirect=null")))
            } catch (x: Throwable) {}
        }
        finish()
    }
}
