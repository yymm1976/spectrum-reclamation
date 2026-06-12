package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.BlazingBombItem;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.HeavySpearItem;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.LivingTrapItem;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.MeteorCrossbowItem;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.PreciseWaypointCompassItem;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.ScopeAttachmentItem;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.WaypointCompassItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品（Item）注册类。
 * 使用 DeferredRegister 延迟注册所有自定义物品，
 * 确保在 NeoForge 注册阶段由引擎统一处理。
 */
public class SRItems {

    /** 物品的 DeferredRegister */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SpectrumReclamation.MOD_ID);

    /**
     * 炽光炸弹物品。
     * - 最大堆叠 16
     * - 右键使用时发射炽光炸弹弹射物
     * - 着弹时在范围内施加发光效果，并对亡灵生物附加燃烧
     */
    public static final DeferredHolder<Item, Item> BLAZING_BOMB =
            ITEMS.register(
                    "blazing_bomb",
                    () -> new BlazingBombItem(new Item.Properties().stacksTo(16))
            );

    /**
     * 活体陷阱物品 —— 可放置于地面的活体陷阱方块。
     */
    public static final DeferredHolder<Item, Item> LIVING_TRAP =
            ITEMS.register(
                    "living_trap",
                    () -> new LivingTrapItem(
                            (com.spectrum_reclamation.spectrum_reclamation.block.LivingTrapBlock) SRBlocks.LIVING_TRAP.get(),
                            new Item.Properties().stacksTo(64)
                    )
            );

    /**
     * 铜管物品 —— 可放置的铜管方块物品。
     */
    public static final DeferredHolder<Item, Item> COPPER_PIPE =
            ITEMS.register(
                    "copper_pipe",
                    () -> new BlockItem(
                            SRBlocks.COPPER_PIPE.get(),
                            new Item.Properties().stacksTo(64)
                    )
            );

    /**
     * 铜管接口物品 —— 可放置的铜管接口方块物品。
     */
    public static final DeferredHolder<Item, Item> COPPER_PIPE_ENDPOINT =
            ITEMS.register(
                    "copper_pipe_endpoint",
                    () -> new BlockItem(
                            SRBlocks.COPPER_PIPE_ENDPOINT.get(),
                            new Item.Properties().stacksTo(64)
                    )
            );

    // ==================== 新增物品 ====================

    /**
     * 瞄准镜物品 —— 可附加到弓/弩上的瞄准镜附件。
     * - 最大堆叠 1（附件类物品不可堆叠）
     * - 副手持瞄准镜 + 主手弓/弩右键 → 附着
     * - 附着后：拉弓时 FOV 缩小（放大效果），箭矢无重力（弹道更直）
     * - 合成配方：望远镜 + 铜锭 + 紫水晶碎片 → 瞄准镜
     */
    public static final DeferredHolder<Item, Item> SCOPE_ATTACHMENT =
            ITEMS.register(
                    "scope_attachment",
                    () -> new ScopeAttachmentItem(new Item.Properties().stacksTo(1))
            );

    /**
     * 追溯指针物品 —— 可自定义坐标指向的指南针。
     * - 最大堆叠 1
     * - 蹲下 + 右键：记录当前位置和维度
     * - 指针指向记录的坐标（同维度时），跨维度时不工作
     * - 悬停提示显示目标坐标和维度
     */
    public static final DeferredHolder<Item, Item> WAYPOINT_COMPASS =
            ITEMS.register(
                    "waypoint_compass",
                    () -> new WaypointCompassItem(new Item.Properties().stacksTo(1))
            );

    /**
     * 精准追溯指针物品 —— 追溯指针的升级版本。
     * - 最大堆叠 1
     * - 继承追溯指针所有功能
     * - 额外显示到目标的距离（"约 N 格"）
     * - 合成配方：追溯指针 + 回响碎片 → 精准追溯指针（锻造台）
     */
    public static final DeferredHolder<Item, Item> PRECISE_WAYPOINT_COMPASS =
            ITEMS.register(
                    "precise_waypoint_compass",
                    () -> new PreciseWaypointCompassItem(new Item.Properties().stacksTo(1))
            );

    // ==================== 陨星弩与沉重之矛 ====================

    /**
     * 沉重之矛物品 —— 陨星弩的专用弹药。
     * - 最大堆叠 1（弹药类物品不可堆叠）
     * - 不是可投掷物品，仅作为弹药被陨星弩消耗
     * - 右键无效果
     */
    public static final DeferredHolder<Item, Item> HEAVY_SPEAR =
            ITEMS.register(
                    "heavy_spear",
                    () -> new HeavySpearItem(new Item.Properties().stacksTo(1))
            );

    /**
     * 陨星弩物品 —— 继承 CrossbowItem 的重型弩。
     * - 最大堆叠 1（武器类物品不可堆叠）
     * - 耐久值沿用原版弩 465，确保每次发射会正常损耗
     * - 仅接受沉重之矛作为弹药
     * - 装填速度比普通弩慢 2 倍（50 ticks vs 25 ticks）
     * - 自带瞄准镜效果（FOV 缩放）
     * - 发射 ThrownHeavySpear 实体（高伤害、强击退、钉穿）
     */
    public static final DeferredHolder<Item, Item> METEOR_CROSSBOW =
            ITEMS.register(
                    "meteor_crossbow",
                    () -> new MeteorCrossbowItem(new Item.Properties().stacksTo(1).durability(465))
            );

    /**
     * 将物品注册器绑定到模组事件总线（MOD_BUS）。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
