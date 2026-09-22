# 项目结构与分层

本专题用于确认新增功能的归属、代码依赖方向和技术基线。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 技术基线

当前技术基线如下；具体版本以仓库构建配置为准，不能为了满足本文中的版本描述而修改构建文件。

| 项目 | 当前约定 |
| --- | --- |
| 模块与包名 | 单 `:app` 模块，包名及 applicationId 为 `cc.kafuu.bilidownload` |
| 语言与构建 | Kotlin、Gradle Kotlin DSL、Gradle Wrapper；CI 使用 JDK 17，Java 源码与目标兼容级别为 1.8 |
| Android 范围 | `minSdk = 24`，`compileSdk = 37`，`targetSdk = 37` |
| 页面 | XML/DataBinding + LiveData，以及 Compose + StateFlow/UiIntent/UiEvent |
| 数据与网络 | Room + KSP、Kotpref、Retrofit/OkHttp、Gson、EventBus |
| 媒体与原生代码 | Media3、`app/libs/ffmpeg-kit.aar`、JNI/CMake、libqrencode |
| 原生构建 | NDK `28.1.13356709`、CMake `3.31.0`；按四种 ABI 分包 |

依赖通过 `CommonLibs`、`NetworkManager`、已有单例及构造参数组织。业务用例优先使用可替换的构造参数或函数依赖，便于独立验证。

## 2. 目录职责

以下代码目录均相对于 `app/src/main/java/cc/kafuu/bilidownload/`。

| 目录或文件 | 职责 |
| --- | --- |
| `BiliDownload.kt`、`common/CommonLibs.kt` | Application 初始化、应用级 Context、数据库和公共目录入口 |
| `common/core/viewbinding/` | DataBinding 页面、ViewModel、列表、对话框和 ViewAction 基础设施 |
| `common/core/compose/` | Compose Activity、状态与意图分发、一次性事件和主题 |
| `feature/viewbinding/view/` | XML 页面的 Activity、Fragment、Dialog |
| `feature/viewbinding/viewmodel/` | XML 页面的 ViewModel，按 activity、fragment、dialog 等分类 |
| `feature/compose/activity/`、`feature/compose/layout/`、`feature/compose/views/` | Compose 宿主、页面布局、可复用组件 |
| `feature/compose/viewmodel/<功能>/` | 对应功能的 ViewModel、UiState、UiIntent 和 UiEvent |
| `common/network/` | 服务定义、响应模型、接口仓库、请求配置、拦截器与 WBI 签名 |
| `common/room/` | Entity、DAO、DTO、Repository、数据库与迁移定义 |
| `common/download/` | 批量下载、导出、删除用例，资源解析及下载快照 |
| `common/manager/` | 下载调度、账号及其他应用级管理能力 |
| `common/storage/` | 工作文件与公共媒体存储的发布、恢复和删除 |
| `common/audio/` | 播放器工厂、音乐共享运行时、PCM 和频谱处理 |
| `common/model/`、`common/constant/`、`common/utils/`、`common/ext/` | 业务模型、偏好、常量、工具及扩展函数 |
| `service/`、`notification/` | 下载与音乐播放服务、前台通知和通知跳转 |

其他重要位置：

- XML 布局位于 `app/src/main/res/views/{activity,fragment,dialog,include,item}/layout/`。各 `layout/` 的父目录由 `app/build.gradle.kts` 注册为资源根目录；新增一类资源目录时须同步配置。
- 文案与主题资源位于 `app/src/main/res/values/`、`app/src/main/res/values-zh/`、`app/src/main/res/values-night/`；Compose 主题位于 `common/core/compose/theme/`。
- 自有 JNI/C++ 代码位于 `app/src/main/cpp/src/`，第三方源码位于 `app/src/main/cpp/thirdparty/`。
- JVM 单元测试位于 `app/src/test/`，设备测试位于 `app/src/androidTest/`，Room schema 位于 `app/schemas/`。
- 依赖版本集中在 `gradle/libs.versions.toml`；发布流程位于 `.github/workflows/release.yml` 和 `scripts/verify-release.ps1`。

