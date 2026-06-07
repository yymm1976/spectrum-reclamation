package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.block_entity.CopperPipeBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块实体类型（BlockEntityType）注册类。
 *
 * 使用 DeferredRegister 延迟注册所有自定义方块实体类型，
 * 确保在 NeoForge 注册阶段由引擎统一处理。
 *
 * BlockEntityType 是 NeoForge 用来"告诉"引擎：
 * "这个方块实体类对应哪些方块"的注册项。
 * 当引擎加载一个方块时，如果该方块实现了 EntityBlock 接口，
 * 引擎会查找对应的 BlockEntityType 来创建方块实体实例。
 */
public class SRBlockEntities {

    /** 方块实体类型的 DeferredRegister */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SpectrumReclamation.MOD_ID);

    /**
     * 铜管方块实体类型。
     *
     * BlockEntityType.Builder.of(构造器引用, 方块...)
     * - 第一个参数：方块实体的构造器引用，接收 BlockPos 和 BlockState
     * - 后续参数：此方块实体类型可以出现在哪些方块上（可变参数）
     *
     * build(null) 中的 null 表示不指定额外的 ResourceLocation，
     * NeoForge 的 DeferredRegister 会自动使用注册名（"copper_pipe"）。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CopperPipeBlockEntity>> COPPER_PIPE =
            BLOCK_ENTITY_TYPES.register(
                    "copper_pipe",
                    () -> BlockEntityType.Builder.of(
                            CopperPipeBlockEntity::new,
                            SRBlocks.COPPER_PIPE.get()   // 对应的铜管方块
                    ).build(null)
            );

    /**
     * 将方块实体类型注册器绑定到模组事件总线（MOD_BUS）。
     * 必须在模组主类构造器中调用。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
