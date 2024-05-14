package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "BiliVideoMain")
data class BiliVideoMainEntity(
    @PrimaryKey val biliBvid: String,
    val author: String,
    val authorId: Long,
    val title: String,
    val description: String,
    val cover: String,
)