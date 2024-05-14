package cc.kafuu.bilidownload.common.model

import android.content.Intent
import androidx.activity.result.ActivityResult

data class ActivityJumpData(
    val targetIntent: Intent?,
    val targetClass: Class<*>?,
    val finishCurrent: Boolean,
    val activityResult: ActivityResult? = null,
    var isDeprecated: Boolean = false
)
