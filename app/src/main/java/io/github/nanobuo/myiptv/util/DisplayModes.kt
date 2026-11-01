package io.github.nanobuo.myiptv.util

import android.app.Activity
import android.os.Build
import android.view.Display
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Automatic refresh rate switching for TV: picks the display mode with the same resolution
 * whose refresh rate is a whole multiple of the video frame rate (25 fps → 50 Hz, 23.976 → 23.976 Hz).
 */
object DisplayModes {

    private fun Activity.currentDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay

    /** Refresh rate of the screen right now, in Hz. */
    fun refreshRate(activity: Activity): Float? = activity.currentDisplay()?.refreshRate

    /** Requests the best mode for [frameRate], or the default mode when null. Returns true when the mode changes. */
    fun apply(activity: Activity, frameRate: Float?): Boolean {
        val display = activity.currentDisplay() ?: return false
        val window = activity.window ?: return false
        val attrs = window.attributes
        val target = if (frameRate == null || frameRate <= 0f) 0 else bestMode(display, frameRate)?.modeId ?: return false
        if (attrs.preferredDisplayModeId == target) return false
        if (target != 0 && display.mode.modeId == target && attrs.preferredDisplayModeId == 0) return false
        attrs.preferredDisplayModeId = target
        window.attributes = attrs
        return true
    }

    private fun bestMode(display: Display, frameRate: Float): Display.Mode? {
        val current = display.mode
        return display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .filter { isMultiple(it.refreshRate, frameRate) }
            // Prefer refresh rates the TV handles natively (≥ 47 Hz), then the lowest one.
            .sortedWith(compareBy<Display.Mode>({ it.refreshRate < 47f }, { it.refreshRate }))
            .firstOrNull()
    }

    private fun isMultiple(refresh: Float, fps: Float): Boolean {
        val ratio = refresh / fps
        val whole = ratio.roundToInt()
        return whole >= 1 && abs(ratio - whole) < 0.01f
    }
}
