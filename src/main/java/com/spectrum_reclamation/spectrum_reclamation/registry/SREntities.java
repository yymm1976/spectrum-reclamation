package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.entity.BlazingBombEntity;
import com.spectrum_reclamation.spectrum_reclamation.entity.ThrownHeavySpear;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体类型（EntityType）注册类。
 * 使用 DeferredRegister 延迟注册所有自定义实体类型，
 * 确保在 NeoForge 注册阶段由引擎统一处理。
 */
public class SREntities {

    /** 实体类型的 DeferredRegister */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SpectrumReclamation.MOD_ID);

    /**
     * 炽光炸弹弹射物实体类型。
     * - 分类：MISC（杂项）
     * - 体积：0.25×0.25（与雪球相同）
     * - 客户端追踪距离：4 区块
     * - 更新间隔：10 ticks
     *
     * build() 参数为实体类型的注册表名称字符串。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<BlazingBombEntity>> BLAZING_BOMB =
            ENTITY_TYPES.register(
                    "blazing_bomb",
                    () -> EntityType.Builder.<BlazingBombEntity>of(BlazingBombEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("blazing_bomb")
            );

    /**
     * 将实体类型注册器绑定到模组事件总线（MOD_BUS）。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    // ==================== 陨星弩与沉重之矛 ====================

    /**
     * 沉重之矛弹射物实体类型。
     * - 分类：MISC（杂项）
     * - 体积：0.25×0.25（与箭矢相同）
     * - 客户端追踪距离：4 区块
     * - 更新间隔：10 ticks
     *
     * 继承 AbstractArrow，具有完整的碰撞检测和伤害计算。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownHeavySpear>> THROWN_HEAVY_SPEAR =
            ENTITY_TYPES.register(
                    "thrown_heavy_spear",
                    () -> EntityType.Builder.<ThrownHeavySpear>of(ThrownHeavySpear::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("thrown_heavy_spear")
            );
}
