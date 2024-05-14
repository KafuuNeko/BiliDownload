package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity

@Entity(primaryKeys = ["biliBvid", "biliCid"], tableName = "BiliVideoPart")
data class BiliVideoPartEntity(
    val biliBvid: String,
    val biliCid: Long,
    val partTitle: String,
)