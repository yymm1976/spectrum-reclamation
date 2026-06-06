package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 药水（Potion）注册类。
 * 使用 DeferredRegister 延迟注册所有自定义药水，
 * 确保在 NeoForge 注册阶段由引擎统一处理。
 *
 * 药水是状态效果的"容器"，定义了效果类型、持续时间和等级。
 * 一个 MobEffect 可以对应多种药水（普通、强效、延长等）。
 *
 * 注意：SRMobEffects 必须先于此注册完成，因为药水引用了 UNBURDEN 效果的 Holder。
 * 由于使用 DeferredHolder，实际解析在注册阶段完成，不存在时序问题。
 */
public class SRPotions {

    /**
     * 药水的 DeferredRegister。
     * Registries.POTION 是 1.21.x 中药水的注册表键。
     */
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, SpectrumReclamation.MOD_ID);

    /**
     * 普通卸负药水（Unburden）。
     * - 效果：卸负（UNBURDEN），等级 I（amplifier = 0）
     * - 持续时间：3600 ticks（3 分钟）
     * - 用途：酿造台基础药水
     */
    public static final DeferredHolder<Potion, Potion> UNBURDEN =
            POTIONS.register(
                    "unburden",
                    () -> new Potion(
                            // MobEffectInstance 参数：效果 Holder、持续时间（ticks）、等级（amplifier）
                            // DeferredHolder 同时实现了 Holder 接口，可直接传入
                            new MobEffectInstance(SRMobEffects.UNBURDEN, 3600, 0)
                    )
            );

    /**
     * 强效卸负药水（Strong Unburden）。
     * - 效果：卸负（UNBURDEN），等级 II（amplifier = 1）
     * - 持续时间：2400 ticks（2 分钟）
     * - 用途：通过红石粉以外的材料（如萤石粉）增强酿造获得
     *
     * 强效药水通常持续时间更短但效果更强。
     */
    public static final DeferredHolder<Potion, Potion> STRONG_UNBURDEN =
            POTIONS.register(
                    "strong_unburden",
                    () -> new Potion(
                            new MobEffectInstance(SRMobEffects.UNBURDEN, 2400, 1)
                    )
            );

    /**
     * 将药水注册器绑定到模组事件总线（MOD_BUS）。
     * 必须在模组主类构造器中调用，否则注册项不会生效。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        POTIONS.register(modEventBus);
    }
}
