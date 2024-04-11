package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class BiliVideoMainEntity(
    @PrimaryKey val biliBvid: String,
    val title: String,
    val description: String,
    val cover: String,
)