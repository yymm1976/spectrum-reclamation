package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.BlazingBombItem;
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
     * 将物品注册器绑定到模组事件总线（MOD_BUS）。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
