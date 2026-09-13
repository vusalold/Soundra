package com.vm.soundra

import android.app.Application
import android.util.Log
import com.vm.soundra.logic.ExtractorHelper

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
