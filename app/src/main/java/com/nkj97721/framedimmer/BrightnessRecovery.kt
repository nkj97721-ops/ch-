package com.nkj97721.framedimmer

import android.content.Context
import android.provider.Settings

/**
 * Persists the pre-intervention brightness before changing it. If Android kills
 * the process while dimmed, MainActivity or the boot receiver can restore it later.
 */
object BrightnessRecovery {
    private const val PREFS = "brightness_recovery"
    private const val KEY_MODE = "saved_mode"
    private const val KEY_VALUE = "saved_value"

    fun save(context: Context, mode: Int, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MODE, mode)
            .putInt(KEY_VALUE, value)
            .commit()
    }

    fun restore(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_MODE) || !prefs.contains(KEY_VALUE)) return false
        return try {
            val value = prefs.getInt(KEY_VALUE, 128)
            val mode = prefs.getInt(KEY_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            if (!Settings.System.canWrite(context)) return false
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            prefs.edit().clear().commit()
            true
        } catch (_: Exception) {
            false
        }
    }
}
