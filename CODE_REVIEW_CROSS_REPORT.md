# 👁️ Spectrum Reclamation AI 交叉审查报告（第二轮）

**项目**: Spectrum Reclamation (Minecraft 1.21.1 NeoForge Mod)
**审查模式**: 4 位 AI 审查员并行交叉审查
- **Reviewer A** — 功能正确性 & 游戏逻辑
- **Reviewer B** — 性能 & 资源管理
- **Reviewer C** — 架构 & 设计模式
- **Reviewer D** — Minecraft/NeoForge 最佳实践
**审查日期**: 2026-06-08
**交叉验证基准**: 第一轮审查报告 (CODE_REVIEW_REPORT.md)

---

## 📊 交叉审查概览

### 与第一轮审查的对比

| 类别 | 数量 | 说明 |
|------|------|------|
| **第一轮发现，已修复** | 5 项 | B-01, B-02, S-05, S-06, S-10 已在代码中修正 |
| **第一轮误判** | 1 项 | S-01 DiamondTrimEffect 多态设计实际上正确 |
| **第一轮确认有效** | 3 项 | B-03, B-04, S-08(部分) 仍存在 |
| **本轮新发现 — Blocker** | 6 项 | 跨审查员共识确认的高置信度问题 |
| **本轮新发现 — Suggestion** | 10 项 | 至少 2 位审查员交叉确认 |
| **本轮新发现 — Nit** | 6 项 | 单审查员发现但值得记录 |

---

## 🔴 Blockers（多审查员交叉确认）

### CB-01: LivingTrapBlock 状态机死锁 — 方块一次性使用后永久失效

**交叉确认**: Reviewer A 🔴 + Reviewer D 🔴
**文件**: `block/LivingTrapBlock.java`（第 183-196 行）
**严重性**: 🔴 P0 — 功能完全失效 + 无限计划刻泄漏

```java
// tick() 方法的两分支逻辑存在死锁
if (state.getValue(COOLDOWN)) {
    // 释放实体... 然后 scheduleTick(200)
    // ❌ 从未将 COOLDOWN 设为 false
} else {
    // 只有这里会将 COOLDOWN 设为 false
    // ❌ 但此分支在 entityInside 总是设 COOLDOWN=true 的前提下永不可达
}
```

**问题链**:
1. `entityInside()` 检测到实体 → `COOLDOWN = true` → `scheduleTick(100)`
2. 100 ticks 后 `tick()` 执行：`COOLDOWN=true` 分支 → 释放实体 → `scheduleTick(200)`
3. 200 ticks 后 `tick()` 再次执行：`COOLDOWN` 仍为 true → 再次 `scheduleTick(200)`
4. **无限循环**：COOLDOWN 永远不会被设为 false，陷阱变为一次性方块
5. 每次 tick 无意义地唤醒方块，占用 ServerLevel tick 调度器

**建议**: 在释放实体后将 `COOLDOWN` 设为 false（`level.setBlock(pos, state.setValue(COOLDOWN, false), 3)`），或改用 BlockEntity tick 替代 scheduleTick 状态机。

---

### CB-02: AmethystTrimEffect — `update()` 语义错误导致效果完全失效

**交叉确认**: Reviewer A 🔴（独立发现）
**文件**: `trim/effect/AmethystTrimEffect.java`（第 58-61 行）
**严重性**: 🔴 P0 — 功能完全失效（与第一轮 B-01 不同根因）

```java
MobEffectInstance current = entity.getEffect(effect);
int remaining = current.getDuration() - extraConsumption;
entity.getActiveEffectsMap().get(effect).update(
    new MobEffectInstance(effect, remaining, current.getAmplifier())
);
```

**问题**: Minecraft 的 `MobEffectInstance.update()` 源码逻辑为：仅当**新实例的 amplifier > 当前 amplifier**，或**amplifier 相等且新 duration > 当前 duration** 时，才会更新。

