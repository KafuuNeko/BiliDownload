package cc.kafuu.bilidownload

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.kafuu.bilidownload.common.room.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 使用 Room 导出的真实结构验证正式数据库迁移及历史数据保留。 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    /** 验证 v3 资源升级到 v4 后原字段不变，新增 contentUri 默认为空。 */
    @Test
    fun migrate3To4PreservesResourceAndAddsNullContentUri() {
        helper.createDatabase(DATABASE_V3, 3).apply {
            execSQL(
                """
                    INSERT INTO DownloadResource(
                        id, taskId, type, name, mimeType,
                        storageSizeBytes, creationTime, file
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    17L,
                    23L,
                    2,
                    "legacy resource",
                    "video/mp4",
                    4096L,
                    123456L,
                    "/legacy/video.mp4"
                )
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_V3, 4, true).use { database ->
            database.query("SELECT * FROM DownloadResource WHERE id = 17").use { cursor ->
                cursor.moveToFirst()
                assertEquals(23L, cursor.getLong(cursor.getColumnIndexOrThrow("taskId")))
                assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("type")))
                assertEquals("legacy resource", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                assertEquals("video/mp4", cursor.getString(cursor.getColumnIndexOrThrow("mimeType")))
                assertEquals(4096L, cursor.getLong(cursor.getColumnIndexOrThrow("storageSizeBytes")))
                assertEquals(123456L, cursor.getLong(cursor.getColumnIndexOrThrow("creationTime")))
                assertEquals("/legacy/video.mp4", cursor.getString(cursor.getColumnIndexOrThrow("file")))
                assertNull(cursor.getString(cursor.getColumnIndexOrThrow("contentUri")))
            }
        }
    }

    /** 验证从 v1 连续迁移到 v4 时重命名字段和关联关系不会丢失。 */
    @Test
    fun migrate1To4RunsCompleteChainAndPreservesRenamedColumns() {
        helper.createDatabase(DATABASE_V1, 1).apply {
            execSQL(
                """
                    INSERT INTO DownloadTask(
                        id, downloadTaskId, status, biliBvid, biliCid, createTime
                    ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(5L, 99L, 3, "BV1legacy", 77L, 654321L)
            )
            execSQL(
                """
                    INSERT INTO DownloadResource(
                        id, taskEntityId, type, name, mimeType,
                        storageSizeBytes, creationTime, file
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    6L,
                    5L,
                    0,
                    "v1 resource",
                    "audio/mp4",
                    2048L,
                    654321L,
                    "/legacy/audio.m4s"
                )
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_V1, 4, true).use { database ->
            database.query("SELECT * FROM DownloadTask WHERE id = 5").use { cursor ->
                cursor.moveToFirst()
                assertEquals(99L, cursor.getLong(cursor.getColumnIndexOrThrow("groupId")))
                assertEquals("BV1legacy", cursor.getString(cursor.getColumnIndexOrThrow("biliBvid")))
                assertEquals(77L, cursor.getLong(cursor.getColumnIndexOrThrow("biliCid")))
            }
            database.query("SELECT * FROM DownloadResource WHERE id = 6").use { cursor ->
                cursor.moveToFirst()
                assertEquals(5L, cursor.getLong(cursor.getColumnIndexOrThrow("taskId")))
                assertEquals("v1 resource", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                assertEquals("/legacy/audio.m4s", cursor.getString(cursor.getColumnIndexOrThrow("file")))
                assertNull(cursor.getString(cursor.getColumnIndexOrThrow("contentUri")))
            }
        }
    }

    private companion object {
        private const val DATABASE_V3 = "migration-v3-to-v4.db"
        private const val DATABASE_V1 = "migration-v1-to-v4.db"
    }
}
