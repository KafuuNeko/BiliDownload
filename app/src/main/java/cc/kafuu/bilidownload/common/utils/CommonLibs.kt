package cc.kafuu.bilidownload.common.utils

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import cc.kafuu.bilidownload.common.room.database.AppDatabase
import java.io.File

@SuppressLint("StaticFieldLeak")
object CommonLibs {
    private var mContext: Context? = null

    private var mAppDatabase: AppDatabase? = null

    fun init(context: Context) {
        mContext = context
        mAppDatabase = AppDatabase.getDatabase(context)
    }

    fun getString(@StringRes id: Int) = requireContext().resources?.getString(id).toString()

    fun getColor(@ColorRes color: Int) = ContextCompat.getColor(requireContext(), color)

    fun getDrawable(@DrawableRes id: Int) = ContextCompat.getDrawable(requireContext(), id)

    fun getStringArray(@ArrayRes id: Int): Array<String> = requireContext().resources.getStringArray(id)

    fun requireContext() = mContext ?: throw IllegalStateException("Program not running")

    fun requireAppDatabase() = mAppDatabase ?: throw IllegalStateException("Program not running")

    private fun requireExternalFilesDir(type: String, relativePath: String = ""): File {
        val root = requireContext().getExternalFilesDir(type)
            ?: throw IllegalStateException("$type directory is null")

        return File(root, relativePath).apply {
            if (!(exists() || mkdirs())) {
                throw IllegalStateException("Directory $this cannot be created")
            }
        }
    }

    fun requireDownloadCacheDir(entityId: Long) = requireExternalFilesDir("cache", "download/task-e$entityId")
    fun requireResourcesDir() = requireExternalFilesDir("resources")

    fun getVersionName(): String = requireContext().packageManager.let {
        it.getPackageInfo(requireContext().packageName, 0).versionName
    }
}