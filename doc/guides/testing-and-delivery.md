# 验证与交付

本专题用于选择测试、构建、设备验证和交付检查。按改动风险执行，区分文档检查、可自动验证的业务规则与真实平台行为。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 验证范围

选择能够证明本次行为正确的最小验证范围；业务规则或故障修复应有相应断言，避免只验证 mock 调用次数或复制实现逻辑。

| 改动范围 | 验证重点 |
| --- | --- |
| 纯文档 | 路径、链接、类名与当前源码一致，无未创建文档的依赖，`git diff --check` |
| 地址、命名、匹配、状态策略 | 对应 JVM 单元测试，正常、边界、无效输入与失败分支 |
| 批量下载或服务调度 | 去重、部分失败、取消、停止竞态、重试、重复服务启动；必要时设备验证 |
| Room 结构 | schema 与迁移测试，旧版本数据保留、新安装和迁移后的业务读写 |
| 存储、导出、删除 | 对应系统分支与权限，空间不足、中途退出、部分成功、URI 恢复和重试 |
| 页面与交互 | Debug 构建，浅深色、空态、错误态、旋转、返回、权限拒绝及重复操作 |
| 播放与频谱 | 相关计算测试，设备上的后台播放、画中画、资源释放和通知控制 |
| JNI、本地 AAR 或打包配置 | 对应构建、四 ABI 产物检查、原生功能设备验证 |

## 2. 常用命令

在仓库根目录按需要执行，依赖缓存完整时可加 `--offline`：

```bash
# JVM 测试与 Debug 构建
bash ./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug

# 指定业务测试
bash ./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests 'cc.kafuu.bilidownload.BatchDownloadUseCaseTest'

# Android 静态检查
bash ./gradlew --no-daemon --console=plain :app:lintDebug

# 需要已连接的设备或模拟器
bash ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest

# 补丁格式检查
git diff --check
```

Windows 使用 `gradlew.bat` 执行对应任务。构建环境需具备项目要求的 JDK、Android SDK、NDK、CMake 和本地 FFmpeg AAR。无法执行验证时，记录实际命令、阻塞原因和未验证范围，不以“应当通过”代替结果。

发布任务另行核对 `.github/workflows/release.yml`：当前流程从符合要求的正式标签构建、签名并校验四 ABI APK。`scripts/verify-release.ps1` 可在 Windows 环境校验版本与分包产物；普通代码验证无需触发发布或修改版本。

## 3. 现有测试导航

| 测试入口 | 适用修改 |
| --- | --- |
| BiliAddressParserTest、BvConvertTest | 地址识别、BV/AV 转换 |
| BatchDownloadResolverTest、BatchDownloadUseCaseTest | 分集解析、批量策略、入队结果 |
| DownloadTaskStatusPolicyTest | 活动任务状态分类 |
| DownloadServiceTaskRegistryTest | 重复服务启动、任务登记和退出判断 |
| DownloadFileNameUtilsTest、ExportFileNameUtilsTest | 文件模板与输出名称 |
| DownloadPathModeTest、BatchQualityMismatchModeTest | 持久化 code 与未知值策略 |
| LocalNetworkHostUtilsTest | 主机识别和本地网络规则 |
| PcmDecoderTest、PcmEncodingMapperTest | PCM 编码映射与解码 |
| AppDatabaseMigrationTest | 历史 schema 的真实迁移与数据保留 |

单元测试位于 [app/src/test](../../app/src/test/)，设备测试位于 [app/src/androidTest](../../app/src/androidTest/)。优先扩充与问题对应的测试，不为可逆的小型文案或纯文档修改编写机械测试。

## 4. 测试设计

- 断言用户可观察的结果与关键数据不变量，例如唯一任务、准确计数、状态不回退或历史关联不丢失。
- 用构造参数替换网络、策略读取和入队函数，测试批量用例，不依赖真实账号与临时可用的服务端数据。
- 失败修复覆盖具体触发条件及正常路径；取消和迟到回调应验证结果归属，不能只断言方法被调用。
- 迁移测试使用导出的历史 schema 创建数据，运行真实迁移链后检查字段、默认值与关联。
- JVM 测试不能证明前台服务、权限、MediaStore、音频输出和四 ABI 的运行行为；这些部分按风险增加设备验证。

## 5. 设备场景与结果记录

按改动选择：页面旋转与返回、前后台切换、权限拒绝与撤销、重复点击、断网或续传、磁盘空间不足、批量部分成功、发布中断恢复、画中画和通知控制。

- 使用明确的系统版本、资源类型与操作步骤复现，记录预期和实际结果。
- 验证失败时区分本次改动、既有问题、依赖下载和工具链故障，不能直接跳过失败后宣称通过。
- 只有相关检查通过后才给出对应结论；无设备或环境不完整时说明未验证范围。
- 检查已覆盖风险后，不在无新变化或新疑点时反复运行同一组测试。

## 6. 文档与交付

文档修改检查所有相对链接、代码入口、章节引用、Markdown 围栏和示例 API。所有专题应能从主入口到达，专题返回链接指向本仓库主入口，不依赖未创建的专题或设计文件。

`git diff --check` 不包含未跟踪文件；新增文档可使用 `git diff --no-index --check /dev/null doc/coding-guidelines.md` 等方式逐个检查补丁空白问题，同时核对 Git 状态。

交付说明包含：改动结果、执行的验证及结果、未验证的真实限制。完成[主入口交付清单](../coding-guidelines.md#7-交付清单)，不把规划中的验证写成已通过。

发布代码入口：[GitHub 工作流](../../.github/workflows/release.yml)、[Windows 发布校验脚本](../../scripts/verify-release.ps1)。普通开发验证不触发远程发布、签名配置变更或版本升级。
