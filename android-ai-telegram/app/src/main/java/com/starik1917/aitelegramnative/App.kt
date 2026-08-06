package com.starik1917.aitelegramnative

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Telegram only returns updates that have not already been confirmed server-side.
        // A persisted offset can become invalid after changing/reconnecting a bot and
        // make fresh Business updates appear to vanish. Start each app process from 0;
        // already-confirmed updates are not returned again by Telegram.
        getSharedPreferences("settings", MODE_PRIVATE)
            .edit()
            .putLong("update_offset", 0L)
            .apply()
    }
}
