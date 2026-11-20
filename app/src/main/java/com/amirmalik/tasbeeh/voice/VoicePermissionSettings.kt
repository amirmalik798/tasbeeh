package com.amirmalik.tasbeeh.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal fun openVoiceAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
