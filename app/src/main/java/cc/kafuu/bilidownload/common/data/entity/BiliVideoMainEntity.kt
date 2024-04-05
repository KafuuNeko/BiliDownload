package cc.kafuu.bilidownload.common.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class BiliVideoMainEntity(
    @PrimaryKey val bvid: String,
    val title: String,
    val description: String,
    val cover: String,
)