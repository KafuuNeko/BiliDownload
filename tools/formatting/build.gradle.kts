import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

application {
    mainClass = "cc.kafuu.bilidownload.formatting.FormattingMainKt"
}

val repositoryRoot = layout.projectDirectory.dir("../..").asFile

/** 注册独立格式化任务，通过参数列表调用已安装的 Android Studio，无需配置 Android 模块。 */
fun registerFormattingTask(
    name: String,
    mode: String,
) = tasks.register<JavaExec>(name) {
    group = "formatting"
    description =
        if (mode == "apply") {
            "Format selected Kotlin sources."
        } else {
            "Check selected Kotlin sources without writing."
        }
    // 应用与检查共用同一入口，模式仅决定批次验证后是否写回源码。
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    workingDir = repositoryRoot
    maxHeapSize = "1g"
    args("--root", repositoryRoot.absolutePath, "--mode", mode)

    // 使用参数列表传递路径，不拼接 shell 命令，兼容带空格和中文的文件名。
    mapOf(
        "formatFiles" to "--files",
        "formatFilesFile" to "--files-from",
        "formatBase" to "--base",
        "androidStudioHome" to "--studio-home",
    ).forEach { (property, option) ->
        providers.gradleProperty(property).orNull?.let { value -> args(option, value) }
    }
    if (providers.gradleProperty("formatAll").orNull == "true") args("--all")
}

registerFormattingTask("formatApply", "apply")
registerFormattingTask("formatCheck", "check")

tasks.test {
    systemProperty("bilidownload.repository", repositoryRoot.absolutePath)
    providers.gradleProperty("androidStudioHome").orNull?.let {
        systemProperty(
            "bilidownload.studio.home",
            it
        )
    }
}
