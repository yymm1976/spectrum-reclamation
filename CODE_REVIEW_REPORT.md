# 👁️ Spectrum Reclamation 深度代码审查报告

**项目**: Spectrum Reclamation (Minecraft 1.21.1 NeoForge Mod)
**审查范围**: `src/main/java` 下全部 57 个 Java 源文件 + 构建配置 + 资源文件
**审查日期**: 2026-06-08
**审查员**: Code Review Expert

---

## 📊 总体评价

| 维度 | 评分 | 说明 |
|------|------|------|
| **正确性** | ⭐⭐⭐☆☆ | 存在 2 个使功能完全失效的 Bug，1 个严重数值设计错误 |
| **安全性** | ⭐⭐⭐⭐☆ | 无注入风险，但存在状态竞争和物品凭空产生/消失的边界情况 |
| **可维护性** | ⭐⭐⭐⭐☆ | 注释极度详尽（几乎过度），但存在大量代码克隆 |
| **性能** | ⭐⭐⭐☆☆ | 每tick高开销操作、静态HashMap未清理、BFS未缓存 |
| **架构设计** | ⭐⭐⭐⭐☆ | 纹饰系统架构优秀，但 DiamondTrimEffect 破坏了多态设计 |

**亮点**：
- 🏆 注释质量极高，几乎每个方法都有完整的 Javadoc，包含设计理由、API 说明和注意事项
- 🏆 纹饰效果系统（TrimEffectHandler + TrimEffectRegistry）的插件化架构设计优秀
- 🏆 正确区分 MOD_BUS / GAME_BUS、服务端/客户端逻辑
- 🏆 Mixin 使用 @Inject(at=HEAD) 而非 @Overwrite，多模组兼容性良好

---

## 🔴 Blockers（必须修复）

### B-01: AmethystTrimEffect 完全不生效

**文件**: `trim/effect/AmethystTrimEffect.java`
**严重性**: 🔴 P0 — 功能完全失效

```java
int extraConsumption = (int) Math.floor(reduction * 2);
```

| 件数 | reduction | reduction × 2 | floor() | extraConsumption |
|------|-----------|---------------|---------|------------------|
| 1 | 0.10 | 0.20 | 0 | **0（无效）** |
| 2 | 0.20 | 0.40 | 0 | **0（无效）** |
| 3 | 0.30 | 0.60 | 0 | **0（无效）** |
| 4 | 0.40 | 0.80 | 0 | **0（无效）** |

**原因**: `reduction` 范围为 0.10~0.40，乘以 2 后最大仅 0.8，`Math.floor()` 恒为 0。紫水晶纹饰"负面效果时长 -10%/件"的效果在所有件数下都不生效。

**建议**: 将乘数从 2 改为 20（或更大的值），使 1 件时 `floor(0.10 * 20)` = 2 ticks/tick，产生可见效果。同时重新审视"每 tick 额外消耗"的机制是否与"时长缩减 10%"的描述语义一致。

---

### B-02: NetheriteTrimEffect 数值设计错误 — 1 件即达上限

**文件**: `trim/effect/NetheriteTrimEffect.java`
**严重性**: 🔴 P0 — 数值设计错误

```java
private static final TrimCountedValue KNOCKBACK_BONUS = TrimCountedValue.linear(0.0, 1.0);
// 每件 +1.0 击退抗性
```

**原因**: Minecraft 的 `Attributes.KNOCKBACK_RESISTANCE` 有效范围为 **0.0 ~ 1.0**。1 件 = 1.0 = 完全免疫击退，2~4 件无任何额外效果。

**建议**: 改为 `TrimCountedValue.linear(0.0, 0.25)`，使 4 件 = 1.0（完全免疫），1 件 = 0.25（25% 减免）。

---

### B-03: LivingTrapBlock 静态 HashMap 存在服务器重启后实体状态泄漏

**文件**: `block/LivingTrapBlock.java`
**严重性**: 🔴 P1 — 服务器重启后实体状态永久异常

