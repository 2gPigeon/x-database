package com.example.x_database

import android.content.Context

object ShareSaveGate {
    private const val PREFS = "share_save_gate"
    private const val KEY_SAVING = "saving"

    fun tryAcquire(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        synchronized(this) {
            val saving = prefs.getBoolean(KEY_SAVING, false)
            if (saving) {
                return false
            }
            prefs.edit().putBoolean(KEY_SAVING, true).apply()
            return true
        }
    }

    fun release(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SAVING, false).apply()
    }

    fun reset(context: Context) {
        release(context)
    }
}
