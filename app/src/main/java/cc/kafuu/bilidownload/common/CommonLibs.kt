package cc.kafuu.bilidownload.common

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.annotation.ArrayRes
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import cc.kafuu.bilidownload.common.room.AppDatabase
import java.io.File
import androidx.core.net.toUri


@SuppressLint("StaticFieldLeak")
object CommonLibs {
    private var mContext: Context? = null

    private var mAppDatabase: AppDatabase? = null

    fun init(context: Context) {
        mContext = context.applicationContext
        mAppDatabase = AppDatabase.requireInstance(context)
    }

    fun getString(@StringRes id: Int) =
        requireContext().resources?.getString(id).toString()

    fun getString(@StringRes id: Int, vararg args: Any) =
        requireContext().resources?.getString(id, *args).toString()

    fun getColor(@ColorRes color: Int) = ContextCompat.getColor(requireContext(), color)

    fun getDrawable(@DrawableRes id: Int) = ContextCompat.getDrawable(requireContext(), id)

    fun getStringArray(@ArrayRes id: Int): Array<String> =
        requireContext().resources.getStringArray(id)

    fun requireContext() = mContext ?: throw IllegalStateException("Program not running")

    fun requireAppDatabase() = mAppDatabase ?: throw IllegalStateException("Program not running")

    private fun requireExternalFilesDir(type: String, relativePath: String = ""): File {
        val root = requireContext().getExternalFilesDir(type)
            ?: throw IllegalStateException("$type directory is null")

        return File(root, relativePath).apply {
            if (!isDirectory && !mkdirs() && !isDirectory) {
                throw IllegalStateException("Directory $this cannot be created")
            }
        }
    }

    fun requireDownloadCacheDir(entityId: Long) =
        requireExternalFilesDir("cache", "download/task-e$entityId")

    /**
     * 下载和 FFmpeg 处理文件始终存放在应用专属目录中。
     * 完成后的资源由 ResourceStorage 发布到公共下载目录。
     */
    fun requireResourceWorkingDir() = requireExternalFilesDir("resources")

    @Suppress("DEPRECATION")
    fun getPublicResourcesDir(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        return File(downloadDir, "BVD")
    }

    fun requirePublicResourcesDir(): File {
        return getPublicResourcesDir().apply {
            if (!isDirectory && !mkdirs() && !isDirectory) {
                throw IllegalStateException("Directory $this cannot be created")
            }
        }
    }

    /**
     * 返回公共影片目录下的 BVD 资源目录。
     *
     * 此方法只计算目录位置，不负责创建目录；目录创建由实际发布流程完成。
     */
    @Suppress("DEPRECATION")
    fun getPublicVideoResourcesDir(): File {
        val moviesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES
        )
        return File(moviesDir, "BVD")
    }

    fun requireConvertTemporaryDir() = requireExternalFilesDir("temporary", "convert")

    fun getVersionName(): String {
        return requireContext().packageManager.getPackageInfo(
            requireContext().packageName, 0
        ).versionName ?: "Unknown"
    }

    fun jumpToUrl(url: String) {
        Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }.also {
            requireContext().startActivity(it)
        }
    }

    fun copyToClipboard(label: String, text: String): Boolean {
        val clipboard = mContext?.getSystemService(Context.CLIPBOARD_SERVICE)
        if (clipboard != null && clipboard is ClipboardManager) {
            clipboard.setPrimaryClip(ClipData.newPlainText("label", text))
            return true
        }
        return false
    }
}