```java
private static final Map<String, LivingEntity> TRAPPED_ENTITIES = new HashMap<>();
```

**问题链**:
1. 服务器运行中：实体被吞入陷阱，设为 `invisible=true, invulnerable=true, noGravity=true`
2. 服务器重启/崩溃：`TRAPPED_ENTITIES` 被清空（静态变量不持久化）
3. 世界重新加载：被吞入的实体重新加载，但 `TRAPPED_ENTITIES` 中没有记录
4. 方块的 scheduledTick 仍在执行，但 `TRAPPED_ENTITIES.get()` 返回 null → 不释放实体
5. 如果方块被破坏，`onRemove` 中的 `TRAPPED_ENTITIES.remove()` 也返回 null → 不恢复实体状态
6. **结果：实体永远处于不可见、无敌、无重力的异常状态**

**建议**:
- 将被吞入实体的状态写入方块实体的 NBT（使用 BlockEntity 而非静态 Map）
- 或在 `onLoad`/`onPlace` 时检查方块范围内是否有异常状态的实体并恢复
- 至少在 `onRemove` 中增加对附近不可见+无敌实体的扫描恢复逻辑

---

### B-04: 铜管网络静态 Map 在维度卸载/服务器停止时未清理

**文件**: `util/CopperPipeNetwork.java`
**严重性**: 🔴 P1 — 内存泄漏 + 跨世界数据污染

```java
private static final Map<UUID, CopperPipeNetwork> networks = new HashMap<>();
```

**问题**:
- 虽然 `clearAll()` 方法存在，但从未被调用
- 当维度卸载时（`UNLOAD_CHUNK` → `setRemoved` → `removeNode`），网络节点被逐个移除
- 但如果服务器正常停止而未触发 `WorldEvent.Unload`，网络数据残留
- 更危险的是：如果玩家在同一位置重建铜管，新 UUID 的网络被创建，但旧网络的入口/出口可能残留

**建议**: 在 `SREventHandler` 中监听 `ServerStoppingEvent` 或 `LevelEvent.Unload`，调用 `CopperPipeNetwork.clearAll()`。

---

## 🟡 Suggestions（应当修复）

### S-01: DiamondTrimEffect 破坏多态设计

**文件**: `trim/effect/DiamondTrimEffect.java` + `trim/TrimEffectEventHandler.java`

```java
// DiamondTrimEffect.java — onCriticalHit 为空，暴露类特有方法
default void onCriticalHit(LivingEntity attacker, LivingEntity target, int count) { /* 空 */ }
public double getCritDamageBonus(int count) { return CRIT_DAMAGE_BONUS.calc(count); }

// TrimEffectEventHandler.java — 必须做类型转换！
if (entry.getKey() instanceof DiamondTrimEffect diamondEffect) {
    double bonus = diamondEffect.getCritDamageBonus(entry.getValue());
```

**问题**: 事件处理器必须将 handler 强制转换为 `DiamondTrimEffect` 才能获取效果值，完全破坏了 `TrimEffectHandler` 接口的多态性。其他效果通过接口返回值传递数据，唯独钻石绕过了这个设计。

**建议**: 在 `TrimEffectHandler` 接口中添加 `default float onCriticalHit(LivingEntity, LivingEntity, int, float currentMultiplier)` 返回暴击乘数修正值，使 DiamondTrimEffect 通过统一的接口方法返回暴击加成。

---

### S-02: EchoShardTrimEffect 无条件覆盖 silent 状态

**文件**: `trim/effect/EchoShardTrimEffect.java`

```java
entity.setSilent(count >= FULL_SET_COUNT); // 每 tick 无条件设置
```

**问题**: 如果其他模组或游戏机制将实体设为 `silent=true`，当纹饰件数 < 4 时，此代码会强制设为 `false`，覆盖其他来源的静音效果。

