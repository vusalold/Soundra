package com.vusal.soundra.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

class InstallerManager(private val context: Context) {
    private val TAG = "APK_INSTALLER"

    fun installApk(file: File) {
        Log.d(TAG, "Installer Started")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Log.d(TAG, "Permission Required")
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            Log.d(TAG, "APK Uri Created: $contentUri")

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                setDataAndType(contentUri, "application/vnd.android.package-archive")
            }
            
            Log.d(TAG, "Opening Installer")
            context.startActivity(installIntent)
            Log.d(TAG, "Installation Intent Sent")
        } catch (e: Exception) {
            Log.e(TAG, "Installation Failed: ${e.message}")
        }
    }
}
