package com.relay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class CatcherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val u = intent?.data
            if (u != null) {
                val token = u.getQueryParameter("autostarttoken")
                if (token != null && token.isNotEmpty()) {
                    val p = getSharedPreferences("relay", Context.MODE_PRIVATE)
                    val m = p.getInt("mode", 1)
                    if (token != p.getString("last", null)) {
                        p.edit().putString("last", token).apply()
                        val redirect = u.getQueryParameter("redirect")
                        if (redirect != null && redirect.startsWith("http")) {
                            p.edit().putString("redirect", redirect).apply()
                        }
                        var s = p.getString("server", "") ?: ""
                        val code = p.getString("code", "") ?: ""
                        if (s.isNotEmpty() && m != 2) {
                            if (!s.startsWith("http")) s = "https://$s"
                            val b = s.trimEnd('/')
                            thread {
                                try {
                                    val c = URL(b + "/api/token").openConnection() as HttpURLConnection
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
                                    c.responseCode
                                } catch (t: Throwable) {
                                }
                            }
                        }
                        if (m != 0) {
                            try {
                                val i = Intent(Intent.ACTION_VIEW, Uri.parse("bankid:///?autostarttoken=" + token))
                                i.setPackage("com.bankid.bus")
                                startActivity(i)
                            } catch (t: Throwable) {
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
        }
        finish()
    }
}
