package cc.kafuu.bilidownload.common.data.entity

import androidx.room.Entity

@Entity(primaryKeys = ["bvid", "cid"])
data class BiliVideoPartEntity(
    val bvid: String,
    val cid: Long,
    val partTitle: String,
)