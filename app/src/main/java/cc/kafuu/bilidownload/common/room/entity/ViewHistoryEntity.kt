package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ViewHistory")
data class ViewHistoryEntity(
    @PrimaryKey val bvid: String,
    val title: String,
    val cover: String,
    val author: String,
    val viewTime: Long = System.currentTimeMillis()
)
