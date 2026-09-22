# 存储与资源生命周期

本专题用于工作文件、公共媒体发布、URI、导出、删除和文件命名。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 基本约束

- 下载及 FFmpeg 工作文件使用 `CommonLibs` 提供的应用专属目录；公共发布通过 `ResourceStorage` 完成。
- `DownloadPathMode.INTERNAL` 保留在应用专属目录，`EXTERNAL` 发布到 `Download/BVD`，`EXTERNAL_MEDIA` 将视频放到 `Movies/BVD`、其他资源放到 `Download/BVD`。工作路径与最终发布路径应分别处理。
- 公共目录、MediaStore 集合、相对路径和 MIME 必须一致。按项目现有平台分支处理 Android 10 及以上与旧系统的差异。
- 发布遵循“保存公共 URI 检查点、写入并核验内容、保存最终记录、清理私有源文件”的顺序。最终记录保存失败时保留源文件及必要恢复信息，不能同时丢失两份副本。
- `DownloadResourceEntity.file` 与 `contentUri` 是不同访问方式；不能把 `content://` 当成本地文件路径，也不能仅凭路径失效就认定公共资源丢失。
- 数据库事务不能回滚文件系统或 MediaStore。跨存储操作须定义检查点、失败清理、重试与幂等规则，并覆盖中途退出。
- 删除资源复用 `ResourceStorage` 和 `DownloadRepository`，先确认实际资源的删除结果，再清理相关记录；失败时保留可定位和重试的依据。
- 导出复用 SAF、`DocumentFile` 和现有批量用例，检查目标目录授权与写入结果。取消、授权失效、空间不足和部分成功都必须有明确结果。
- 文件名复用 `DownloadFileNameUtils`、`ExportFileNameUtils` 及唯一名称处理，过滤非法字符、处理空名称和冲突，不能让标题或模板生成目录穿越路径。
- 对外分享通过受控的 content URI 和必要的临时授权；不能为了分享一个资源而扩大整个目录的暴露范围。

## 2. 资源身份与所有权

| 对象 | 归属与用途 |
| --- | --- |
| 下载 .part | 按任务隔离的缓存，用于续传，尚不是可用成品 |
| DASH 源文件 | 对应任务的音视频源资源，可用于合成或重试 |
| 合成或转封装输出 | 完成核验后登记的工作资源 |
| 公共 MediaStore 资源 | 发布后通过路径和 contentUri 定位，按平台规则读写 |
| SAF 导出副本 | 用户选择目录下的输出，与原下载资源分别管理 |

删除前确认对象属于当前操作、没有仍需使用它的合成或发布任务。目录递归清理限制在明确拥有的任务缓存范围，不根据视频标题推导要删除的目录。

## 3. 发布检查点

```text
工作文件可用
  -> 创建或恢复公共 URI，并持久化检查点
  -> 写入并核验公共内容
  -> 完成公共条目状态与最终资源记录
  -> 清理私有源文件
```

- 数据库保存中间 URI 后，中断重试优先识别该记录，不能盲目创建同名副本。
- Android 10 及以上通过 MediaStore pending 状态管理尚未完成的发布；目录、集合和 MIME 必须配套。
- 复制、内容核验或最终持久化失败时，按当前实现的补偿规则处理公共副本，保留至少一个有效来源。
- 资源已经发布或仅有可访问 content URI 时，读取和恢复逻辑须根据实际可访问性判断，不把路径缺失等同于资源丢失。
- 设置切换不应中断对已开始发布任务的恢复；`forcePublish` 用于继续处理已有发布阶段，使用前明确任务状态。

## 4. 删除与导出结果

删除先执行真实资源操作，成功后清理相关记录。批量删除逐项记录结果；失败项保留身份与重试依据，不能在 UI 中直接抹去全部条目。

导出通过用户授权目录创建副本。处理 URI 或文件来源时核对用例实际支持的访问方式，不能假定所有读取接口都同时支持路径和 content URI；扩展支持时补足源读取与失败分支。

- 输出文件名不冲突，扩展名与 MIME、实际内容一致。
- 目标授权撤销、不可写、目录无效、源资源缺失或磁盘空间不足均返回明确结果。
- 空输入、无可导出资源、用户取消与部分成功应分别展示。
- 复制失败后明确未完成目标的清理责任；导出成功不会自动删除原资源。
- 分享只授予目标所需的 URI 权限，不通过宽泛目录暴露扩大读取范围。

## 5. 命名模板

模板输入经过 `DownloadFileNameUtils` / `ExportFileNameUtils` 处理：检查空名称、非法字符、目录穿越、长度与冲突。标题中的引号、斜杠或 Unicode 字符不能改变目录归属或媒体命令语义。

模板变化须明确只影响后续生成名称，还是包括其他指定范围；不要在普通设置更新时自动重命名全部历史资源。

## 6. 验证入口

代码入口：[ResourceStorage](../../app/src/main/java/cc/kafuu/bilidownload/common/storage/ResourceStorage.kt)、[ResourcePublishResult](../../app/src/main/java/cc/kafuu/bilidownload/common/storage/ResourcePublishResult.kt)、[DownloadRepository](../../app/src/main/java/cc/kafuu/bilidownload/common/room/repository/DownloadRepository.kt)、[BatchExportUseCase](../../app/src/main/java/cc/kafuu/bilidownload/common/download/BatchExportUseCase.kt)、[BatchDeleteUseCase](../../app/src/main/java/cc/kafuu/bilidownload/common/download/BatchDeleteUseCase.kt)、[FileProvider 配置](../../app/src/main/res/xml/resource_paths.xml)。

文件名规则使用现有 JVM 测试；公共发布与删除要在对应系统分支验证。重点覆盖中断后的重试、最终落库失败、只剩 URI、权限拒绝及批量部分成功。数据库字段变化同时阅读 [Room 数据层](./room-data-layer.md)。