**建议**: 改为仅在 `count >= 4` 时设为 true，不主动设为 false：
```java
if (count >= FULL_SET_COUNT) {
    entity.setSilent(true);
    // 在 onEquipmentChange 中检测件数减少时恢复
}
```
同时增加一个标记机制（如自定义 NBT 或 EntityTag），区分本模组设置的 silent 和其他来源的 silent。

---

### S-03: GoldTrimEffect 缺少客户端侧检查 + 可能覆盖其他吸收效果

**文件**: `trim/effect/GoldTrimEffect.java`

**问题 A**: `onTick` 方法没有检查 `entity.level().isClientSide()`，可能在客户端侧也执行了效果添加。其他 tick 型效果（AmethystTrimEffect、EchoShardTrimEffect）都有此检查。

**问题 B**: 每 tick 刷新 40 tick 的吸收效果会"锁定"效果等级。如果玩家通过金苹果获得 Absorption IV（更高等级），纹饰的每 tick 刷新会用 Absorption I 覆盖掉金苹果的效果。

**建议**: 在添加效果前检查已有效果等级，仅在当前等级 < 纹饰等级时才刷新。

---

### S-04: SREventHandler 中 PLAYERS_WITH_SCOPED_SHOT 的 UUID 泄漏

**文件**: `event/SREventHandler.java`

```java
private static final Set<UUID> PLAYERS_WITH_SCOPED_SHOT = new HashSet<>();
```

**问题**: 如果 `ArrowLooseEvent` 触发但 `EntityJoinLevelEvent` 未触发（例如箭矢被其他事件取消），UUID 会永远残留在 Set 中，虽然不会造成功能错误（`remove` 操作不会匹配到任何实体），但属于状态泄漏。

**建议**: 在 `EntityJoinLevelEvent` 的处理中，如果遍历后仍有未消费的 UUID，可以在下一个 tick 或定时清理。或使用 `WeakReference` + 玩家退出时清理。

---

### S-05: ThrownHeavySpear 棕色涂装 — 装备脱落但未从实体身上移除

**文件**: `entity/ThrownHeavySpear.java`

```java
private void applyBrownEffect(LivingEntity target) {
    // ...
    equippedItems.add(equipment.copy());  // copy 了装备
    // ...
    ItemEntity itemEntity = new ItemEntity(this.level(),
            target.getX(), target.getY() + 0.5, target.getZ(), dropped);
```

**问题**: 代码在目标位置生成了掉落物，但**从未从实体身上移除该装备**（没有 `target.setItemSlot(slot, ItemStack.EMPTY)`）。结果是：装备被复制了一份掉落，原装备仍在实体身上——物品凭空产生。

**建议**: 在生成掉落物后，调用 `target.setItemSlot(对应slot, ItemStack.EMPTY)` 移除原装备。需要记录随机选中的槽位，而非记录物品副本。

---

### S-06: 铜管网络 — 两个相邻铜管会产生两个独立网络

**文件**: `block_entity/CopperPipeBlockEntity.java`

当两个铜管相邻放置时：
1. 放置铜管 A → `onLoad` → 创建网络 UUID-1，A 加入网络
2. 放置铜管 B（与 A 相邻）→ `onLoad` → 创建网络 UUID-2，B 加入新网络
3. A 的邻居中有 B，B 的邻居中有 A，但它们属于不同的网络

**结果**: 两个物理上连通的铜管属于两个不同的网络，物品传输可能无法找到出口。

**建议**: 在 `onLoad` 注册到网络前，先扫描相邻铜管是否已有网络。如果有，将自身加入相邻铜管的网络（而非创建新网络），并合并网络。这是管道系统中最常见的"网络合并"问题。

---

### S-07: 四个属性修饰器类存在大量代码克隆

**文件**: `IronTrimEffect.java`, `NetheriteTrimEffect.java`, `RedstoneTrimEffect.java`, `TurtleTrimEffect.java`

