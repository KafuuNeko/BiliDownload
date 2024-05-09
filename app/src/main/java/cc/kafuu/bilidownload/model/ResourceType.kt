package cc.kafuu.bilidownload.model

import androidx.annotation.IntDef
@IntDef(ResourceType.VIDEO, ResourceType.AUDIO)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class ResourceType {
    companion object {
        const val VIDEO = 0
        const val AUDIO = 1
    }
}
