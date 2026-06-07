package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.BlazingBombItem;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.LivingTrapItem;
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
     * 铜管物品 —— 可放置的铜管方块物品。
     * - 最大堆叠 64
     * - 右键放置铜管方块（CopperPipeBlock）
     * - 放置后自动与相邻铜管/铜管接口建立连接
     * - 每个铜管方块关联一个 CopperPipeBlockEntity，持有网络 UUID
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
     * - 最大堆叠 64
     * - 右键放置铜管接口方块（CopperPipeEndpointBlock）
     * - 放置时自动朝向容器方向
     * - 右键切换入口/出口模式
     * - 入口模式：从朝向的容器提取物品，传入铜管网络
     * - 出口模式：从铜管网络接收物品，存入朝向的容器
     */
    public static final DeferredHolder<Item, Item> COPPER_PIPE_ENDPOINT =
            ITEMS.register(
                    "copper_pipe_endpoint",
                    () -> new BlockItem(
                            SRBlocks.COPPER_PIPE_ENDPOINT.get(),
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
