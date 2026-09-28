# 下载任务与状态生命周期

本专题用于下载创建、批量处理、队列、暂停取消、断点续传、合成和失败重试。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 入口与唯一性

- 单任务通过 `DownloadManager` 的现有入口创建和调度；批量操作复用 `BatchDownloadResolver`、`BatchDownloadUseCase`、`BatchExportUseCase` 和 `BatchDeleteUseCase`。
- 视频分集使用 `(bvid, cid)` 标识，数据库任务主键、`groupId`、资源主键及分集 ID 各有职责，不得混用。
- 创建任务与 DASH 记录复用 `DownloadRepository.createNewRecordIfAbsent()` 的事务边界，不能改成 UI 层“先查询再插入”的非原子判断。
- 保持排队、正在启动、正在下载等阶段的去重与停止语义；重复点击和重复启动服务不能让同一任务同时执行两次。
- `DownloadServiceTaskRegistry` 负责同一服务实例的任务登记和停止判断，避免某个任务结束后直接停止仍有其他任务运行的服务。
- 批量下载按 `BatchQualityMismatchMode` 处理清晰度或编码不匹配，准确返回新增、跳过、失败和取消结果，不擅自改变用户选定的策略。

## 2. 状态边界

`TaskStatus` 是写入数据库的任务生命周期；`DownloadStatus` 是下载执行器的运行状态。二者不能直接按名称或整数互换。

| 阶段 | 持久化状态 | 失败处理 |
| --- | --- | --- |
| 等待与准备 | `PREPARE` | 保留任务身份，避免重复入队 |
| 获取音视频流 | `DOWNLOADING` | `DOWNLOAD_FAILED`，按下载恢复策略重试 |
| 合成或转封装 | `SYNTHESIS` | `SYNTHESIS_FAILED`，保留可复用的源资源 |
| 发布公共资源 | `PUBLISHING` | `PUBLISH_FAILED`，从发布检查点恢复 |
| 所需处理全部完成 | `COMPLETED` | 资源与记录应可用于后续播放、导出和删除 |

具体任务可按资源类型和存储设置跳过不需要的阶段。下载字节传输结束不代表整个任务完成；完成状态必须反映合成、资源登记与必要发布的最终结果。

- `TaskStatus.code` 已持久化，既有编码必须保持稳定，不能改用 `ordinal`。新增状态时同步检查活动状态集合、历史列表、操作按钮、通知、重试和测试。
- 暂停、取消、失败与完成具有不同含义。处理迟到回调时核对任务当前归属和状态，避免停止后的任务重新开始或被覆盖为完成。
- 发布失败不应触发无条件重新下载，也不能在重试前清理唯一的有效工作文件。

## 3. 流下载与 FFmpeg

- 保留按任务隔离的 `.part` 缓存。修改续传时覆盖 `206` 追加、`200` 覆盖重下、`416` 缓存失效处理，以及未知长度和响应提前结束的情况。
- 流 URL 的选择、备用地址和自定义源处理集中在下载层；修改时保留路径、查询参数与必要请求头，避免用字符串拼接破坏地址。
- `ResourceDownloader` 在资源内部完成自动恢复；同源重试、切源和一次播放地址刷新共用最多 5 次额外尝试，期间保持 `DOWNLOADING`，耗尽预算才发布失败。429/503 的等待提示不得被退避缩短，超过自动等待上限则保留手动恢复。
- `.part.resume` 保存源地址指纹、强 ETag 和长度，不保存完整 URL。跨地址续传须通过强 ETag 和范围校验；缺少可信检查点、资源版本变化或范围不匹配时安全重下，不拼接身份不明的缓存。
- 请求取消监听必须持续到正文消费完毕，覆盖连接、读取、退避和地址刷新。不得仅在接收响应头前绑定取消，也不得让一个子资源失败后另一个资源继续阻塞队列。
- `DownloadSourceSelector` 每个候选最多读取 64 KiB、最多等待 3 秒；样本只能影响候选顺序，不得丢弃所有备用源或将空正文标记为可用。
- 通过 `FFMpegUtils` 等现有入口处理合成、音频转封装和格式转换。区分容器格式、编码、MIME 与扩展名，不通过改后缀伪装格式转换。
- FFmpeg 返回成功后仍需确认预期输出可用；失败、取消或输出不完整时不得登记为可用成品。
- 输入、输出路径正确引用或按参数接口传递，防止空格、引号等字符改变命令含义。
- “合成后删除源文件”等偏好只在对应成功条件满足后执行，保留失败重试所需的音视频源文件。