新增功能按所用页面体系放入上述目录，并把业务能力放到对应公共层。调整已有页面时沿用该页面的体系；独立新页面优先使用 Compose。跨体系迁移应有明确需求和完整验证范围，不在普通功能修改中顺带进行。

## 3. 分层规则

两套页面体系共享网络、持久化、下载和媒体能力。

```text
XML View ──方法调用/绑定事件──> CoreViewModel ──LiveData──> XML View
                                  └──ViewAction──> 页面宿主

Compose Layout ──UiIntent──> CoreCompViewModel ──UiState──> Compose Layout
                                  └──UiEvent──> Activity

ViewModel ──> UseCase / Manager / Repository ──> 网络、Room、存储、媒体能力
下载服务 ──> 下载调度、合成、发布、持久化与通知
音乐播放服务 ──> MediaSession 与后台播放器
```

| 层级 | 编码要求 |
| --- | --- |
| View / Layout / Adapter | 渲染展示数据、收集交互、控制视图动画；不发网络请求、不读写数据库、不执行文件操作 |
| Activity / Fragment / Dialog | 绑定状态、处理跳转、系统选择器、权限与窗口行为；复杂业务交给 ViewModel 或用例 |
| ViewModel | 维护页面状态、响应操作、协调业务调用；不长期持有 Activity、View、Binding 或 Dialog 实例 |
| UseCase | 编排批量操作和跨能力业务流程；通过数据、结果与回调沟通，不依赖具体页面或对话框 |
| Manager / Service | 管理跨页面运行的任务与资源生命周期；不持有页面状态，也不借页面存活维持后台任务 |
| Network Repository | 处理接口请求、响应与业务模型转换；不直接操作页面 |
| Room Repository / DAO | Repository 组织查询、写入和事务，DAO 定义数据库操作；不承担 UI 决策 |
| Storage / Audio / JNI | 封装平台、媒体和文件边界；明确线程、资源所有权和失败语义 |

补充约定：

- 新增业务逻辑先查找现有用例、仓库与工具。不要在多个 ViewModel 中复制地址解析、清晰度匹配、文件命名或资源删除逻辑。
- `CommonLibs` 只扩展确有公共用途的基础能力，不能成为承载所有业务的容器。
- 网络层与 Room 层均有 `BiliVideoRepository`，使用时核对包名；同时使用时通过明确的 import 别名区分。
- 新的后台和数据能力不应反向依赖具体 Activity、Layout 或页面 UiState。
- 修改 `common/core/` 前检查所有使用者，确认问题属于公共契约；单页需求优先在业务层处理。

## 4. 新增页面的落点

以下以设置页面的实际结构说明 Compose 文件的组织，不要求把已有页面迁移到另一套目录：

```text
feature/compose/
├── activity/SettingsActivity.kt
├── layout/SettingsLayout.kt
└── viewmodel/settings/
    ├── SettingsViewModel.kt
    ├── SettingsUiState.kt
    ├── SettingsUiIntent.kt
    └── SettingsUiEvent.kt
```

XML 页面分别放在 `feature/viewbinding/view/` 和 `feature/viewbinding/viewmodel/` 的对应分类，布局放在已注册的 `res/views/` 资源根目录。仅属于某页的模型留在该功能范围；被多个页面共同使用的领域类型才进入 `common/model/`。

新增业务前确认：

- 页面数据来自哪个仓库、任务或配置入口，是否已有可复用用例。
- 状态由谁维护，系统动作由哪个宿主执行，后台操作由谁持有。
- 新增 Android 组件是否同步登记 Manifest；新增依赖是否放入版本目录。
- 类型或包重命名是否影响 DataBinding、反射、Manifest、序列化及 Intent 参数。

代码入口：[构建配置](../../app/build.gradle.kts)、[版本目录](../../gradle/libs.versions.toml)、[页面目录](../../app/src/main/java/cc/kafuu/bilidownload/feature/)、[公共能力](../../app/src/main/java/cc/kafuu/bilidownload/common/)。

相关专题：[页面实现与生命周期](./viewmodel-activity-compose.md)、[依赖组织与 Kotpref](./dependencies-and-kotpref.md)。