传入的 `remaining = current - extraConsumption` 必然比当前 duration **更短**，`update()` 永远返回 false，效果时长完全不被修改。

**注意**: 第一轮报告指出的 `Math.floor(reduction * 2) = 0` 问题**已被修复**（现改为 `* 20`），但即使数值正确，`update()` 的语义仍然导致功能失效。

**建议**: 使用反射直接修改 `MobEffectInstance.duration` 字段，或通过 `entity.removeEffect()` + `entity.addEffect()` 重建实例（后者会重置效果图标闪烁，用户体验较差）。

---

### CB-03: FletchingTableMenu — 普通点击输出槽导致无限复制

**交叉确认**: Reviewer A 🔴 + Reviewer C 🔴 + Reviewer D 🔴
**文件**: `inventory/FletchingTableMenu.java`
**严重性**: 🔴 P0 — 物品复制漏洞

**问题**: 输出槽是 `Slot` 而非 `ResultSlot`，未重写 `onTake()`。当玩家**普通点击**（非 Shift+点击）取走输出物时：
1. `AbstractContainerMenu.clicked()` 调用默认 `Slot.onTake()`，**不消耗输入**
2. 输出槽变空触发 `slotsChanged()` → `updateOutput()`
3. 输入未被消耗，配方仍然匹配，输出槽**立即重新填充**
4. 玩家可反复点击无限获取输出物

**第一轮状态**: S-08 已报告 Shift+点击不消耗材料，代码中 Shift+点击 (`quickMoveStack`) 部分**已修复**，但普通点击漏洞**未被修复且未被第一轮发现**。

**建议**: 将输出槽改为 `ResultSlot`（原版工作台的输出槽类型），或在 `FletchingTableMenu` 中重写输出槽的 `onTake()` 方法，在取走时消耗输入材料。

---

### CB-04: MeteorCrossbowItem — 发射时不消耗弩的耐久度

**交叉确认**: Reviewer D 🔴（独立发现，属于游戏机制漏洞）
**文件**: `item/custom/MeteorCrossbowItem.java`（第 67-128 行）
**严重性**: 🔴 P1 — 武器耐久机制被破坏

**问题**: `MeteorCrossbowItem` 完全重写了 `use()` 并自行创建弹射物，但**遗漏了原版 `CrossbowItem` 的 `hurtAndBreak(1)` 耐久消耗逻辑**。弩可以无限次发射而不损坏。

**建议**: 在发射逻辑（`!level.isClientSide()` 分支内）添加：
```java
crossbowStack.hurtAndBreak(1, player,
    player.getUsedItemHand() == InteractionHand.MAIN_HAND
        ? EquipmentSlot.MAINHAND
        : EquipmentSlot.OFFHAND);
```

---

### CB-05: EchoShardTrimEffect — 服务器崩溃/重启后玩家可能永久静音

**交叉确认**: Reviewer D 🔴（独立发现）
**文件**: `trim/effect/EchoShardTrimEffect.java` + `trim/TrimEffectEventHandler.java`
**严重性**: 🔴 P1 — 跨重启状态泄漏

**问题链**:
1. 玩家装备 4 件回声碎片纹饰 → `setSilent(true)` → 该状态写入实体 NBT
2. 服务器在玩家脱下一件装备前崩溃/重启
3. 重启后玩家以 3 件装备登录
4. `LivingEquipmentChangeEvent`**仅在装备变化时触发**，登录加载时不会触发
5. `onEquipmentChange` 中 `count < 4` 恢复 `silent=false` 的逻辑**永远不会执行**
6. **玩家永久静音**

**建议**: 在 `TrimEffectEventHandler.onPlayerTick` 中为 EchoShardTrimEffect 增加兜底检查：若当前 `count < 4` 但实体 `isSilent()`，则恢复 `setSilent(false)`。

---

### CB-06: ThrownHeavySpear 深灰涂装 — 跨重启后实体永久静音

