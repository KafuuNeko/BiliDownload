package cc.kafuu.bilidownload.common.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    @SuppressLint("DefaultLocale")
    fun formatSecondTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    fun formatTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    @SuppressLint("DefaultLocale")
    fun formatDuration(durationInSeconds: Double): String {
        val hours = (durationInSeconds / 3600).toInt()
        val minutes = ((durationInSeconds % 3600) / 60).toInt()
        val seconds = (durationInSeconds % 60).toInt()
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun evalFrameRate(frameRate: String): Double {
        val parts = frameRate.split("/")
        return if (parts.size == 2) {
            val numerator = parts[0].toDoubleOrNull()
            val denominator = parts[1].toDoubleOrNull()
            if (numerator != null && denominator != null && denominator != 0.0) {
                numerator / denominator
            } else {
                0.0
            }
        } else {
            frameRate.toDoubleOrNull() ?: 0.0
        }
    }
}