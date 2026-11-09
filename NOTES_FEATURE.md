# 新增功能：生成图文笔记（横版 PDF）

在 BiliDownload 基础上增加的功能，目标与 bili-to-notes 桌面脚本一致：
在视频详情页对任意一集点“生成笔记”，App 自动取字幕、下载 720P 视频流，
按字幕时间截图排版，导出一份横版 PDF 笔记（字幕逐行、画面变化才插图、
截图裁掉顶部 25%、图下标注时间段）。

## 改动清单

新增文件：
- `app/src/main/java/cc/kafuu/bilidownload/common/notes/NotesPdfGenerator.kt`
  笔记生成引擎：FFmpegKit 逐字幕截图 → 裁顶 25% → 128×72 像素签名去重
  （变化占比阈值 0.3%，与桌面脚本一致）→ 安卓原生 PdfDocument 横版逐行排版。

修改文件：
- `feature/viewbinding/viewmodel/activity/VideoDetailsViewModel.kt`
  新增 `onGenerateNotes / confirmGenerateNotes / exportNotesToUri`，
  完全复用既有 `requestSubtitleList / requestBccSubtitleData / loadPartDash`
  私有挂起函数；视频流由 `selectNotesVideoStream` 按推荐程度挑选：清晰度优先向上——有 720P 或更高的选其中最低一档（720P 最优先，避免动辄 4K），全部低于 720P 时才选最高一档 → 编码 AVC > HEVC > AV1（按 codecs 字符串与 codecid 双重判断）→ 同档选码率更高的一条让截图文字更清楚；生成结束自动删除，用
  `NetworkManager.downloadClient`（带 Referer/UA）直下缓存目录，
  生成后经 `ExportNotesAction` 交 Activity 导出。临时文件全部自动清理：下载的视频流在生成结束（finally）即删，截图临时帧在生成器内删，缓存 PDF 在导出完成或用户取消选择后删；只有用户选定位置的 PDF 成品保留。
- `feature/viewbinding/view/activity/VideoDetailsActivity.kt`
  新增确认弹窗与 SAF 导出通道（照抄字幕的 `ShowDownloadSubtitleConfirmAction /
  ExportSubtitleAction` 模式）；用户取消选位置时删除缓存 PDF。
- `res/views/item/layout/item_part_video.xml`
  每个分 P 在字幕按钮旁新增笔记图标按钮（`@drawable/ic_description`），
  生成中显示 ProgressBar（绑定 `generatingNotesPartLiveData`）。
- `res/values/strings.xml`、`res/values-zh/strings.xml`
  新增 9 条 notes_* 文案（中英）。

没有新增任何第三方依赖：PDF 用安卓原生 `PdfDocument`，截图用项目已集成的
FFmpegKit，去重/裁图用原生 Bitmap API。

## 使用流程（App 内）

1. 登录账号（取字幕需要登录态，与下载字幕相同）。
2. 打开任意视频/番剧详情页，在目标分 P 一行点笔记图标。
3. 确认弹窗点“确认” → 等生成完成（期间该行显示转圈）。
4. 系统文件选择器里选保存位置 → 得到 `<分P名>.pdf`，横屏打开即横版笔记。

## 构建（GitHub Actions）

注意：上游的 **Release** 工作流需要原作者的签名密钥（仓库 Secrets 里的
RELEASE_KEYSTORE_BASE64 等四项），在你自己的 fork 上会卡在
“Restore signing key”一步，属正常现象，不要用它。

本改动另附了一个免密钥的自用构建工作流 `.github/workflows/build-debug.yml`：

1. 把这份源码推到你自己的 GitHub 仓库（fork 上游后覆盖同名文件，
   或直接新建仓库上传整份源码）。
2. 仓库 Actions 页手动运行 **Build Debug APK** 工作流。
3. 构建完成后，在该次运行记录底部的 Artifacts 里下载
   `BiliDownload-notes-debug`，解压得到各 ABI 的 debug APK，
   手机选 arm64-v8a 那个安装即可（debug 签名与官方版本签名不同，
   不能覆盖安装官方版，需先卸载官方版或只保留一个）。

注意（本机环境限制下未执行的检查）：
- 本次改动是在无 JDK/Android SDK 的环境里完成的，未能本地编译，
  也未能按仓库约定运行 `tools/formatting` 的 `formatApply / formatCheck`；
  首次 CI 构建若报格式或编译问题，按报错回修即可（改动面已尽量收敛到
  上述 6 个文件，且全部照抄既有字幕流程的写法）。
- 已做的静态自检：3 个 XML 文件解析通过；Kotlin 大括号配平；
  全部新增字符串键在中英两份 strings.xml 中均有定义；
  引用的既有符号（`loadPartDash`、`requestSubtitleList`、
  `DownloadFileNameUtils.sanitizeFileName`、`NetworkManager.downloadClient`、
  `BiliPlayStreamResource.getStreamUrl/bandwidth`、`BccItem` 顶层模型）
  逐一对过源码。

## 已知限制（第一版）

- 只支持逐集生成（与原“下载字幕”相同粒度）；多集批量生成可后续
  参照 `BatchDownloadUseCase` 再加。
- 只出 PDF，不出 docx（安卓上 docx 需要引入 Apache POI，体积大且
  兼容性差；桌面脚本版继续提供 docx）。
- 无字幕的视频会直接提示“该视频暂无字幕”（与下载字幕一致），
  暂未做语音识别兜底。
- 字幕与视频时长差超过 5 秒时会弹出提示（截图位置可能偏差），
  但仍会生成，与桌面版的报告口径一致。
- 长视频逐条截图是逐个 FFmpeg 会话执行的，几十分钟的长视频
  生成会比较慢，属预期内表现。

## 构建修复记录

- 2026-11-08 第一次 CI 构建失败：`VideoDetailsViewModel.kt:1066` 处
  `response.body` 属性式访问报错（该 OkHttp 版本的 Response.body 是
  package-private 字段，须用方法调用）。已改为 `response.body()`，
  与仓库内 FileUtils/BiliVideoRepository 的既有写法一致。
- 2026-11-08 装机首测“笔记生成失败”：原下载实现自带了一套请求头
  （移动端 UA + www Referer 且没显式带 Cookie），与 App 正常下载不同。
  已改为与 DownloadManager.buildRequest 完全一致的 DOWNLOAD_HEADERS +
  账号 Cookie，并按主/备地址轮换重试；失败提示带回具体原因
  （notes_generate_failed_detail_message，如 download: HTTP 403），
  便于下一轮直接定位。
- 2026-11-08 第二轮构建失败：新下载代码里 `response.code` 同样是
  package-private 字段问题，已改为 `response.code()`（与 NetworkUtils、
  ResourceDownloader 的既有写法一致）。教训：该 OkHttp 版本的 Response
  成员（body/code 等）一律用方法调用，不用属性式访问。