**交叉确认**: Reviewer D 🔴（独立发现）
**文件**: `entity/ThrownHeavySpear.java`（第 536-554 行）
**严重性**: 🔴 P1 — 跨重启状态泄漏

**问题**: `applyDarkGrayEffect` 使用 `TickTask` 延迟 40 ticks 恢复 `silent=false`，同时将恢复时间戳写入 `persistentData`。但**没有任何地方在实体 tick 或登录时读取该时间戳**。若服务器在 40 ticks 内重启，实体加载时 `Silent=true` 从 NBT 恢复，但 `TickTask` 已丢失。

**建议**: 在 `ServerPlayer` 登录事件或 `LivingEntity` 通用 tick 逻辑中检查 `spectrum_reclamation:silent_until_tick`，若 `level.getGameTime() > savedTick` 则 `setSilent(false)` 并清除 NBT 键。

---

## 🟡 Suggestions（多审查员交叉确认）

### CS-01: LivingTrapBlock 静态 Map 在服务器重启后丢失（第一轮 B-03 确认）

**交叉确认**: Reviewer A 🟡 + Reviewer D 🔴
**状态**: 仍未修复，且因 CB-01 状态机死锁变得更加严重

**问题**: `TRAPPED_ENTITIES` 是静态 `ConcurrentHashMap`，重启后清空。已放置方块不会触发 `onPlace()`，导致：
1. 陷阱方块永远卡在冷却状态（无 tick 重置）
2. 被困实体永久不可见、无敌、无重力
3. `recoverStrayEntities()` 仅在 `onPlace()` 触发时执行，重启后永不执行

**建议**: 引入 `LivingTrapBlockEntity`，将被困实体的 UUID、计时器、冷却状态全部写入 NBT。用 BlockEntity tick 替代 `scheduleTick`。

---

### CS-02: CopperPipeNetwork 网络合并时未加载区块节点 networkId 无法更新

**交叉确认**: Reviewer C 🟡 + Reviewer D 🟡
**文件**: `block_entity/CopperPipeBlockEntity.java`（第 160-196 行）

**问题**: `mergeNeighborNetworks` 遍历 `currentNetwork.getNodes()` 更新 `networkId`，但如果某些节点所在区块未加载，`getBlockEntity` 返回 null，这些节点的 `networkId` 仍指向旧网络。当这些区块后续加载时，`onLoad` 会尝试注册到旧网络（或创建幽灵网络），导致网络分裂和数据不一致。

**建议**: 让网络 ID 成为拓扑推导结果而非每个 BE 独立持有；或在 `saveAdditional` 中不序列化内存中的 `networkId`，加载时动态查询。

---

### CS-03: FletchingTableMenu.stillValid 始终返回 true

**交叉确认**: Reviewer C 🔴 + Reviewer D 🔴
**文件**: `inventory/FletchingTableMenu.java`（第 335-337 行）

**问题**: `stillValid` 恒返回 `true`，玩家可以无限远离制箭台并继续通过 GUI 合成。原版 `AbstractContainerMenu` 要求基于容器持有者的位置进行校验。

**根本原因**: 客户端构造器接收 `FriendlyByteBuf buf` 却完全不读取，菜单不知道制箭台的 BlockPos。

**建议**: 服务端打开菜单时通过 `FriendlyByteBuf` 写入 BlockPos，客户端读取并保存，在 `stillValid` 中检查距离。

---

### CS-04: Iron/Netherite/Redstone/TurtleTrimEffect 四者代码高度重复

**交叉确认**: Reviewer C 🔴（高置信度）
**文件**: 四个属性修饰器纹饰效果类

**问题**: 四个类除目标 `Attribute`、材料 ID 和数值常量外，逻辑 95% 相同。每新增一种属性修饰效果都需要复制 60+ 行代码。

**建议**: 提取抽象基类 `AttributeModifierTrimEffect`，子类只需提供常量和属性引用。或直接在 `VanillaTrimEffects` 中提供数据驱动注册方法。

