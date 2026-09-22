# 界面资源与主题

本专题用于布局、文案、图标、颜色和浅深色样式的修改。设计依据是本项目现有主题资源、公共组件和对应页面。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 实现入口

| 页面体系 | 样式与组件入口 |
| --- | --- |
| XML/DataBinding | res/values 中的 themes、styles、colors、dimens，以及 res/views 布局 |
| XML 深色资源 | res/values-night |
| Compose | common/core/compose/theme、AppTheme、AppTopBar 与各 Layout |
| 默认和中文文案 | res/values/strings.xml、res/values-zh/strings.xml |

已有页面保持视觉层级、操作位置和语义一致。调整公共主题、颜色或组件参数时，检查所有使用页面，而非只看当前截图。

## 2. 布局与资源组织

- XML 布局按 activity、fragment、dialog、include、item 放入已注册的资源根目录。
- 新目录需要同步 `app/build.gradle.kts` 的 sourceSets；资源合并后名称全局可见，不能只靠文件夹区分同名资源。
- Compose 页面复用 `CoreCompActivity` 与 `AppTheme` 的包装，公共组件放在 `feature/compose/views/`，避免页面复制主题定义。
- 尺寸、颜色和字体复用当前资源与主题语义；新增颜色同时评估浅色、深色和选中、禁用、错误状态。
- 页面边缘、系统栏和画中画控件使用对应窗口行为，避免盲目加固定高度或重复 inset。

## 3. 文案与可访问性

- 新增用户可见文案使用字符串资源，同步维护默认文案与 `values-zh/strings.xml`，保持格式占位符一致。
- 颜色、字体、尺寸优先复用当前 XML 资源与 Compose 主题；新增界面检查浅色、深色、长文本、系统字体缩放和系统栏留白。
- 可操作图标提供适当的无障碍描述；错误提示说明失败结果和下一步操作，避免直接展示异常堆栈。

- 用户可见文本使用资源占位符表达动态值，避免通过字符串拼接破坏语序与翻译。
- 图标按钮有可理解的操作说明，状态不能只靠颜色区分。
- 批量操作提示准确区分成功、跳过、失败和取消；错误信息与可恢复动作对应。
- 文件路径或资源名确需展示时，只展示操作需要的内容；错误信息不暴露 Cookie、鉴权地址和堆栈。

## 4. Preview 与设备检查

- 主要 Compose Layout 提供浅色、深色 Preview，沿用 `ActivityPreview` / `AppTheme` 的现有包装方式。
- Preview 使用独立假数据，不启动网络、下载、播放器或数据库读取。
- 检查窄屏、长标题、较大系统字体、列表空态、加载和失败态；业务按钮不能被导航栏或悬浮控件遮挡。
- 涉及选中、禁用、播放暂停或进度变化时，实际触发状态切换，确认图标与文案同步。
- XML 与 Compose 的页面跳转检查主题衔接和系统栏可读性，旋转与返回后的页面状态按需求保留。

## 5. 交互边界

权限和系统选择器的触发规则见[UiIntent、UiEvent 与 ViewAction](./intent-and-uievent.md)。页面显示“已选择目录”不等于授权仍有效，“已完成”也不能仅由动画结束决定；真实业务结果由 ViewModel 和用例发布。

代码入口：[XML 资源](../../app/src/main/res/)、[Compose 主题](../../app/src/main/java/cc/kafuu/bilidownload/common/core/compose/theme/)、[CoreCompActivity 与 ActivityPreview](../../app/src/main/java/cc/kafuu/bilidownload/common/core/compose/CoreCompActivity.kt)、[AppTopBar](../../app/src/main/java/cc/kafuu/bilidownload/feature/compose/views/AppTopBar.kt)。
