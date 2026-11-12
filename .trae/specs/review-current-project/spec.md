# 全面代码审查 Spec

## Why
当前项目是 Minecraft 1.21.1 + NeoForge + Java 21 模组工程，需要在不修改源码的前提下，系统性识别 NeoForge API、客户端/服务端隔离、性能、边界情况与可维护性风险。审查报告将帮助后续按优先级决定修复范围。

## What Changes
- 浏览项目结构与关键配置，确认 Minecraft、NeoForge、Java、Mappings 与依赖信息。
- 审查主要源码包、资源文件、注册入口、事件监听、网络、数据组件、Mixin、DataGen 等相关模块。
- 必要时在本地 Gradle 缓存中核实 NeoForge/Minecraft 1.21.1 API，无法确认时在报告中明确标注“不确定，建议人工核实”。
- 生成 Markdown 格式审查报告，可保存为 `REVIEW.md`，但本轮不修改任何源码。
- **不执行代码修复、不推进后续 Phase、不改动功能逻辑。**

## Impact
- Affected specs: 代码审查、NeoForge 1.21.1 API 兼容性、Side 安全、性能与可维护性。
- Affected code: 只读检查 `build.gradle`、`gradle.properties`、`neoforge.mods.toml`、Java 源码包、资源目录、Mixin 配置、DataGen 与网络相关代码。

## ADDED Requirements
### Requirement: 项目架构审查
系统 SHALL 先读取项目构建配置与包组织方式，形成对当前模组架构的简要概览。

#### Scenario: 成功建立架构概览
- **WHEN** 审查开始
- **THEN** 报告包含 Minecraft/NeoForge/Java 版本、关键依赖、入口类、注册模块、事件模块、资源与数据生成组织方式。

### Requirement: NeoForge API 正确性审查
系统 SHALL 审查 DeferredRegister/DeferredHolder、事件总线、Data Component/Data Attachment、网络 Codec/StreamCodec、弃用 API 与 Mixin 注入点是否符合 Minecraft 1.21.1 + NeoForge 规范。

#### Scenario: 发现 API 风险
- **WHEN** 某处实现可能不符合 1.21.1 NeoForge API
- **THEN** 报告标注文件路径、大致行号、严重程度、问题描述与修改建议。

### Requirement: 客户端/服务端分离审查
系统 SHALL 检查通用代码是否误引用客户端专属类、服务端逻辑是否存在客户端类加载风险，以及 `@OnlyIn(Dist.CLIENT)` 或等价隔离是否准确。

#### Scenario: 发现 Side 安全风险
- **WHEN** 通用或服务端路径中存在客户端专属引用
- **THEN** 报告将其标为阻塞性或重要问题，并说明可能导致专用服务器崩溃的原因。

### Requirement: 性能、资源与边界情况审查
系统 SHALL 检查 tick、事件监听、静态集合、缓存、异常路径、空指针、并发访问与资源管理风险。

#### Scenario: 发现运行期风险
- **WHEN** 代码存在重复计算、无限增长缓存、未清理集合、空指针或未处理异常路径
- **THEN** 报告提供定位、严重程度与最小修改建议。

### Requirement: 审查报告输出
系统 SHALL 输出 Markdown 审查报告，结构包括项目架构概览与按模块/文件列出的问题。

#### Scenario: 审查完成
- **WHEN** 所有清单项完成
- **THEN** 用户收到整体总结，并可决定后续是否进入修复阶段。

## MODIFIED Requirements
无。

## REMOVED Requirements
无。