---

### CS-05: CopperPipeBlock 与 CopperPipeEndpointBlock 大量重复逻辑

**交叉确认**: Reviewer C 🔴（高置信度）
**文件**: `block/CopperPipeBlock.java` + `block/CopperPipeEndpointBlock.java`

**问题**: 两者在方块状态属性、碰撞箱臂、含水逻辑、连接判定、`getDirectionFromPos` 方法等方面大量重复。

**建议**: 提取 `AbstractCopperPipeBlock` 基类，下沉共享逻辑。

---

### CS-06: TrimEffectRegistry.lookupFromArmor 每 tick 重建 ArrayList

**交叉确认**: Reviewer B 🔴（高置信度）
**文件**: `trim/TrimEffectRegistry.java`（第 106-145 行）

**问题**: `onPlayerTick` 每 tick 对每个玩家调用 `lookupFromArmor`，无条件 `new ArrayList<>()`，遍历 4 个盔甲槽位、解包 Optional、统计计数。即使结果与上一 tick 完全相同，也全部重建。

**影响**: 20 人服务器下，每 tick 产生数十个临时 ArrayList。

**建议**: 在 `LivingEntity` 上附加缓存字段（或通过 `LivingEquipmentChangeEvent` 失效），只在装备变化时重新计算并缓存处理器列表和件数统计。

---

### CS-07: 铜管 BFS 无缓存，每 20 ticks 全图搜索

**交叉确认**: Reviewer B 🔴 + Reviewer A 🟡
**文件**: `event/CopperPipeTickHandler.java` + `util/CopperPipeNetwork.java`

**问题**: `findNearestExit` / `findPath` 每次传输都新建 `LinkedList` + `HashSet`，从零开始 BFS。出口拓扑在绝大多数 tick 中是不变的。

**建议**: 为每个入口点缓存其最近的出口 `BlockPos`；缓存仅在 `addNode` / `removeNode` / `addExitPoint` / `removeExitPoint` 时失效。

---

### CS-08: WaypointCompassItem 每 tick 无条件写入 DataComponents

**交叉确认**: Reviewer B 🔴 + Reviewer D 🟡 + Reviewer A 💭
**文件**: `item/custom/WaypointCompassItem.java`

**问题**: `inventoryTick` 每秒 20 次新建 `LodestoneTracker` 并执行 `stack.set()`，即使数据未变。`ItemStack.set()` 标记 dirty，可能触发服务端→客户端的库存同步包。

**建议**: 设置前先检查现有 tracker 是否需要更新；或降低检查频率（如每 5 tick 执行一次）。

---

### CS-09: CopperPipeNetwork.transfer() 实现与文档严重不符

**交叉确认**: Reviewer C 🟡（高置信度）
**文件**: `util/CopperPipeNetwork.java`（第 382-437 行）

**问题**: 方法注释说明"从入口容器提取物品，通过网络瞬移到出口容器"，且承诺"出口满时放回入口"。但实际代码**从未访问入口容器**，直接插入传入的 `itemStack`；出口满时物品被掉落为 `ItemEntity`，**未归还入口**。

**建议**: 重构 transfer 签名或实现，或至少修正注释以匹配实际行为。

---

### CS-10: 翻译键硬编码（多文件）

**交叉确认**: Reviewer D 🔴 + Reviewer C 💭
**文件**: 
- `item/custom/WaypointCompassItem.java` — `"目标坐标："`、`"维度："`
- `item/custom/PreciseWaypointCompassItem.java` — `"✦ 精准模式"`
- `client/SRClientScopeHandler.java` — `"距离：跨维度，无法计算"`、`"距离：约 X 格"`

**建议**: 全部替换为 `Component.translatable()` + `lang/*.json` 翻译键。

---

## 💭 Nits（本轮新发现）

