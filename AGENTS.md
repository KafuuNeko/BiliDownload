# AI 开发约定

- 开始任务先阅读 `doc/coding-guidelines.md`，再按任务查阅相关专题。
- Kotlin 源码的格式化与交付检查遵循 `doc/guides/code-formatting.md`：依次执行 `formatApply`、同范围的 `formatCheck`，再由 AI 按 `doc/guides/code-elegance-and-readability.md` 完成样式审计。
- 样式审计修改了 Kotlin 代码时，重新执行格式化、格式检查和 AI 样式审计，直到最终改动通过；存在他人的未提交改动时，显式指定本次负责的文件范围。
