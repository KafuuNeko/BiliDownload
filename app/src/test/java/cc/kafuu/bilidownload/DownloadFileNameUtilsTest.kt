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

    /** 下载侧对照：外部存储模式用模板生成文件名，标题受限字符不能直接落盘。 */
    @Test
    fun buildUniqueFile_sanitizesTitleTemplateForExternalDownload() {
        val directory = Files.createTempDirectory("bvd-external-download").toFile()
        try {
            val file = DownloadFileNameUtils.buildUniqueFile(
                directory = directory,
                template = "{VideoName} - {PartName}",
                context = DownloadFileNameUtils.TemplateContext(
                    videoName = """A\/:*?"<>|B""",
                    partName = """C\/:*?"<>|D""",
                ),
                extension = "mp4",
                fallbackBaseName = "stream-1-2-3",
            )

            assertEquals("A_B - C_D.mp4", file.name)
            listOf('\\', '/', ':', '*', '?', '"', '<', '>', '|').forEach { illegal ->
                assertFalse("$illegal should not remain in ${file.name}", file.name.contains(illegal))
            }
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

    /** 不同文件系统或文档提供方可能拒绝这些字符，完整名称需在落盘前替换。 */
    @Test
    fun sanitizeFileName_replacesEveryIllegalCharacterAndKeepsExtension() {
        listOf('\\', '/', ':', '*', '?', '"', '<', '>', '|').forEach { illegal ->
            val fileName = DownloadFileNameUtils.sanitizeFileName("前${illegal}后.mp4")

            assertEquals("前_后.mp4", fileName)
        }
    }

    @Test
    fun sanitizeFileName_collapsesReplacedCharactersAndFallsBackForEmptyNames() {
        assertEquals(
            "a_b.mp4",
            DownloadFileNameUtils.sanitizeFileName("""a\/:*?"<>|b.mp4"""),
        )
        assertEquals("resource.mp4", DownloadFileNameUtils.sanitizeFileName("...mp4"))
        assertEquals("Title.mp4", DownloadFileNameUtils.sanitizeFileName("Title..mp4"))
        assertEquals("resource", DownloadFileNameUtils.sanitizeFileName("///"))
    }

    @Test
    fun sanitizeFileName_limitsUtf8LengthWithoutBreakingExtension() {
        val fileName = DownloadFileNameUtils.sanitizeFileName("测🎵".repeat(100) + ".mp4")

        assertTrue(fileName.toByteArray(Charsets.UTF_8).size <= 240)
        assertTrue(fileName.endsWith(".mp4"))
    }

    /** 扩展名中的路径分隔符、受限字符和控制字符也必须清理。 */
    @Test
    fun sanitizeFileName_removesIllegalCharactersFromExtension() {
        val illegalChars = listOf('\\', '/', ':', '*', '?', '"', '<', '>', '|') + ('\u0000'..'\u001F')
        illegalChars.forEach { illegal ->
            assertEquals(
                "Title.mp4",
                DownloadFileNameUtils.sanitizeFileName("Title.m${illegal}p4"),
            )
        }
        assertEquals("Title.mp4", DownloadFileNameUtils.sanitizeFileName("Title. m p4 "))
    }

    /** 扩展名清理为空时省略点，主名称为空时仍须使用回退名称。 */
    @Test
    fun sanitizeFileName_omitsExtensionWhenOnlyInvalidCharactersRemain() {
        assertEquals("Title", DownloadFileNameUtils.sanitizeFileName("Title.?/\u0000 "))
        assertEquals("resource", DownloadFileNameUtils.sanitizeFileName("...?/\u0000 "))
    }

    /** 超长扩展名不能挤掉有效主名称，输出仍须能够创建并回读。 */
    @Test
    fun sanitizeFileName_limitsOverlongExtensionsAndProducesWritableFiles() {
        val directory = Files.createTempDirectory("bvd-extension-name").toFile()
        try {
            val cases = mapOf(
                "x".repeat(300) to "x".repeat(32),
                "测🎵".repeat(100) to ("测🎵".repeat(4) + "测"),
                "🎵".repeat(100) to "🎵".repeat(8),
            )
            // 截断结果既须保留有效扩展名，也须能作为实际文件名称创建和回读。
            cases.forEach { (extension, expectedExtension) ->
                val fileName = DownloadFileNameUtils.sanitizeFileName("Title.$extension")

                assertEquals("Title.$expectedExtension", fileName)
                assertEquals(fileName, fileName.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
                val file = File(directory, fileName).apply { writeText("complete media") }
                assertEquals("complete media", file.readText())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    /** 模板、下载文件和冲突名称共用扩展名规则，重名递增时保持相同扩展名。 */
    @Test
    fun fileNameBuilders_keepExtensionWithinBudgetWhenResolvingCollisions() {
        val extension = "x".repeat(300)
        val expectedExtension = "x".repeat(32)
        val longContext = DownloadFileNameUtils.TemplateContext("测🎵".repeat(100), "P1")
        val fileName = DownloadFileNameUtils.buildFileName(
            template = "{VideoName}", context = longContext,
            extension = extension, fallbackBaseName = "resource",
        )
        assertTrue(fileName.toByteArray(Charsets.UTF_8).size <= 240)
        assertTrue(fileName.endsWith(".$expectedExtension"))
        assertEquals(
            "Title.$expectedExtension",
            DownloadFileNameUtils.resolveUniqueFileName("Title.$extension") { false },
        )

        // 连续占用名称并跨过个位序号，验证预算同时包含主名称、序号和扩展名。
        val existingNames = mutableSetOf(fileName)
        repeat(12) { index ->
            val uniqueName = DownloadFileNameUtils.resolveUniqueFileName(fileName, existingNames::contains)
            assertTrue(uniqueName.toByteArray(Charsets.UTF_8).size <= 240)
            assertTrue(uniqueName.endsWith("(${index + 1}).$expectedExtension"))
            assertEquals(uniqueName, uniqueName.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
            assertTrue(existingNames.add(uniqueName))
        }
    }

    /** 下载输出重名时只创建新的候选文件，已有文件内容须保持不变。 */
    @Test
    fun buildUniqueFile_limitsOverlongExtensionAndPreservesExistingFile() {
        val directory = Files.createTempDirectory("bvd-extension-conflict").toFile()
        try {
            val extension = "x".repeat(300)
            val expectedExtension = "x".repeat(32)
            val existing = File(directory, "Title.$expectedExtension").apply { writeText("existing") }
            val output = DownloadFileNameUtils.buildUniqueFile(directory, "Title", extension)

            assertEquals("Title (1).$expectedExtension", output.name)
            output.writeText("new media")
            assertEquals("new media", output.readText())
            assertEquals("existing", existing.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
