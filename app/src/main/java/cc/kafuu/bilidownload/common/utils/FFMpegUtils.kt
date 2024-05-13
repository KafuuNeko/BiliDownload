package cc.kafuu.bilidownload.common.utils

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode

object FFMpegUtils {
    private const val TAG = "FFMpegUtils"

    fun mergeMedia(input1: String, input2: String, output: String): Boolean {
        val command = "-i \"$input1\" -i \"$input2\" -c:v copy -c:a copy -strict experimental \"$output\""
        Log.d(TAG, "mergeMedia: $command" )
        val session = FFmpegKit.execute(command)
        return ReturnCode.isSuccess(session.returnCode)
    }
}