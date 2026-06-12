# Tasks
- [x] Task 1: 复核修复范围与现有代码：读取 `REVIEW.md` 和相关源码，确认所有仍成立问题的当前实现与测试入口。
  - [x] SubTask 1.1: 读取活体陷阱、注册、方块实体和资源定义。
  - [x] SubTask 1.2: 读取流星弩、原版弩参考源码和沉重之矛逻辑。
  - [x] SubTask 1.3: 读取铜管网络、铜管方块、端点方块和方块实体。
  - [x] SubTask 1.4: 读取纹饰效果、事件分发、语言文件与客户端提示逻辑。
- [x] Task 2: 为关键行为建立验证：优先添加或调整可运行的 GameTest/单元级验证，覆盖活体陷阱状态、流星弩耐久、铜管网络契约和文本键完整性。
- [x] Task 3: 修复活体陷阱状态问题：实现持久化状态、释放复位冷却、重启恢复或安全释放，并保留必要中文注释。
- [x] Task 4: 修复流星弩和沉重之矛边界：补齐耐久消耗、创造模式体验和玩家传送同步方式，并避免 Side 越界。
- [x] Task 5: 修复铜管网络问题：处理未加载区块 `networkId` 重新校验、传输契约一致、合并遍历优化和端点加载顺序耦合。
- [x] Task 6: 收敛重复代码与性能分配：抽取属性纹饰公共逻辑、铜管公共逻辑，降低紫水晶纹饰和纹饰事件 tick 分配。
- [x] Task 7: 修复文本国际化：替换剩余硬编码玩家可见文本，补充 `zh_cn.json` 翻译键。
- [x] Task 8: 更新审查报告状态：在 `REVIEW.md` 中记录已修复项与必要 `// CONCERN`。
- [x] Task 9: 构建与质量验收：运行针对性验证和 `./gradlew.bat build --no-daemon`，修复所有由本轮改动导致的问题。
- [ ] Task 10: 代码审查与最终提交：执行代码审查；通过后按要求 `git add -A`、`git commit`、`git push -u origin main`，并通过 GitHub API 验证远程文件内容与本地一致。

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 2
- Task 5 depends on Task 2
- Task 6 depends on Task 3, Task 5
- Task 7 depends on Task 1
- Task 8 depends on Task 3, Task 4, Task 5, Task 6, Task 7
- Task 9 depends on Task 8
- Task 10 depends on Task 9
