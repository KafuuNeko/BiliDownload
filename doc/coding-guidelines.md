# BiliDownload 编码规范主入口

本文是 BiliDownload 的通用编码规范入口，适用于开发者与 AI 的新增、修改、重构和代码审查。先阅读本文件，再按任务读取专题指南；所有规则与代码入口均以本仓库为依据。

本文件维护通用约束、阅读路线和交付清单，`doc/guides/` 维护专题规则与示例。新增或修改规范时同步维护相关链接，避免同一规则在多处出现互相矛盾的版本。规范约束新增和修改的代码，不因历史实现与规范有差异而扩大任务为全仓库重构。

## 1. 项目目标

BiliDownload 是 Android 视频下载与本地媒体管理应用，支持地址解析、账号登录、分集与批量下载、音视频合成与转封装、字幕及弹幕导出、本地播放和资源管理。

开发优先保证：

- 下载可靠：任务身份明确，重复请求、暂停、取消和重试不会造成重复执行或错误完成状态。
- 数据完整：任务记录、工作文件、成品和公共媒体记录一致，失败后保留恢复依据。
- 生命周期清晰：页面、后台下载和后台音乐播放由各自的所有者管理。
- 隐私可控：凭据、账号数据和带鉴权参数的地址只在必要边界内使用。
- 改动聚焦：复用当前分层与公共入口，保持代码、测试、文案和文档一致。
- 可维护、可验证：解释关键约束，并完成与改动风险匹配的验证。

## 2. 按任务阅读

新增、修改、重构或审查源码时，[代码注释与 KDoc](./guides/code-comments-and-kdoc.md) 始终必读，其他专题按任务叠加阅读。

| 任务类型 | 必读专题 |
| --- | --- |
| 新增页面、调整目录或公共能力 | [项目结构与分层](./guides/project-structure-and-layers.md) |
| 设计页面状态、多选、加载、错误或业务弹窗 | [MVI 与 UiState 树](./guides/mvi-and-uistate-tree.md) |
| 新增用户操作、跳转、权限或系统选择器 | [UiIntent、UiEvent 与 ViewAction](./guides/intent-and-uievent.md) |
| 编写 ViewModel、Activity、Fragment、Compose 或异步流程 | [页面实现与生命周期](./guides/viewmodel-activity-compose.md) |
| 修改布局、文案、颜色、图标或浅深色主题 | [界面资源与主题](./guides/ui-resources-and-theme.md) |
| 新增 Entity、DAO、Repository 或修改 schema | [Room 数据层](./guides/room-data-layer.md)，并核对本文第 4.1 节 |
| 新增依赖、应用级能力或偏好配置 | [依赖组织与 Kotpref](./guides/dependencies-and-kotpref.md) |
| 下载、批量处理、暂停、取消、合成或失败重试 | [下载任务与状态生命周期](./guides/download-and-task-lifecycle.md) |
| 文件发布、MediaStore、导出、删除或文件名模板 | [存储与资源生命周期](./guides/storage-and-resource-lifecycle.md) |
| 地址解析、接口、Cookie、登录或自定义下载源 | [网络与账号](./guides/network-and-account.md) |
| 播放、频谱、FFmpeg、JNI 或原生依赖 | [媒体播放与原生代码](./guides/media-and-native.md) |
| 选择测试、构建、设备验证或整理交付结果 | [验证与交付](./guides/testing-and-delivery.md) |

例如，“修改公共目录下载失败后的重试”应阅读注释、下载、存储、Room 和验证专题；“新增一个设置开关”应阅读注释、状态、事件、页面实现、界面资源和偏好专题。

## 3. 通用分层

项目使用 Kotlin 单 `:app` 模块，页面同时采用 XML/DataBinding + LiveData 与 Compose + StateFlow/UiIntent/UiEvent，共享 Room、网络、下载和媒体能力。技术版本及完整目录见[项目结构与分层](./guides/project-structure-and-layers.md)。

```text
XML View ──绑定事件/方法调用──> CoreViewModel ──LiveData──> XML View
                                  └──ViewAction──> 页面宿主

Compose Layout ──UiIntent──> CoreCompViewModel ──UiState──> Compose Layout
                                  └──UiEvent──> Activity

ViewModel ──> UseCase / Manager / Repository ──> 网络、Room、存储、媒体
```

- 页面负责渲染和系统交互，ViewModel 负责页面业务状态，用例负责业务编排。
- 下载调度和后台播放有独立生命周期，不依赖页面存活。
- 依赖由 `CommonLibs`、`NetworkManager`、现有单例及构造参数组织；不为普通业务引入新的依赖框架。
- 调整已有页面时沿用该页面体系；独立新页面优先 Compose，跨体系迁移须在需求范围内。
- 数据、存储和后台能力不反向依赖具体页面。公共基类变更须检查全部使用者。

## 4. 通用编码与改动约束

- 使用 4 空格缩进、UTF-8、文件末尾换行，显式 import，清理未使用 import。
- 优先 `val`、明确空值语义和提前返回，避免无依据的 `!!`、魔法数字和深层嵌套。
- 普通业务函数建议不超过 80 行、嵌套不超过 4 层；按职责拆分，不为行数制造无意义封装。
- 涉及时长、字节、采样或索引，在命名或注释中明确单位。
- 先检查 Git 状态和调用链，保留用户已有改动；不覆盖无关 IDE 配置或生成文件。
- 不做无关重构、批量格式化或框架改写；新增依赖说明用途与平台、体积和许可证影响。
- 不提交本机路径、签名材料、账号缓存、构建产物或临时日志；Room schema 按数据变更要求维护。

