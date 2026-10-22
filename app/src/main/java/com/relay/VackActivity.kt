package com.relay

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class VackActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 500)
    }
}
