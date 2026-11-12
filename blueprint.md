# 《项目共识与架构蓝图》V1.1

---

## 一、模组身份

| 属性 | 值 |
|---|---|
| 工作标题 | **Spectrum Reclamation** |
| 中文名 | **光谱救赎** |
| Mod ID | `spectrum_reclamation` |
| Minecraft 版本 | **1.21.1** |
| 模组加载器 | **NeoForge** |
| 前置依赖 | Spectrum（`spectrum`）≥ 1.11.8 |
| 代码语言 | Java 21 |
| 构建工具 | Gradle + `net.neoforged.moddev` 插件 |
| 许可证 | 待定 |

---

## 二、模组功能范围

### ✅ 做什么

1. **卸负药水**：自定义负面状态效果 + Spectrum 药水工坊配方
2. **陨星弩与沉重之矛**：自定义重型弩 + 矛弹射物 + 钉穿机制 + 墨水涂装升级系统
3. **原版鸡肋救赎**：炽光炸弹、活体陷阱、制箭台 GUI、铜管传输、通用瞄准镜、可自定义追溯指针
4. **盔甲纹饰效果系统**：原版 12 种 + Spectrum 6 种纹饰材料提供叠加增益，可扩展架构预留外部注册 API

### ❌ 不做什么

- 不修改 Spectrum 本体任何文件（纯附属模组）
- 不添加新维度、生物群系、大型结构
- 不做 GUI 配置界面（V1：配置文件）
- 不做火花石、月长石的纹饰效果（效果待定，V2）
- 不做动态纹饰材料生成（不复制 AllTheTrims 的"任意物品做纹饰"机制）

---

## 三、技术架构

### 3.1 文件结构

```
src/main/java/com/<author>/spectrum_reclamation/
├── SpectrumReclamation.java              // @Mod 主入口
├── SpectrumReclamationClient.java        // 客户端入口
│
├── registry/
│   ├── SRMobEffects.java                // 卸负状态效果
│   ├── SRItems.java                     // 所有自定义物品
│   ├── SRBlocks.java                    // 铜管、活体陷阱方块
│   ├── SRBlockEntities.java             // 铜管、制箭台方块实体
│   ├── SRCreativeModeTabs.java          // 创造模式物品栏
│   ├── SREntities.java                  // 矛弹射物实体
│   ├── SRTrimMaterials.java             // 自定义纹饰材料注册
│   ├── SRParticleTypes.java             // 粒子效果
│   ├── SRSoundEvents.java               // 音效
│   └── SRMenuTypes.java                 // 制箭台 GUI 容器类型
│
├── mob_effect/
│   └── UnburdenMobEffect.java           // 卸负：盔甲脱落 + 死亡全掉落
│
├── trim/
│   ├── TrimEffectRegistry.java          // 可扩展效果注册表（公开 API）
│   ├── TrimEffectHandler.java           // 事件处理器：检测纹饰 → 注入效果
│   └── TrimCountedValue.java            // 叠加计算：linear(base, perPiece)
│
├── item/
│   ├── custom/
│   │   ├── HeavySpearItem.java          // 沉重之矛（投掷物释放）
│   │   ├── BlazingBombItem.java         // 炽光炸弹
│   │   ├── LivingTrapItem.java          // 活体陷阱放置器
│   │   ├── ScopeAttachmentItem.java     // 通用瞄准镜
│   │   └── WaypointCompassItem.java     // 可自定义追溯指针
│   └── UnburdenPotionItem.java          // 卸负药水物品（如有特殊逻辑）
│
├── block/
│   ├── CopperPipeBlock.java             // 铜管（网络节点）
│   ├── CopperPipeEndpointBlock.java     // 铜管接口（连接容器）
│   ├── LivingTrapBlock.java             // 活体陷阱
│   └── FletchingTableOverride.java      // 制箭台交互覆盖
│
├── block_entity/
│   ├── CopperPipeBlockEntity.java       // 铜管网络 + 路径查找
│   └── FletchingTableBlockEntity.java   // 制箭台库存 + 配方逻辑
│
├── entity/
│   └── ThrownHeavySpear.java            // 矛弹射物：重力 + 钉穿判定
│
├── screen/
│   └── FletchingTableScreen.java        // 制箭台 GUI 渲染
│
├── inventory/
│   └── FletchingTableMenu.java          // 制箭台容器逻辑
│
├── networking/
│   └── SRPackets.java                   // 网络包（制箭台同步）
│
├── compat/
│   └── (预留：未来联动材料的纹饰效果处理器)
│
└── mixin/
    └── FletchingTableMixin.java         // 覆盖原版制箭台 useWithoutItem
```

