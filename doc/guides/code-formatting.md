# 自动代码格式化

项目通过 `tools/formatting` 调用 Android Studio 自带的 `format` 命令，支持 Windows、Linux、macOS，处理自有 `.kt` / `.kts` 源码。

[返回编码规范主入口](../coding-guidelines.md)

## 执行环境

- 安装 JDK，推荐 Java 21，并配置 `JAVA_HOME`；安装 Git。
- 安装 `tools/formatting/android-studio.properties` 指定的 Android Studio。目前使用 Panda 4 | 2025.3.4 Patch 1，保留安装包内的 Kotlin 插件与 JBR。
- 从仓库根目录使用 Gradle wrapper，格式化无需 Android SDK。
- 工具会查找常见安装目录。其他位置使用环境变量 `ANDROID_STUDIO_HOME`，或传入 `"-PandroidStudioHome=安装目录"`。macOS 可指定 `.app` 或其 `Contents` 目录。
- 首次构建需要下载 Gradle 与 Kotlin 构建依赖，应先说明原因；缓存齐全后可以加入 `--offline`。格式化命令不会自动下载 IDE。

Windows 配置安装位置示例：

```powershell
$env:ANDROID_STUDIO_HOME = "C:\Program Files\Android\Android Studio 2026"
```

Linux / macOS 示例：

```bash
export ANDROID_STUDIO_HOME="/opt/android-studio"
# macOS: export ANDROID_STUDIO_HOME="/Applications/Android Studio.app"
```

## 项目代码样式

- `.idea/codeStyles/Project.xml` 保存代码样式，`codeStyleConfig.xml` 将编辑器设为使用项目样式；命令行显式读取同一份 XML。
- Kotlin 使用 Android Studio 的 Kotlin 官方预设，保留已有换行；`.editorconfig` 管理编码、行尾与缩进。
- 格式化等同于原生 `Reformat Code` 的排版操作。导入清理由编辑器的 `Optimize imports` 单独执行，代码清理由 `Code cleanup` 单独执行。
- 编辑器中的 `Do not keep line breaks` 保持未勾选。需要调整排版时，在 AS 的项目 Code Style 中修改并保存项目配置。
- 跨平台使用固定的 IDE 构建与内置 Kotlin 插件，避免不同版本产生不同结果。

## 交付流程

交付顺序为：完成代码与导入清理 → `formatApply` → 同范围的 `formatCheck` → AI 样式审计 → 按影响范围编译与验证。

- 修改 `.kt` / `.kts` 后，对本次自己修改且仍存在的文件依次执行 `formatApply` 和同范围的 `formatCheck`，随后由 AI 按[代码美观与可读性](./code-elegance-and-readability.md)审计最终 diff。
- AI 样式审计检查命名与单位、具名参数、复杂条件、Lambda 接收者与返回目标、Compose 参数及控制流，核对求值顺序、异常、取消和资源生命周期。
- 审计修改 Kotlin 代码后，重新执行 `formatApply`、同范围的 `formatCheck` 和 AI 样式审计，直到最终改动通过；审计范围覆盖最终变更。
- 存在其他人的未提交改动时，显式传入自己的文件列表；大量文件或含逗号的路径使用文件清单。
- 全仓格式化需要任务授权，使用 `-PformatAll=true` 显式选择。
- 批量格式化后审查 diff，并按影响范围执行编译和现有测试；格式检查不替代语法及业务校验。
- 交付时分别说明格式化、格式检查、AI 样式审计和业务验证的实际结果，以及相关检查未执行的情况。

## 日常使用

Windows PowerShell：

```powershell
.\gradlew.bat -p tools/formatting formatApply
.\gradlew.bat -p tools/formatting formatCheck
```

Linux / macOS：

```bash
./gradlew -p tools/formatting formatApply
./gradlew -p tools/formatting formatCheck
```

默认选择 Git 中暂存、未暂存及未跟踪且未忽略的 Kotlin 源码，排除已删除文件与 `build` 等生成目录。

## 指定范围

以下示例使用 Windows wrapper，Linux / macOS 替换为 `./gradlew`。路径相对于仓库根目录，也支持仓库内的绝对路径。

显式文件列表，支持空格和中文路径：

```powershell
.\gradlew.bat -p tools/formatting formatApply "-PformatFiles=path/to/First.kt,path/to/Second.kt"
.\gradlew.bat -p tools/formatting formatCheck "-PformatFiles=path/to/First.kt,path/to/Second.kt"
```

大量文件或含逗号的路径使用 UTF-8 清单，每行一个路径：

```powershell
.\gradlew.bat -p tools/formatting formatApply "-PformatFilesFile=selected-files.txt"
```

分支相对 merge base 的已提交改动，加上工作区改动：

```powershell
.\gradlew.bat -p tools/formatting formatCheck "-PformatBase=origin/master"
```

全仓处理：

```powershell
.\gradlew.bat -p tools/formatting formatApply -PformatAll=true
.\gradlew.bat -p tools/formatting formatCheck -PformatAll=true
```

范围选项互斥。不存在、非 Kotlin、生成目录或仓库外的显式路径会失败。

## 检查与维护

- 原生引擎在临时副本上批量格式化，保留相对路径及各级 `.editorconfig`，以原生 `-dry` 检查输出是否稳定；需要继续排版时最多执行四轮，未收敛则失败。
- IDE 的配置、缓存、日志和用户插件目录独立，可与正在使用的 Android Studio 同时运行。
- 非零退出、跳过文件、处理数量不符和稳定性检查失败均会中止，不写回源码。
- `formatCheck` 只报告副本与原文的差异；有差异时返回失败，不修改源码。
- `formatApply` 在整个批次完成后核对原文与可写状态，再原位写回，保留权限。写入阶段的磁盘错误仍可能中断批次。
- 输入与输出均校验 UTF-8，非法字节不会被替换后保存。

修改工具或样式配置后运行接入测试，并对工具本身的 Kotlin 源码执行格式化和只读检查：

```powershell
.\gradlew.bat -p tools/formatting test
```

测试覆盖跨平台启动器选择、IDE 版本与安装目录识别、中文与特殊字符路径、Git 范围及批次写入保护。[格式化 CI](../../.github/workflows/formatting.yml)在三个平台安装固定版本的 AS，校验下载包 SHA-256 后执行测试与变更文件检查。AI 样式审计由执行开发任务的 AI 在格式检查通过后完成，并在交付说明中记录结论。

参考：[原生命令行格式化器](https://www.jetbrains.com/help/idea/command-line-formatter.html)、[项目代码样式](https://www.jetbrains.com/help/idea/settings-code-style.html)、[Android Studio 下载存档](https://developer.android.com/studio/archive)。
