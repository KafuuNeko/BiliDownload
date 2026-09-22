# MVI 与 UiState 树

本专题用于设计页面状态、互斥阶段和状态的生命周期，适用于 Compose，也适用于 XML 页面中的聚合状态。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 状态的发布者

Compose 使用 `CoreCompViewModel<I, S>` 的 `uiStateFlow`；ViewModel 通过状态对象的 `setup()` 发布新值，UI 只读取。XML 页面使用 LiveData 或明确的聚合状态，由 ViewModel 统一更新。

状态表示“当前界面是什么”，Intent 表示“用户做了什么”，UiEvent / ViewAction 表示“宿主需要执行什么”。事件规则见[交互专题](./intent-and-uievent.md)。

## 2. 按页面结构组织状态

- 使用 `sealed class` / `sealed interface` 表达互斥阶段，使用不可变 `data class` 表达同一阶段的内容；避免多个 Boolean 组合出不可能状态。
- 简单页面保持平坦状态；复杂页面按顶部栏、列表、选择区、播放控制和弹窗等实际区域拆分子状态。共享数据放在共同父状态，避免每个组件重复保存。
- 区分加载中、无数据、失败与正常内容；刷新失败时是否保留已有内容应有明确行为。
- 列表选择、批处理进度等页面业务状态由 ViewModel 管理；跨页面任务进度来自下载快照或持久化记录，不能靠页面局部计数重建。
- 跨互斥状态仍需使用的数据放在父状态或 ViewModel 私有快照；需要跨进程保留的数据进入 Room、偏好或合适的恢复状态载体。
- 不把文件内容、完整网络响应、Cookie 或可变的全局集合塞入 UiState。播放器对象按现有播放链路的所有权管理，避免重组时创建或释放。

复杂下载页面可先画出下面的状态划分，再按实际需要建立类型；这是设计示意，不是现有类结构：

```text
DownloadScreen
├── TopBar：标题、可用操作
├── Content：Loading / Empty / List / Error
├── Selection：选中的任务 ID、当前可选 ID
├── BatchOperation：Idle / Running / Result
└── Dialog：None / ConfirmDelete / SelectStreams
```

子组件只接收自己需要的状态与回调。不要为了形式完整，给静态标签、每个按钮或可从其他字段推导出的值建立独立状态。

## 3. 数据生命周期

| 数据 | 存放位置 | 注意事项 |
| --- | --- | --- |
| 某个互斥阶段专用数据 | 对应子状态 | 切换阶段后可以丢弃 |
| 多个阶段共享的标题、对象身份 | 父状态 | 不依赖已退出的子状态取数据 |
| 页面内复用但不直接展示的快照 | ViewModel 私有字段 | 明确刷新、失效与释放条件 |
| 任务运行进度 | 下载快照或服务状态 | 页面展示它，不另建执行事实 |
| 下载历史及资源记录 | Room | 通过仓库查询和更新 |
| 用户全局设置 | AppModel | 只保存小体量偏好 |
| 焦点、短期动画 | 视图本地状态 | 不承载业务提交结果 |

可持久恢复的状态只保留必要标识和参数；Bitmap、Player、Context 及打开的资源句柄不进入恢复数据。

## 4. 更新与并发

- 使用不可变集合和 `copy()` 生成新状态，不原地修改已经发布的集合。
- 异步结果返回时核对页面仍在处理同一个对象或请求；切换视频、重新搜索后的旧结果不得覆盖新内容。
- 并行更新不同字段时，避免拿任务启动前的旧快照覆盖另一项已完成更新。确认当前状态再合并。
- 点击禁用属于展示反馈，操作入口仍需检查当前状态、任务身份与重复提交。
- 结束页面使用已有 UiEvent / ViewAction 通道；如业务需要结束过渡状态，应保留最后可渲染内容并阻止重复操作，不为所有页面强制新增同一种结束状态。

## 5. 多选与批处理

现有 `HistoryMultiSelectUiState` 用 `selectedIds` 与 `availableIds` 表达选择。列表刷新时将选择集与可选集取交集，避免操作已不存在或不再允许操作的任务。

```kotlin
// 列表刷新后仅保留仍可操作的任务，避免选择状态引用失效条目。
val nextSelection = selection.copy(
    selectedIds = selection.selectedIds intersect availableIds,
    availableIds = availableIds,
)
```

批量操作开始时确定输入快照；处理中列表变动不应悄悄改变已确认的目标。进度、完成数和失败数必须来自用例结果，区分全部成功、部分成功与取消。

## 6. 核对入口与验证

- [CoreCompViewModel](../../app/src/main/java/cc/kafuu/bilidownload/common/core/compose/CoreCompViewModel.kt)：状态发布接口。
- [HistoryMultiSelectUiState](../../app/src/main/java/cc/kafuu/bilidownload/feature/viewbinding/viewmodel/fragment/HistoryMultiSelectUiState.kt)：稳定 ID、多选更新与派生状态。
- [SettingsUiState](../../app/src/main/java/cc/kafuu/bilidownload/feature/compose/viewmodel/settings/SettingsUiState.kt)：简单设置页面状态。
- [MusicPlayerUiState](../../app/src/main/java/cc/kafuu/bilidownload/feature/compose/viewmodel/musicplayer/MusicPlayerUiState.kt)：播放、频谱及展示资源状态。

验证空态、失败态、刷新后选择变化、重复点击、旧请求迟到和页面重建；含媒体引用的状态同时核对[播放生命周期](./media-and-native.md)。