```
src/main/resources/
├── assets/spectrum_reclamation/
│   ├── textures/
│   │   ├── item/                        // 物品贴图
│   │   ├── block/                       // 方块贴图
│   │   ├── entity/                      // 弹射物贴图
│   │   ├── gui/                         // 制箭台 GUI
│   │   └── trims/                       // ⚠️ 纹饰颜色调色板纹理
│   │       └── palettes/
│   │           ├── onyx_powder.png      // 8 色调色板
│   │           ├── midnight_chip.png
│   │           ├── quitoxic_powder.png
│   │           ├── moonstone_powder.png
│   │           └── sparkstone.png       // (V2)
│   ├── sounds/
│   ├── particles/
│   ├── lang/
│   │   └── zh_cn.json                   // 中文优先
│   └── atlases/
│       └── armor_trims.json             // ⚠️ 关键文件：纹饰调色板图集
│
├── data/spectrum_reclamation/
│   ├── recipe/
│   │   ├── potion_workshop_brewing/     // 卸负药水配方
│   │   ├── potion_workshop_crafting/    // 药水工坊物品配方
│   │   ├── pedestal/                    // 七彩基座配方
│   │   └── fusion_shrine/              // 融合圣坛配方
│   └── advancement/
│
└── META-INF/
    └── neoforge.mods.toml
```

### 3.2 依赖关系

```
spectrum_reclamation
    │
    ├── spectrum (required, ≥1.11.8)
    │   ├── revelationary
    │   ├── modonomicon
    │   └── cloth-config
    │
    └── neoforge (1.21.1, via moddev plugin)
```

### 3.3 数据流

```
盔甲纹饰效果触发链路：

  LivingTickEvent 触发
        │
        ▼
  TrimEffectHandler.onLivingTick(player)
        │
        ▼
  遍历 4 个盔甲槽位:
    读取 DataComponents.TRIM
        │
        ▼
  TrimEffectRegistry.lookup(trimMaterial)
    返回 List<EffectHandler>
        │
        ▼
  累加匹配数量 (1-4 件)
        │
        ▼
  TrimCountedValue.linear(base, perPiece).calc(count)
        │
        ▼
  注入属性修饰器 / 注册事件监听器
```

---

## 四、关键技术决策

### 决策 1：纹饰效果架构（参考 BetterTrims 但不复制）

BetterTrims 的架构（动态注册表 + CountBasedValue + 能力类型 Mixin）很优雅，但过度依赖 Mixin（每个能力类型一个 `@ModifyVariable`），侵入性强。

**我们的方案**：
- **事件驱动**：使用 `LivingTickEvent` + `LivingHurtEvent` + `LivingExperienceDropEvent` 等 NeoForge 事件——这是比 Mixin 更干净的扩展点
- **属性修饰器**：持续性效果（移速、护甲值、游泳速度等）通过 `addAttributeModifier` 直接附加到盔甲物品上
- **线性叠加**：`TrimCountedValue.linear(base, perPiece).calc(count)` ——简单、清晰、不引入复杂的公式系统
- **注册表**：`TrimEffectRegistry` 是公开 Java API + 预留 JSON 数据驱动接口（V2）

### 决策 2：自定义纹饰材料的颜色渲染