### 4.1 版本与兼容性

- 数据库已有历史 schema 和迁移链，默认保护既有下载历史、资源信息与关联关系。
- 修改 Room 结构、配置 key、枚举 code、序列化字段或接口参数时，先评估旧数据和调用方，再实施必要的兼容改动；数据库操作细则见 [Room 数据层](./guides/room-data-layer.md)。
- 已发布结构变化时维护版本、迁移及验证，不改写历史 schema，不用删库、清空记录或破坏性重建绕过问题。
- 若需求涉及丢弃数据或缩小兼容范围，应明确影响和处理方案；当前任务已有明确决定时沿用，不重复确认，不扩大为额外的清理或重建操作。
- 应用版本、数据库版本和依赖版本分别管理，普通修复不顺带升级，发布按发布任务范围执行。

### 4.2 功能移除与完整清理

- 沿依赖链检查入口、业务逻辑、状态、专用数据、序列化、资源、偏好、Manifest、测试、注释、文档和流程图，清理功能独占内容与失效引用。
- 当前源码与说明只描述有效职责和行为，不用“已移除某功能”等否定历史的表述代替清理；变更经过由 Git 记录。
- 测试验证当前行为，不为保留旧用例留下无用字段或接口。
- 全局检索相关符号、资源 ID、中英文名称和语义近似表述，人工核对注释与图示；编译通过不能替代完整清理。
- 保留其他功能使用的公共能力，以及既有数据所必需的迁移、历史 schema 和兼容性测试。

## 5. 注释要求

详细规则与本项目示例见[代码注释与 KDoc](./guides/code-comments-and-kdoc.md)。

- 使用中文解释意图、约束、时序、风险和恢复条件，不复述代码。
- 核心类型、公共及受保护 API、`@UiIntentObserver` 方法和复杂内部方法补充 KDoc。
- 方法体有效代码超过 15 行时，用紧邻代码块的注释说明关键阶段。
- 源码注释和 KDoc 的多项说明使用 `-` 无序列表，不使用数字分步。
- 不出现真实凭据、用户媒体信息或开发者机器路径；行为变化时同步维护注释与示例。

## 6. 命名速查

| 对象 | 约定 |
| --- | --- |
| 类型与文件 | `UpperCamelCase`，主要类型与文件名对应 |
| Activity / Fragment / ViewModel | `<Feature>Activity`、`<Feature>Fragment`、`<Feature>ViewModel` |
| Compose 页面及状态 | `<Feature>Layout`、`<Feature>UiState`、`<Feature>UiIntent`、`<Feature>UiEvent` |
| 数据与业务能力 | `<Name>Entity`、`<Name>Dao`、`<Name>Repository`、`<Name>UseCase` |
| 函数、参数、局部变量 | `lowerCamelCase`，函数以行为命名，Boolean 名称表达清晰的判断含义 |
| 成员与常量 | 既有类保留 `mViewModel` 等 `m` 前缀习惯；构造依赖及局部变量遵循所在文件风格；常量使用 `UPPER_SNAKE_CASE` |
| 资源 | 小写下划线，沿用 `activity_`、`fragment_`、`dialog_`、`item_`、`include_`、`ic_` 等前缀 |

## 7. 交付清单

- 功能位于正确层级，使用当前页面体系与公共入口。
- 页面状态、一次性事件、后台任务和持久化数据的归属清楚。
- 重复操作、取消、迟到回调与失败重试不会误报成功或重复执行。
- 数据库、工作文件、公共 URI 与清理顺序保持一致。
- 线程、作用域、观察者、播放器、流及原生资源的生命周期完整。
- 文案、主题、权限、Manifest、schema、测试与注释同步更新。
- 日志与示例没有敏感数据，Git diff 没有无关改动和产物。
- 已完成[对应验证](./guides/testing-and-delivery.md)，交付说明写明结果、证据和实际限制。

## 8. 常用代码入口

- [Application 初始化](../app/src/main/java/cc/kafuu/bilidownload/BiliDownload.kt)、[CommonLibs](../app/src/main/java/cc/kafuu/bilidownload/common/CommonLibs.kt)。
- [Compose 基类](../app/src/main/java/cc/kafuu/bilidownload/common/core/compose/)、[DataBinding 基类](../app/src/main/java/cc/kafuu/bilidownload/common/core/viewbinding/)。
- [下载用例](../app/src/main/java/cc/kafuu/bilidownload/common/download/)、[DownloadManager](../app/src/main/java/cc/kafuu/bilidownload/common/manager/DownloadManager.kt)、[DownloadService](../app/src/main/java/cc/kafuu/bilidownload/service/DownloadService.kt)。
- [ResourceStorage](../app/src/main/java/cc/kafuu/bilidownload/common/storage/ResourceStorage.kt)、[AppDatabase](../app/src/main/java/cc/kafuu/bilidownload/common/room/AppDatabase.kt)。
- [NetworkManager](../app/src/main/java/cc/kafuu/bilidownload/common/network/manager/NetworkManager.kt)、[AppModel](../app/src/main/java/cc/kafuu/bilidownload/common/model/AppModel.kt)。
- [MusicPlaybackService](../app/src/main/java/cc/kafuu/bilidownload/service/MusicPlaybackService.kt)、[原生构建](../app/src/main/cpp/CMakeLists.txt)。

涉及这些能力时，从对应专题列出的规则、代码入口和验证场景开始，避免一次加载所有实现。
