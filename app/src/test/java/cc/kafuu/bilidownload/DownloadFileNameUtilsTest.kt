package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DownloadFileNameUtilsTest {
    private val context = DownloadFileNameUtils.TemplateContext(
        videoName = "Video/Name:*?",
        partName = "Part<Name>|"
    )

    @Test
    fun buildFileName_replacesTokensAndSanitizesIllegalCharacters() {
        val fileName = DownloadFileNameUtils.buildFileName(
            template = "{VideoName}-{PartName}",
            context = context,
            extension = "mp4",
            fallbackBaseName = "fallback"
        )

        assertEquals("Video_Name-Part_Name.mp4", fileName)
    }

    @Test
    fun buildFileName_fallsBackWhenTemplateIsBlankAfterSanitize() {
        val fileName = DownloadFileNameUtils.buildFileName(
            template = "////",
            context = context,
            extension = "mp4",
            fallbackBaseName = "stream-1"
        )

        assertEquals("stream-1.mp4", fileName)
    }

    @Test
    fun buildFileName_truncatesOverlongNamesByUtf8Bytes() {
        val fileName = DownloadFileNameUtils.buildFileName(
            template = "{VideoName}",
            context = DownloadFileNameUtils.TemplateContext(
                videoName = "测".repeat(120),
                partName = "P1"
            ),
            extension = "mp4",
            fallbackBaseName = "fallback"
        )

        assertTrue(fileName.toByteArray(Charsets.UTF_8).size <= 240)
        assertTrue(fileName.endsWith(".mp4"))
    }

    @Test
    fun buildUniqueFile_appendsIndexWhenNameAlreadyExists() {
        val directory = Files.createTempDirectory("bvd-file-name").toFile()
        try {
            File(directory, "Name.mp4").writeText("")

            val file = DownloadFileNameUtils.buildUniqueFile(
                directory = directory,
                baseName = "Name",
                extension = "mp4"
            )

            assertEquals("Name (1).mp4", file.name)
            assertFalse(file.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
