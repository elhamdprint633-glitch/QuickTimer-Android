package com.quicktimer.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("running", false)
            .putBoolean("ringing", true)
            .putLong("endAt", 0L)
            .putLong("remaining", 0L)
            .apply()

        val serviceIntent = Intent(context, AlarmService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
        } catch (_: Exception) {
            // The scheduled alarm remains visible to the user in the system alarm UI.
        }
    }
}