### CN-01: 紫水晶纹饰每 tick 为每个负面效果新建 MobEffectInstance
**审查员**: Reviewer B
**文件**: `trim/effect/AmethystTrimEffect.java`
即使修复了 `update()` 语义问题，每 tick 为每个负面效果 `new MobEffectInstance` 仍存在分配压力。建议使用反射直接修改 duration。

### CN-02: TrimEffectEventHandler 事件分发重复创建 HashMap
**审查员**: Reviewer B
**文件**: `trim/TrimEffectEventHandler.java`
7 个事件处理方法都 `new HashMap<>()` 统计件数。对于只有 2-4 个元素的微型集合，HashMap 开销远高于线性扫描。

### CN-03: mergeNeighborNetworks 更新 networkId 时遍历整个合并后网络
**审查员**: Reviewer B
**文件**: `block_entity/CopperPipeBlockEntity.java`
应只遍历 `oldNetwork.getNodes()` 而非 `currentNetwork.getNodes()`。

### CN-04: CopperPipeEndpointBlockEntity 时序耦合
**审查员**: Reviewer C + Reviewer D
若端点所在区块先加载而相邻铜管后加载，端点可能永久孤立。应采用延迟注册或铜管加载后主动通知端点。

### CN-05: MeteorCrossbowItem 创造模式仍需背包有矛
**审查员**: Reviewer D
原版弩在创造模式下无需背包拥有箭矢即可装填。当前 `use()` 检查 `!findHeavySpear(player).isEmpty()`，创造模式玩家若背包无矛，弩完全无法使用。

### CN-06: ThrownHeavySpear 紫色涂装使用 moveTo 而非 teleportTo
**审查员**: Reviewer D
`moveTo` 直接修改服务端坐标，对 `ServerPlayer` 不会发送传送同步包，客户端可能数 tick 内看到错位。应使用 `teleportTo`。

---

## ✅ 第一轮问题修复状态

| ID | 原问题 | 状态 | 说明 |
|----|--------|------|------|
| B-01 | 紫水晶纹饰 `floor(reduction * 2) = 0` | ✅ **已修复** | 现改为 `* 20`，但发现新根因 CB-02 |
| B-02 | 下界合金纹饰 1 件即满 | ✅ **已修复** | 现改为 `linear(0.0, 0.25)` |
| B-03 | 活体陷阱静态 HashMap 重启丢失 | ⚠️ **仍存在** | 未修复，且因 CB-01 死锁更严重 |
| B-04 | 铜管网络静态 Map 未清理 | ⚠️ **仍存在** | 未修复，新增 CS-02 网络分裂问题 |
| S-01 | 钻石纹饰破坏多态设计 | ✅ **误判** | 代码实际上正确使用了多态，无 `instanceof` 检查 |
| S-02 | 回声碎片覆盖 silent | ⚠️ **仍存在** | 未修复，新增 CB-05 跨重启永久静音 |
| S-03 | 金纹饰缺少客户端检查 | ✅ **不存在** | `TrimEffectEventHandler.onPlayerTick` 已做 `!isClientSide` 拦截 |
| S-05 | 沉重之矛棕色涂装复制装备 | ✅ **已修复** | 现已有 `target.setItemSlot(chosenSlot, ItemStack.EMPTY)` |
| S-06 | 相邻铜管不合并网络 | ✅ **已修复** | 现已有 `findNeighborNetworkId()` + `mergeNeighborNetworks()` |
| S-07 | 四个属性类代码克隆 | ⚠️ **仍存在** | 未修复，确认问题严重 |
| S-08 | 制箭台输出不消耗输入 | ⚠️ **部分修复** | Shift+点击已修复，普通点击仍有 CB-03 漏洞 |
| S-09 | 追溯指针每 tick 写入 DataComponent | ⚠️ **仍存在** | 未修复 |
| S-10 | 铜管优先空槽而非合并 | ✅ **已修复** | 现已有两遍扫描策略 |
| N-01 ~ N-08 | 第一轮 Nit | — | 大部分仍存在，不再重复列出 |

---

## 📈 交叉置信度分析

