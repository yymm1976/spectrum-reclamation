# Tasks
- [x] Task 1: 建立项目架构认知：读取构建配置、模组元数据、资源目录与 Java 包组织，确认版本、依赖、入口类和模块边界。
  - [x] SubTask 1.1: 读取 `build.gradle`、`gradle.properties`、`settings.gradle` 与 `neoforge.mods.toml`。
  - [x] SubTask 1.2: 浏览 `src/main/java`、`src/main/resources`、Mixin 与 DataGen 相关目录。
- [x] Task 2: 审查 NeoForge API 使用：检查注册、事件总线、Data Component/Data Attachment、网络包、弃用 API、Mixin 与 DataGen 用法。
  - [x] SubTask 2.1: 对不确定 API 在本地 Gradle 缓存中尝试核实。
  - [x] SubTask 2.2: 对无法确认的 API 在报告中明确标注“不确定，建议人工核实”。
- [x] Task 3: 审查 Side 安全、性能与边界情况：检查客户端/服务端隔离、tick/事件性能、静态缓存、空指针、异常与并发路径。
- [x] Task 4: 编写 Markdown 审查报告：按“项目架构概览”和“按模块/文件列出问题”组织，包含文件路径、大致行号、严重程度、问题描述与修改建议。
- [x] Task 5: 验证审查报告完整性：对照 checklist 确认报告覆盖所有审查重点，且未修改项目源码。

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 1
- Task 4 depends on Task 2 and Task 3
- Task 5 depends on Task 4
