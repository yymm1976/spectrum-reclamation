package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.block.BlazingLightBlock;
import com.spectrum_reclamation.spectrum_reclamation.block.LivingTrapBlock;
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
     * 活体陷阱方块 —— 可捕捉路过的小型生物。
     * - 低矮外观（类似感测体），碰撞箱高 2 像素
     * - 小型生物（宽度 ≤ 0.6）踩上后被吞入 5 秒
     * - 释放后施加 10 秒饥饿 I
     * - 冷却 15 秒后可再次触发
     * - 破坏后掉落自身
     */
    public static final DeferredHolder<Block, Block> LIVING_TRAP =
            BLOCKS.register(
                    "living_trap",
                    () -> new LivingTrapBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(2.0F)        // 硬度 2.0，木镐即可挖掘
                                    .requiresCorrectToolForDrops()  // 需要正确工具才能掉落
                                    .noOcclusion()         // 不遮挡相邻面
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
