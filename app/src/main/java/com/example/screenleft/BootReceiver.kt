package com.example.screenleft

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Prefs.enabled(c)) runCatching { UsageService.start(c) }
    }
}
