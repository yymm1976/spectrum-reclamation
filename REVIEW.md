# Spectrum Reclamation 当前项目审查报告

## 审查范围

- 项目：Spectrum Reclamation，Minecraft 1.21.1 + NeoForge 模组。
- 目标：基于已收集的第一轮深度审查与第二轮 AI 交叉审查结论，形成当前可执行的总览报告。
- 范围：构建配置、模组元数据、Java 源码包、资源目录、Mixin 配置、Data Component、事件总线、Side 安全、性能与边界情况。
- 限制：本轮只生成审查报告，不修改项目源码或资源文件。

## 项目架构概览

### 版本与依赖

| 项目 | 当前值 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.218 |
| Java | 21 |
| Mappings | Parchment 2024.11.17 for Minecraft 1.21.1 |
| Mod ID | spectrum_reclamation |
| Mod 版本 | 0.1.0 |
| 前置依赖 | Spectrum >= 1.11.8 |

### 构建与元数据

- `build.gradle.kts` 使用 `net.neoforged.moddev` 2.0.139，并配置 client、server、gameTestServer、data 四类运行任务。
- `gradle.properties` 固定 Minecraft、NeoForge、Parchment、Mod ID 与版本信息。
- `src/main/templates/META-INF/neoforge.mods.toml` 声明 `javafml`、Mixin 配置、Access Transformer，以及 NeoForge、Minecraft、Spectrum 依赖。
- `sourceSets.main` 同时包含 `src/main/resources` 与 `src/generated/resources`，但当前仓库未发现专门的 DataGen Provider 源码包。

### 主要包组织

- `registry/`：集中注册方块、物品、实体、方块实体、菜单、药水、状态效果、纹饰材料与 Data Components。
- `block/`、`block_entity/`、`util/`、`event/`：实现活体陷阱、铜管网络、服务端 tick 扫描与运行时事件。
- `item/custom/`、`entity/`：实现沉重之矛、流星弩、追溯指针、瞄准镜等物品和投射物逻辑。
- `trim/` 与 `trim/effect/`：实现纹饰效果注册、事件分发与各材料效果。
- `client/`、`screen/`：客户端渲染、FOV、Tooltip 与菜单界面逻辑，通过客户端专属订阅隔离。
- `mixin/`：当前仅有 `FletchingTableMixin`，用于拦截原版制箭台交互并打开自定义 GUI。

## NeoForge API 与机制审查

### 已确认较好的实现

- 注册体系整体使用 `DeferredRegister` / `DeferredHolder`，并在主类构造器中注册到 MOD_BUS，符合 1.21.x 生命周期要求。
- 物品自定义数据使用 `DataComponentType`，并配置 `Codec` 与 `networkSynchronized`，方向符合 1.21.x 替代直接 NBT 的要求。
- 运行时事件通过 `NeoForge.EVENT_BUS` 注册，注册类事件和运行时事件边界清晰。
- 客户端事件类通过 `@EventBusSubscriber(value = Dist.CLIENT)` 限制加载，降低专用服务器类加载崩溃风险。
- Mixin 使用 `@Inject(at = @At("HEAD"), cancellable = true)`，没有使用高风险 `@Overwrite`。

### 需要注意的 API / 机制点

- 未发现自定义网络包实现，因此 `CustomPacketPayload` / `StreamCodec` 网络协议暂无审查对象。
- 当前未发现 Data Attachment 使用；实体或世界级持久状态主要仍依赖静态集合或原版 NBT 状态，这也是多个跨重启问题的根因。
- DataGen 运行任务已配置，但未发现 Provider 源码；现有资源多为手写 JSON，应在后续新增内容时优先补齐 Provider。
- `MobEffectInstance.update()` 的具体语义已不再影响当前紫水晶纹饰实现；源码已改为 `removeEffect + addEffect` 替换负面效果实例。

## P0/P1 源码复核结论

- 本次复核直接对照当前源码，而非只沿用第一轮/第二轮审查结论。
- 当前 Task1-8 覆盖的 P0/P1 修复项均已落地：`CB-01`、`CB-02`、`CB-03`、`CB-04`、`CB-05`、`CB-06`、`CS-01`、`CS-02`、`CS-03`。
- 已确认的维护性与性能修复项：`CS-04`、`CS-05`、`CS-06`、`CS-08`、`CS-09`、`CS-10`、`CN-02`、`CN-03`、`CN-04`、`CN-05`、`CN-06`。
- 仍保留为观察项的问题均已在下方 `// CONCERN` 索引标注，不再作为阻塞当前修复批次的未完成项。

## 阻塞问题当前状态

