package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.block.BlazingLightBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块（Block）注册类。
 * 使用 DeferredRegister 延迟注册所有自定义方块，
 * 确保在 NeoForge 注册阶段由引擎统一处理。
 */
public class SRBlocks {

    /** 方块的 DeferredRegister */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, SpectrumReclamation.MOD_ID);

    /**
     * 炽光灯方块 —— 炽光炸弹着弹时生成的临时光源。
     * - 不可见、无碰撞箱、亮度 15（最高）
     * - 30 秒（600 ticks）后自动销毁
     * - 可被替换（玩家可以在其位置放置其他方块）
     */
    public static final DeferredHolder<Block, Block> BLAZING_LIGHT =
            BLOCKS.register(
                    "blazing_light",
                    () -> new BlazingLightBlock(
                            BlockBehaviour.Properties.of()
                                    .noCollission()       // 无碰撞箱，实体可以穿过
                                    .noOcclusion()        // 不遮挡相邻面，避免渲染异常
                                    .lightLevel(state -> 15)  // 最高亮度
                                    .noLootTable()        // 无战利品表，销毁时不掉落
                                    .instabreak()         // 瞬间破坏
                                    .replaceable()        // 可被其他方块替换
                    )
            );

    /**
     * 将方块注册器绑定到模组事件总线（MOD_BUS）。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
