package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.BlazingBombItem;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.LivingTrapItem;
import net.minecraft.core.registries.Registries;
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
     * - 最大堆叠 64
     * - 右键放置活体陷阱方块
     * - 绑定 SRBlocks.LIVING_TRAP 方块
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
     * 将物品注册器绑定到模组事件总线（MOD_BUS）。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