| ID | 严重程度 | 当前状态 | 源码依据 |
| --- | --- | --- | --- |
| CB-01 | P0 | 已修复 | `LivingTrapBlock` 已改为 `LivingTrapBlockEntity` ticker 驱动，吞入状态由方块实体持久化，释放与冷却复位不再依赖旧 `scheduleTick` 死锁状态机。 |
| CB-02 | P0 | 已修复 | `AmethystTrimEffect` 使用 `removeEffect()` + `addEffect()` 替换负面效果实例，并由 `reviewFixGuards` 禁止回退到 `update()`。 |
| CB-03 | P0 | 已修复 | `FletchingTableMenu` 输出槽已在取走结果时统一消耗输入，普通点击与 Shift+点击路径均受输出槽逻辑约束。 |
| CB-04 | P1 | 已修复 | `SRItems` 为陨星弩补充 `durability(465)`，`MeteorCrossbowItem.performShooting()` 委托原版 `CrossbowItem.performShooting()`，恢复原版耐久消耗链路。 |
| CB-05 | P1 | 已修复 | `TrimEffectEventHandler.onPlayerTick()` 调用 `EchoShardTrimEffect.enforceSilenceState()`，为服务器重启后的静音状态提供兜底恢复。 |
| CB-06 | P1 | 已修复 | `SREventHandler.onLivingEntityJoinLevel()` 会读取并清理深灰涂装留下的静音截止标记，过期后恢复实体声音状态。 |

## 重要问题当前状态

| ID | 严重程度 | 当前状态 | 源码依据 |
| --- | --- | --- | --- |
| CS-01 | P1 | 已修复 | 活体陷阱已引入 `LivingTrapBlockEntity`，被困实体 UUID、吞入倒计时与冷却倒计时由方块实体保存和恢复。 |
| CS-02 | P1 | 已修复 | `CopperPipeBlockEntity.onLoad()` 先调用 `revalidateNetworkId()`，未加载旧节点会在下次加载时按相邻已加载铜管自愈网络 ID。 |
| CS-03 | P1 | 已修复 | 制箭台打开菜单时同步 `BlockPos`，客户端读取坐标，`stillValid()` 已按方块距离校验。 |
| CS-04 | P2 | 已修复 | 属性型纹饰已收敛到 `AbstractAttributeTrimEffect`，子类只保留属性、材料 ID 与数值差异。 |
| CS-05 | P2 | 已修复 | 铜管与端点方块共享逻辑已下沉，连接属性、含水与碰撞箱逻辑不再分散维护。 |
| CS-06 | P2 | 已修复 | `TrimEffectRegistry.lookupFromArmor()` 已按纹饰组合缓存，并在装备变化时清理缓存。 |
| CS-07 | P2 | 已部分修复，保留 `// CONCERN` | `CopperPipeNetwork.findNearestExit()` 已有出口缓存，拓扑变化时失效；逐次传输路径 BFS 仍作为后续性能观察项。 |
| CS-08 | P2 | 已修复 | `WaypointCompassItem.inventoryTick()` 写入前比较现有 `LODESTONE_TRACKER`，避免每 tick 无差异同步。 |
| CS-09 | P2 | 已修复 | `CopperPipeNetwork.transfer()` 文档与实现已统一为“调用方已提取物品后尝试插入出口”，失败不再生成掉落物。 |
| CS-10 | P3 | 已修复 | 追溯指针、精准追溯指针与瞄准镜提示已使用翻译键，语言文件补齐并由哨兵检查重复键。 |

## 源码复核后移除的问题

| 原 ID | 原严重程度 | 当前状态 | 源码依据 |
| --- | --- | --- | --- |
| CB-02 | P0 | 已修复，不再作为阻塞问题 | `AmethystTrimEffect` 当前通过 `removeEffect()` + `addEffect()` 替换负面效果，没有再调用 `MobEffectInstance.update()`。 |
| CB-03 | P0 | 已修复，不再作为阻塞问题 | `FletchingTableMenu` 输出槽虽然仍是匿名 `Slot`，但已重写 `onTake()` 并在普通点击取走输出时收缩输入槽。 |
| CB-05 | P1 | 已修复，不再作为关键问题 | `TrimEffectEventHandler.onPlayerTick()` 会调用 `EchoShardTrimEffect.enforceSilenceState()` 兜底恢复静音。 |
| CB-06 | P1 | 已修复，不再作为关键问题 | `SREventHandler.onLivingEntityJoinLevel()` 已读取 `spectrum_reclamation:silent_until_tick`，过期后恢复声音并清理标记。 |
| CS-03 | P1 | 已修复，不再作为关键问题 | `FletchingTableMixin` 打开菜单时写入 `BlockPos`，`FletchingTableMenu.fromNetwork()` 读取坐标，`stillValid()` 已按 8 格距离校验。 |
| CS-06 | P2 | 已修复，不再作为性能问题 | `TrimEffectRegistry.lookupFromArmor()` 已基于纹饰组合哈希缓存结果，装备变化时通过 `clearCache()` 失效。 |
| CS-07 | P2 | 已部分修复，降级为观察项 | `CopperPipeNetwork.findNearestExit()` 已有 `exitCache`，并在节点/端点变更时失效；`findPath()` 仍会按传输路径执行 BFS。 |
| CS-08 | P2 | 已修复，不再作为性能问题 | `WaypointCompassItem.inventoryTick()` 已比较当前 `LODESTONE_TRACKER` 后再写入，跨维度也只在存在 tracker 时移除。 |

## 本轮新增修复状态

