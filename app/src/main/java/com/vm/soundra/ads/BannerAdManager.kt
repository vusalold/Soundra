package com.vm.soundra.ads

import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

object BannerAdManager {
    private const val TAG = "ADMOB"

    @Composable
    fun AdaptiveBannerAd(modifier: Modifier = Modifier) {
        if (!AdConstants.ADS_ENABLED) return
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { ctx ->
                Log.d(TAG, "Creating AdView")
                AdView(ctx).apply {
                    adUnitId = AdConstants.BANNER_AD_UNIT_ID
                    val adSize = getAdSize(ctx)
                    Log.d(TAG, "Setting Ad Size: $adSize")
                    setAdSize(adSize)
                    
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            Log.d(TAG, "Banner Loaded Successfully")
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.e(TAG, "Banner Load Failed!")
                            Log.e(TAG, "Code: ${error.code}")
                            Log.e(TAG, "Message: ${error.message}")
                            Log.e(TAG, "Domain: ${error.domain}")
                            Log.e(TAG, "Response Info: ${error.responseInfo}")
                        }

                        override fun onAdOpened() {
                            Log.d(TAG, "Banner Opened (Covering the screen)")
                        }

                        override fun onAdClicked() {
                            Log.d(TAG, "Banner Clicked")
                        }

                        override fun onAdClosed() {
                            Log.d(TAG, "Banner Closed")
                        }

                        override fun onAdImpression() {
                            Log.d(TAG, "Banner Impression Recorded")
                        }
                    }

                    Log.d(TAG, "Loading Banner Ad")
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }

    private fun getAdSize(context: Context): AdSize {
        // Modern approach using WindowMetrics for current window width
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val displayMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            android.graphics.Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
        }

        val density = context.resources.displayMetrics.density
        val adWidthPixels = bounds.width().toFloat()
        val adWidth = (adWidthPixels / density).toInt()

        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
    }
}
