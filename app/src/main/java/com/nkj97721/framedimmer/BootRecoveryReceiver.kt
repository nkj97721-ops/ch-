package com.nkj97721.framedimmer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            BrightnessRecovery.restore(context)
        }
    }
}
