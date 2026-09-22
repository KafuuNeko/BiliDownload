# Room 数据层

本专题用于 Entity、DAO、Repository、事务与数据库迁移。涉及实际文件或公共 URI 时同时阅读存储专题。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 数据流与目录

```text
ViewModel / UseCase / Manager / Service
  -> common/room/repository
  -> common/room/dao
  -> AppDatabase
```

| 目录 | 职责 |
| --- | --- |
| common/room/entity | 持久化字段、主键和实体映射 |
| common/room/dao | 查询与写入契约 |
| common/room/dto | 关联查询和聚合结果 |
| common/room/repository | 业务读写入口、事务与数据操作编排 |
| app/schemas | Room 导出的当前与历史数据库结构 |

网络响应模型、数据库实体和展示状态各自维护，不能因为字段相近而让页面直接承担网络解析和数据库写入。

## 2. Entity 与字段契约

- 表名、字段名、主键、默认值和关联关系属于持久化契约，修改前检查已有 schema 和调用方。
- 任务主键、`groupId`、`bvid`、`cid`、DASH 标识和资源主键各有含义，参数与 KDoc 明确区分。
- `TaskStatus.code` 等持久化枚举编码保持稳定；新增状态同步检查查询条件与页面操作策略。
- 新增可空字段明确空值表示什么。资源的 `file` 与 `contentUri` 不能合并为未经区分的路径字符串。
- 必要字段的单位、时间基准与来源写入 KDoc，避免含义仅存在于调用方。

## 3. DAO 与 Repository

- DAO 使用参数绑定定义查询，避免拼接外部输入；同步查询必须由合适的线程调用。
- 查询范围与排序体现业务需要，页面更新用已有 LiveData 等能力观察，不轮询整个数据库刷新一行。
- Repository 提供业务操作入口，组合多表操作时明确事务边界；不要把事务跨越到网络请求、用户等待或文件复制。
- UI 模型转换不能掩盖数据库缺失或资源不可用，应将对应状态传给业务层处理。

`DownloadRepository.createNewRecordIfAbsent()` 在同一事务中判断活动任务并写入任务及 DASH 记录。新增下载入口复用此方法，不能退回页面层先查后插；重复任务结果要由调用方明确处理。

## 4. 数据库与文件的边界

Room 提交成功不代表文件已经发布，文件复制成功也不代表最终资源记录已保存。跨存储流程通过检查点、可恢复记录和结果返回协调，不能宣称一个 Room 事务可原子回滚全部外部操作。

删除与发布顺序遵守[存储与资源生命周期](./storage-and-resource-lifecycle.md)，批量操作同时区分单项失败和全部失败。保持资源可定位，避免先删记录后失去清理或重试依据。

## 5. 版本与迁移

- 当前 `AppDatabase` 版本为 4，已有 `1 → 2 → 3 → 4` 自动迁移和迁移测试。修改数据结构时默认保护已有下载历史、资源信息和关联关系。
- Entity、DAO、Repository 各司其职；新增表或 DAO 同步注册到 `AppDatabase`，多表一致性操作在合适的事务边界内完成。
- 已发布结构发生变化时，更新数据库版本、相应迁移和导出的 schema，并补充迁移验证；保留历史 schema，不通过重写旧 schema 掩盖不兼容变化。
- 验证迁移不仅检查数据库能打开，还需确认历史字段、任务与资源关联、默认值及迁移后的业务行为。
- 不使用 `fallbackToDestructiveMigration`、删库或清空记录来绕过迁移问题。若需求确实涉及丢弃数据或缩小兼容范围，先明确影响范围和数据处理方案，沿用任务中已经确认的决定。

应用版本号和数据库版本分别管理。全局兼容决策见[主入口第 4.1 节](../coding-guidelines.md#41-版本与兼容性)，配置键与枚举偏好规则见[依赖组织与 Kotpref](./dependencies-and-kotpref.md)。

## 6. 数据变更核对流程

- 先确定受影响的表、已有版本及历史数据语义。
- 同步修改实体、DAO、Repository、数据库登记和必要迁移，导出新 schema。
- 在测试库写入有代表性的旧数据，执行真实迁移路径后验证字段与关联。
- 覆盖新安装、直接升级和跨多个版本升级，核对资源 URI、任务状态、默认值和历史记录仍可读。
- 文件位置、资源标识或任务状态同时变化时，补充迁移后的业务操作验证。

代码入口：[AppDatabase](../../app/src/main/java/cc/kafuu/bilidownload/common/room/AppDatabase.kt)、[DownloadRepository](../../app/src/main/java/cc/kafuu/bilidownload/common/room/repository/DownloadRepository.kt)、[DownloadResourceEntity](../../app/src/main/java/cc/kafuu/bilidownload/common/room/entity/DownloadResourceEntity.kt)、[迁移测试](../../app/src/androidTest/java/cc/kafuu/bilidownload/AppDatabaseMigrationTest.kt)、[schemas](../../app/schemas/)。