这四个类的 `updateModifiers()` 方法逻辑几乎完全相同（移除旧修饰器 → 重新计数 → 均分添加新修饰器），仅属性类型、材料 ID 和数值不同。

**建议**: 提取 `AbstractAttributeTrimEffect` 基类，封装通用的修饰器管理逻辑，子类只需提供属性引用、材料 ID 和数值常量。

---

### S-08: FletchingTableMenu 配方输出不消耗输入材料

**文件**: `inventory/FletchingTableMenu.java`

`quickMoveStack()` 处理输出槽时：
```java
if (slotIndex == OUTPUT_SLOT) {
    if (!this.moveItemStackTo(slotItem, SLOT_COUNT, this.slots.size(), true)) {
        return ItemStack.EMPTY;
    }
    slot.onQuickCraft(slotItem, result);
```

**问题**: 当玩家从输出槽取走物品时，`quickMoveStack` 将输出物品移入背包，但**没有消耗输入槽的材料**。`slotsChanged` → `updateOutput` 仅在输入变化时更新输出，不处理消耗逻辑。原版工作台（CraftingMenu）通过 `craftRemaining` 和 `recipeUsed` 机制处理消耗，此处未实现。

**建议**: 在取走输出物品时，减少输入槽物品的数量（每个槽位减少 1 个）。

---

### S-09: WaypointCompassItem.inventoryTick 每 tick 都操作 DataComponents

**文件**: `item/custom/WaypointCompassItem.java`

```java
@Override
public void inventoryTick(ItemStack stack, Level level, Entity entity, int itemSlot, boolean isSelected) {
    // 每 tick 设置 LODESTONE_TRACKER
    stack.set(DataComponents.LODESTONE_TRACKER, tracker);
```

**问题**: 每 tick（每秒 20 次）都调用 `stack.set()`，即使数据没有变化。`ItemStack.set()` 会触发数据组件的脏标记，可能导致不必要的网络同步包。

**建议**: 在设置前先检查当前值是否需要更新，仅在数据变化时才调用 `set()`。

---

### S-10: CopperPipeTickHandler.insertIntoContainer 优先放空槽位

**文件**: `event/CopperPipeTickHandler.java`

```java
private ItemStack insertIntoContainer(Container container, ItemStack itemStack) {
    // ...
    if (slotStack.isEmpty()) {
        container.setItem(i, remaining);  // 空槽位直接放入
        remaining = ItemStack.EMPTY;
        break;
    }
```

**问题**: 遍历顺序是先检查空槽位再检查合并。但 Minecraft 容器的标准行为是**优先合并到已有同类物品的槽位**，其次才放入空槽位。当前实现可能导致：一个 32 个苹果的槽位旁边有空槽位，放入 16 个苹果时被放入空槽位（变为两个 32+16 的堆），而非合并为 48+0。

**建议**: 采用两遍扫描策略——第一遍寻找可合并的同类槽位，第二遍寻找空槽位。

---

## 💭 Nits（改进建议）

### N-01: ThrownHeavySpear 飞行方向代码重复

`onHitEntity` 和 `doKnockback` 中有完全相同的"防止零向量 normalize"逻辑（约 10 行），出现两次。

**建议**: 提取为 `getHorizontalFlightDirection()` 私有方法。

### N-02: CopperPipeBlock 和 CopperPipeEndpointBlock 的 PROPERTY_BY_DIRECTION 完全相同

两个类各有一个完全一样的方向-属性映射和 `getDirectionFromPos` 方法。

**建议**: 提取到共享工具类或基类中。

### N-03: TrimCountedValue.calc() 缺少 count 参数校验

传入负数 `count` 会产生非预期结果。虽然调用方通常保证 0-4，但防御性编程缺失。

### N-04: LapisTrimEffect 经验加成在低经验值时失效

`Math.floor(amount * 0.08)` 当 amount ≤ 12 时，额外经验为 0。建议使用 `Math.max(1, ...)` 保底或使用 `Math.round()` 替代 `Math.floor()`。