## 4. 执行链路

```text
页面 / BatchDownloadUseCase
  -> DownloadManager 接收请求
  -> DownloadRepository 原子创建任务与 DASH 记录
  -> 队列与服务登记
  -> 解析流地址、下载并登记源资源
  -> 按需要合成或转封装
  -> 按存储模式发布资源、保存检查点
  -> 更新任务最终状态与通知
```

该图表示各阶段的职责关系，具体入口与调用顺序以代码为准。改动时追踪当前任务如何进入每一阶段，不能假定任务必须从下载重新开始。

## 5. 生命周期与竞态

| 场景 | 必须保持的行为 |
| --- | --- |
| 相同分集再次提交 | 已有活动任务被识别，调用方得到新增或跳过结果 |
| 排队时暂停或取消 | 后续出队不会继续启动已停止的请求 |
| 解析流地址时停止 | 迟到的解析结果不得重新启动该任务 |
| 多资源并发下载 | 任一资源状态变化不会破坏组状态与已完成资源 |
| 同一任务重复启动 Service | 不重复登记或执行，更新有效的 startId 判断 |
| 某任务结束但其他任务运行 | Service 不提前退出，前台通知继续有效 |
| 合成或发布失败 | 保留所需源资源，重试从对应阶段恢复 |
| 页面退出或进程重建 | 页面退出不控制后台生命周期；重建按持久记录和资源重新判断 |

执行器运行状态、服务登记和数据库状态各自承担职责。操作涉及多个容器时明确锁或串行处理边界，不能以“用了 ConcurrentHashMap”代替对完整竞态的判断。

## 6. 批量操作

- 解析候选资源后按分集身份去重，先确定用户选择的范围与清晰度策略。
- 用例通过参数和回调请求选择，不创建具体 Dialog 或持有 Activity。
- 输入列表与用户确认的目标保持一致，处理中列表刷新不应改变当前批次。
- 单项不可下载、无匹配流、已有活动任务和入队失败都要计入结果；部分成功不能提示为全部成功。
- 取消选择、取消批次与停止已创建任务是不同操作，保持当前用例契约并在 UI 中准确表达。

## 7. 通知与验证

通知状态与数据库、执行器结果对应；点击通知携带稳定身份并进入能恢复展示的页面。修改任务完成或服务停止条件时，同时检查下载通知和通知跳转。

代码入口：[DownloadManager](../../app/src/main/java/cc/kafuu/bilidownload/common/manager/DownloadManager.kt)、[DownloadService](../../app/src/main/java/cc/kafuu/bilidownload/service/DownloadService.kt)、[服务任务登记](../../app/src/main/java/cc/kafuu/bilidownload/service/DownloadServiceTaskRegistry.kt)、[批量下载用例](../../app/src/main/java/cc/kafuu/bilidownload/common/download/BatchDownloadUseCase.kt)、[下载通知](../../app/src/main/java/cc/kafuu/bilidownload/notification/DownloadNotification.kt)。

现有验证入口：[传输恢复测试](../../app/src/test/java/cc/kafuu/bilidownload/download/ResourceDownloaderTest.kt)、[源探测测试](../../app/src/test/java/cc/kafuu/bilidownload/download/DownloadSourceSelectorTest.kt)、[批量下载测试](../../app/src/test/java/cc/kafuu/bilidownload/BatchDownloadUseCaseTest.kt)、[服务登记测试](../../app/src/test/java/cc/kafuu/bilidownload/DownloadServiceTaskRegistryTest.kt)、[活动状态测试](../../app/src/test/java/cc/kafuu/bilidownload/DownloadTaskStatusPolicyTest.kt)。续传、前台服务和真实资源发布还需按改动选择设备场景，不能以纯 JVM 测试代替。

相关专题：[存储](./storage-and-resource-lifecycle.md)、[网络](./network-and-account.md)、[媒体](./media-and-native.md)、[验证](./testing-and-delivery.md)。