这是**最大的技术风险**。NeoForge 1.21.1 的 `armor_trims.json` 要求在 `paletted_permutations` 中为每个材料 × 每个图案显式列出调色板。

**我们的方案**：
- 创建 `assets/spectrum_reclamation/textures/trims/palettes/<material>.png`：每个材料一张 1×8 像素的 PNG（8 色调色板）
- 在 `atlases/armor_trims.json` 中使用 `paletted_permutations`：
  - `palette_key` 指向我们的调色板纹理
  - `permutations` 列出每个原版纹饰图案 → 我们材料的映射
  - 为 Spectrum 可能有的自定义纹饰图案预留位置（通过 `optional` 标记或运行时检测）
- **Phase 0 必须验证**：放置一个带自定义纹饰的盔甲，确认不显示紫色/黑色棋盘格

### 决策 3：制箭台 Mixin

仅 Mixin `FletchingTableBlock.useWithoutItem()` 一个方法，打开我们的 GUI。不对方块实体、方块状态、村民职业做任何修改。失败的话，退路是新建一个独立方块。

### 决策 4：铜管传输网络

- 每个铜管方块在放置/破坏时检测相邻铜管，更新网络图的邻接表
- 网络使用 BFS 从入口（铜管接口 + 容器）查找最近出口（铜管接口 + 容器）
- 物品瞬间传送（这是"铜管"的核心卖点 —— 不是漏斗的复制品）
- 路径缓存直到网络拓扑变化

### 决策 5：矛弹射物

- 继承 `AbstractArrow` 获得碰撞检测、伤害计算框架
- 覆盖重力系数为 1.5×（弹道更陡）
- 钉穿判定：击中实体时，检测实体位置背后 2 格内是否有实体方块 → 如有，实体获得 1.5 秒禁锢（设置 motion = 0）
- 矛落地后以 `ItemEntity` 形式存在，可捡回

### 决策 6：卸负盔甲脱落

- 继承 `MobEffect` + 实现 `applyEffectTick`
- 每 5 秒：每个盔甲槽位独立概率判定
- 受击时（监听 `LivingHurtEvent`）：概率判定
- 盔甲脱落实现：`livingEntity.setItemSlot(slot, ItemStack.EMPTY)` + 在生物位置生成 `ItemEntity`
- 死亡 100% 掉落：监听 `LivingDropsEvent`，如果生物有卸负效果，将其所有盔甲槽物品强制加入掉落列表

---

## 五、纹饰材料效果完整表（定稿）

### 5.1 效果编码规则

- 所有效果采用 **「每件 +X」线性叠加**，4 件 = 4 倍
- 不使用概率触发型效果（除铜的耐久免消耗因其性质特殊可保留概率，但不按件数叠加概率——改为每件独立判定）
- 奇毒粉末叠件**提高中毒等级**而非延长时间

### 5.2 原版纹饰材料

| # | 材料 | 颜色 | 效果（每件） | 叠满（4件） | 实现方式 |
|---|---|---|---|---|---|
| 1 | 石英 | 白 | 近战攻击伤害 +2% | +8% | `LivingHurtEvent` |
| 2 | 铁锭 | 灰 | +0.5 盔甲值 | +2 | 属性修饰器 |
| 3 | 金锭 | 黄 | +1 伤害吸收等级；猪灵中立 | +4 黄心 | 属性修饰器 + 猪灵 AI 事件 |
| 4 | 钻石 | 青 | 暴击伤害 +5% | +20% | `CriticalHitEvent` |
| 5 | 下界合金 | 黑 | +1 击退抗性；免疫火焰销毁物品 | +4 抗性 | 属性修饰器 + `LivingDropsEvent` |
| 6 | 绿宝石 | 绿 | 村民交易价格 -5% | -20% | 村民交易事件 |
| 7 | 红石粉 | 红 | 移动速度 +3% | +12% | 属性修饰器 |
| 8 | 青金石 | 蓝 | 击杀经验 +8% | +32% | `LivingExperienceDropEvent` |
| 9 | 紫水晶 | 紫 | 负面效果时长 -10% | -40% | `MobEffectEvent.Applicable` |
| 10 | 海龟鳞甲 | 浅绿 | 游泳速度 +15% | +60% | 属性修饰器 |
| 11 | 蜜脾 | 橙 | 摔落有效高度 -1 格 | -4 格 | `LivingFallEvent` |
| 12 | 回响碎片 | 深青 | 幽匿感测体检测范围 -20%；潜行完全无声 | -80%；无声 | 感测体 AI 事件 + 潜行检测 |
| 13 | 铜锭 | 棕橙 | 工具耐久消耗 8% 概率免消耗 | 每件独立判定 | `LivingEquipmentChangeEvent` — 监听耐久变化 |

