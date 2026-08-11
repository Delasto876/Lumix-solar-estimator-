package com.lumix.estimator.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Mirrors the system "Remove animations" accessibility setting (animator duration
 * scale of 0). Decorative, looping motion should be skipped when this is true;
 * state-change animations that convey information (counters, transitions) can stay.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        scale == 0f
    }
}
