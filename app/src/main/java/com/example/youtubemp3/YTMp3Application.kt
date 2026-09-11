package com.vusal.soundra

import android.app.Application
import android.util.Log
import com.vusal.soundra.logic.ExtractorHelper

class SoundraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Initialize NewPipe Extractor globally at app startup
            ExtractorHelper.init(this)
        } catch (e: Exception) {
            Log.e("SoundraApp", "Extractor init failed", e)
        }
    }
}