### N-05: gradle.properties 中 JVM 堆内存偏小

`org.gradle.jvmargs=-Xmx1G` 对 NeoForge 模组开发来说偏小，编译和运行游戏时可能 OOM。建议改为 `-Xmx3G` 或 `-Xmx4G`。

### N-06: SRTrimMaterials 中三个自定义材料都使用 IRON_INGOT 作为锻造材料

```java
Items.IRON_INGOT, // 锻造材料：暂用铁锭
```

注释标记了"暂用"，但功能上玩家可以用铁锭锻造三种不同的纹饰，可能不是预期行为。

### N-07: BlazingBombEntity 不处理玩家自伤

炽光炸弹对半径 8 格内所有 LivingEntity 施加发光效果，**包括投掷者自己**。如果设计意图是只影响敌人，需要排除投掷者。

### N-08: PreciseWaypointCompassItem 使用 ChatFormatting 硬编码

```java
tooltip.add(Component.literal(ChatFormatting.AQUA + "✦ 精准模式"));
```

硬编码中文字符串 + ChatFormatting 直接拼接。应使用翻译键 + 样式系统，保持国际化一致性。

---

## 📈 架构级观察

### 纹饰效果系统 — 设计优秀但有裂缝

```
TrimEffectHandler (接口)
  ├── QuartzTrimEffect     ✅ 通过 onDealDamage() 返回值
  ├── IronTrimEffect       ✅ 通过属性修饰器
  ├── DiamondTrimEffect    ❌ 需要类型转换，破坏多态
  ├── EmeraldTrimEffect    ❌ 完全空实现
  └── ...
```

**核心问题**: `TrimEffectHandler` 的方法签名无法覆盖所有效果类型（暴击乘数、交易折扣等），导致 DiamondTrimEffect 绕过接口。

**建议**: 为 `onCriticalHit` 增加返回值（暴击乘数修正），为交易折扣预留 `onTradeOffer()` 方法，保持统一的多态分发机制。

### 铜管网络 — 单 UUID 模型的局限性

当前每个铜管在放置时生成独立 UUID，但相邻铜管不会自动合并网络。这意味着：
- 单人依次放置时，每个铜管是独立网络
- 物品传输（BFS 查找出口）可能无法跨越物理连通但逻辑独立的网络

**根本解决方案**: 改用"发现邻居时加入邻居的网络"策略，或实现网络合并算法。

---

## 📋 修复优先级排序

| 优先级 | ID | 问题 | 影响范围 |
|--------|-----|------|----------|
| **P0** | B-01 | 紫水晶纹饰完全不生效 | 1 种纹饰 × 所有件数 |
| **P0** | B-02 | 下界合金纹饰 1 件即满 | 1 种纹饰 × 2-4 件 |
| **P1** | B-03 | 活体陷阱重启后实体状态泄漏 | 服务器重启场景 |
| **P1** | B-04 | 铜管网络静态 Map 内存泄漏 | 所有铜管用户 |
| **P1** | S-05 | 棕色涂装复制装备而非脱落 | 1 种涂装 |
| **P1** | S-06 | 相邻铜管不合并网络 | 铜管系统核心 |
| **P1** | S-08 | 制箭台输出不消耗输入 | 制箭台功能 |
| **P2** | S-01 | 钻石纹饰破坏多态设计 | 纹饰系统架构 |
| **P2** | S-02 | 回声碎片覆盖 silent 状态 | 多模组兼容性 |
| **P2** | S-03 | 金纹饰缺少客户端检查 | 运行时错误风险 |
| **P2** | S-07 | 四个属性类代码克隆 | 可维护性 |
| **P3** | N-* | 所有 Nit 级别建议 | 代码质量 |

---

*审查完成。这份报告旨在帮助提升代码质量，而非批评。项目的注释质量、架构设计和 NeoForge API 的正确使用都非常出色——修复上述问题后，这将是一个非常健壮的模组。*
