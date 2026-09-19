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

    @Test
    fun resolveUniqueFileName_keepsAvailableNamesAndSkipsOccupiedSuffixes() {
        val existingNames = setOf("Name.mp4", "Name(1).mp4", "Name(2).mp4")

        assertEquals(
            "Available.mp4",
            DownloadFileNameUtils.resolveUniqueFileName("Available.mp4", existingNames::contains),
        )
        assertEquals(
            "Name(3).mp4",
            DownloadFileNameUtils.resolveUniqueFileName("Name.mp4", existingNames::contains),
        )
    }

    @Test
    fun resolveUniqueFileName_preservesDotsAndHandlesMissingExtensions() {
        val existingNames = setOf("Video.Part.mp4", "Video")

        assertEquals(
            "Video.Part(1).mp4",
            DownloadFileNameUtils.resolveUniqueFileName("Video.Part.mp4", existingNames::contains),
        )
        assertEquals(
            "Video(1)",
            DownloadFileNameUtils.resolveUniqueFileName("Video", existingNames::contains),
        )
    }

    @Test
    fun resolveUniqueFileName_reservesBytesForGrowingSuffixesWithoutOverwriting() {
        val desiredName = "测🎵".repeat(33) + "测.mp4"
        val existingNames = mutableSetOf(desiredName)

        repeat(12) { index ->
            val fileName = DownloadFileNameUtils.resolveUniqueFileName(
                desiredName,
                existingNames::contains,
            )

            assertTrue(fileName.toByteArray(Charsets.UTF_8).size <= 240)
            assertTrue(fileName.endsWith("(${index + 1}).mp4"))
            assertEquals(fileName, fileName.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
            assertTrue(existingNames.add(fileName))
        }
    }
}