| 原 ID | 当前状态 | 源码依据 |
| --- | --- | --- |
| CN-02 纹饰事件分发重复创建 `HashMap` | 已修复 | `TrimEffectEventHandler` 通过统一计数入口复用统计逻辑，并支持处理器自定义 tick 间隔与错峰执行。 |
| CN-03 铜管合并遍历整个合并后网络 | 已修复 | `CopperPipeBlockEntity.mergeNeighborNetworks()` 已遍历 `oldNetwork.getNodesSnapshot()`，避免扩大更新范围。 |
| CN-04 铜管端点加载顺序耦合 | 已修复 | `CopperPipeEndpointBlockEntity.syncWithAdjacentPipeNetwork()` 可由端点加载、模式切换和相邻铜管加载/合并后调用。 |
| CN-05 陨星弩创造模式仍需背包有矛 | 已修复 | `MeteorCrossbowItem.use()` 在创造模式且无弹药时写入虚拟沉重之矛，保留原版创造体验。 |
| CN-06 沉重之矛紫色涂装同步风险 | 已修复 | `ThrownHeavySpear` 传送后设置双方 `hurtMarked`，让服务端运动状态同步到客户端。 |

## 轻微问题与后续优化

- // CONCERN: [RISK] `AmethystTrimEffect` 仍通过创建替换实例缩短负面效果时长；这是避免 `MobEffectInstance.update()` 语义问题的安全实现，但在大量负面效果并存时仍有分配压力。
- // CONCERN: [RISK] `CopperPipeNetwork.findPath()` 仍会在实际传输时执行 BFS；最近出口选择已缓存，但完整路径缓存可作为后续性能优化，不阻塞当前修复批次。
- // CONCERN: [RISK] `EchoShardTrimEffect` 与深灰涂装都可能改写实体 `silent` 状态；当前已有跨重启兜底恢复，但与其他模组同时修改静音状态时仍建议后续引入来源标记。

## 第一轮结论当前状态

| 原 ID | 当前状态 | 说明 |
| --- | --- | --- |
| B-01 紫水晶纹饰 `floor(reduction * 2)=0` | 已修复 | 现已改为 `* 20`，且当前实现已使用 `removeEffect + addEffect` 避开 `update()` 语义问题。 |
| B-02 下界合金纹饰 1 件即满 | 已修复 | 现已改为 `linear(0.0, 0.25)`。 |
| B-03 活体陷阱静态 Map 重启丢失 | 已修复 | 已归入 CS-01；活体陷阱已改为 `LivingTrapBlockEntity` 持久化，不再依赖静态 Map 保存被困实体。 |
| B-04 铜管网络静态 Map 未清理 | 已修复 | `CopperPipeTickHandler.onServerStopping()` 已调用 `CopperPipeNetwork.clearAll()`。 |
| S-01 钻石纹饰破坏多态设计 | 误判 | 第二轮确认当前代码实际通过多态处理，无需按第一轮建议修复。 |
| S-02 回声碎片覆盖 silent | 已补兜底，保留 CONCERN | 当前已有装备变化恢复和玩家 tick 兜底；跨模组静音来源冲突已记录为观察项。 |
| S-03 金纹饰缺少客户端检查 | 不存在 | 事件分发层已有 `!isClientSide` 拦截。 |
| S-05 沉重之矛棕色涂装复制装备 | 已修复 | 当前已有清空目标装备槽逻辑。 |
| S-06 相邻铜管不合并网络 | 已修复 | 当前已有邻居网络发现与合并逻辑。 |
| S-07 属性类代码克隆 | 已修复 | 已归入 CS-04；属性型纹饰已收敛到抽象基类。 |
| S-08 制箭台输出不消耗输入 | 已修复 | 普通点击通过输出槽 `onTake()` 消耗输入，Shift+点击路径也会触发输出槽取走逻辑。 |
| S-09 追溯指针高频写入 DataComponent | 已修复 | `inventoryTick()` 已在写入前比较现有 `LODESTONE_TRACKER`。 |
| S-10 铜管优先空槽而非合并 | 已修复 | 当前已有两遍扫描策略。 |

## CONCERN 索引

- [RISK] `AmethystTrimEffect` — 安全替换负面效果实例仍有分配压力，后续若要优化需谨慎避免回退到 `update()`。
- [RISK] `CopperPipeNetwork.findPath()` — 最近出口已缓存，但逐次传输路径 BFS 仍可能在大型网络中形成性能压力。
- [RISK] `EchoShardTrimEffect` / 深灰涂装静音逻辑 — 当前已有恢复兜底，但与其他模组同时控制 `silent` 时仍可能互相覆盖。

## 审查结论

项目的 NeoForge 注册结构、事件总线区分、客户端隔离和 Data Component 使用方向整体正确。Task1-8 覆盖的审查修复项已同步为已修复状态；剩余内容均作为 `// CONCERN` 观察项记录，不阻塞 Task9 的构建与质量验收。下一步应执行 `./gradlew.bat build --no-daemon`，以构建结果作为本轮修复批次的硬性验收依据。
