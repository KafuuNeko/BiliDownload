package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import cc.kafuu.bilidownload.common.constant.SearchType

@Entity(tableName = "SearchRecord")
data class SearchRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val time: Long = System.currentTimeMillis(),
    val keyword: String,
    @SearchType val searchType: Int
)