| 问题 | A | B | C | D | 置信度 |
|------|---|---|---|---|--------|
| CB-01 LivingTrap 死锁 | 🔴 | — | — | 🔴 | **高** |
| CB-02 Amethyst update() 语义 | 🔴 | — | — | — | **中**（需验证 Mojang 源码）|
| CB-03 制箭台普通点击复制 | 🔴 | — | 🔴 | 🔴 | **极高** |
| CB-04 流星弩无耐久 | — | — | — | 🔴 | **中**（需与原版对比）|
| CB-05 回声碎片永久静音 | — | — | — | 🔴 | **中**（极端场景）|
| CB-06 深灰涂装跨重启静音 | — | — | — | 🔴 | **中**（极端场景）|
| CS-01 LivingTrap 重启丢失 | 🟡 | — | — | 🔴 | **高** |
| CS-02 铜管网络分裂 | — | — | 🟡 | 🟡 | **高** |
| CS-03 stillValid 永远 true | — | — | 🔴 | 🔴 | **极高** |
| CS-04 属性类代码克隆 | — | — | 🔴 | — | **高** |
| CS-05 铜管方块重复 | — | — | 🔴 | — | **高** |
| CS-06 lookupFromArmor 每 tick 重建 | — | 🔴 | — | — | **高** |
| CS-07 铜管 BFS 无缓存 | — | 🔴 | 🟡 | — | **高** |
| CS-08 追溯指针高频写入 | 🟡 | 🔴 | — | 🟡 | **高** |
| CS-09 transfer 文档不符 | — | — | 🟡 | — | **中** |
| CS-10 翻译键硬编码 | — | — | — | 🔴 | **高** |

---

## 📋 修复优先级（本轮）

| 优先级 | ID | 问题 | 修复建议 |
|--------|-----|------|----------|
| **P0** | CB-01 | LivingTrapBlock 死锁 | 释放实体后设 COOLDOWN=false，或改用 BlockEntity tick |
| **P0** | CB-02 | Amethyst update() 拒绝短 duration | 用反射修改 duration，或 remove+add 重建实例 |
| **P0** | CB-03 | 制箭台普通点击复制 | 输出槽改为 ResultSlot，或重写 onTake() |
| **P1** | CB-04 | 流星弩不消耗耐久 | 发射后调用 hurtAndBreak(1) |
| **P1** | CB-05 | 回声碎片跨重启永久静音 | onPlayerTick 兜底恢复 silent=false |
| **P1** | CB-06 | 深灰涂装跨重启静音 | 登录/tick 时检查 silent_until_tick |
| **P1** | CS-01 | LivingTrap 重启后实体状态泄漏 | 引入 LivingTrapBlockEntity + NBT 持久化 |
| **P1** | CS-02 | 铜管网络合并分裂 | 网络 ID 改为拓扑推导，或区块加载时校验 |
| **P1** | CS-03 | stillValid 缺失距离校验 | 传递 BlockPos，检查 distance <= 8 |
| **P2** | CS-04 ~ CS-07 | 代码重复 & 性能 | 提取基类、缓存 BFS、缓存 lookupFromArmor |
| **P2** | CS-08 | 追溯指针高频写入 | 变化检测 + 降频 |
| **P3** | CS-10 | 翻译键硬编码 | 统一替换为 translatable |
| **P3** | CN-01 ~ CN-06 | Nit 级别优化 | 按需处理 |

---

*AI 交叉审查完成。4 位审查员共发现 22 项新问题，其中 6 项 Blocker、10 项 Suggestion、6 项 Nit。与第一轮相比，本轮审查在功能正确性（Reviewer A）和 Minecraft 最佳实践（Reviewer D）角度发现了此前未识别的严重漏洞，特别是 LivingTrapBlock 状态机死锁、AmethystTrimEffect update() 语义错误、以及 FletchingTableMenu 普通点击复制漏洞。建议优先处理 P0 级问题。*