### 5.3 Spectrum 纹饰材料

| # | 材料 | 颜色 | 效果（每件） | 叠满（4件） | 实现方式 |
|---|---|---|---|---|---|
| 14 | 缟玛瑙粉末 | 黑紫 | 对满血目标首击 +8% 伤害 | +32% | `LivingHurtEvent` |
| 15 | 午夜碎片 | 深紫 | 攻击无视目标 6% 护甲 | 无视 24% | `LivingHurtEvent` |
| 16 | 奇毒粉末 | 毒绿 | 攻击者近战攻击你 → 中毒；每件 +1 中毒等级 | 中毒 IV | `LivingHurtEvent` |
| 17 | ~~月长石粉末~~ | — | **V2 待定** | — | — |
| 18 | ~~火花石~~ | — | **V2 待定** | — | — |

### 5.4 相关说明

- `TrimEffectRegistry` 对外暴露 `register(ResourceLocation materialId, TrimEffectHandler handler)` 方法，未来联动材料通过此 API 注册
- 所有增益效果的属性修饰器命名空间统一为 `spectrum_reclamation:trim.<material_name>`
- 纹饰材料的颜色调色板（用于盔甲上的视觉渲染）在 `textures/trims/palettes/` 中定义，每张 1×8 像素 PNG

---

## 六、Phase 总览

| Phase | 标题 | 类型 | 复杂度 | 依赖 |
|---|---|---|---|---|
| **Phase 0** | 项目骨架 + 纹饰渲染验证 | 基础设施 | 中 | — |
| **Phase 1** | 卸负药水 | 核心功能 | 中 | P0 |
| **Phase 2** | 炽光炸弹 + 活体陷阱 | 原版救赎 | 低 | P0 |
| **Phase 3** | 制箭台 GUI | 原版救赎 | 中高 | P0 |
| **Phase 4** | 铜管传输系统 | 原版救赎 | 高 | P0 |
| **Phase 5** | 通用瞄准镜 + 追溯指针升级 | 原版救赎 | 低 | P0 |
| **Phase 6** | 纹饰效果系统 — 框架 + 原版材料 | 新系统 | 高 | P0 |
| **Phase 7** | 纹饰效果系统 — Spectrum 材料 | 新系统 | 中 | P6 |
| **Phase 8** | 陨星弩 + 沉重之矛（基础版） | 核心功能 | 高 | P0 |
| **Phase 9** | 墨水涂装升级系统 | 核心功能 | 中 | P8 |
| **Phase 10** | 配方、进度、本地化 | 打磨 | 中 | P1-P9 |

---

## 七、变更记录

- **V1.0**：初始蓝图
- **V1.1**：基于 BetterTrims / AllTheTrims 源码调研更新：
  - 细化纹饰效果触发架构（事件驱动 + 属性修饰器，替代 Mixin 重型方案）
  - 新增 `TrimCountedValue` 线性叠加模型
  - 新增纹饰颜色渲染技术方案（`armor_trims.json` + 调色板 PNG）
  - 更新文件结构（新增 `trim/` 包）
  - Phase 0 增加纹饰颜色渲染验证任务
  - 不采用 BetterTrims 的数据驱动注册表（V1 用代码注册），不采用 AllTheTrims 的动态纹饰生成
