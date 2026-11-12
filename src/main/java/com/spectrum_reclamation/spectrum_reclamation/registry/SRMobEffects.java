package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.mob_effect.UnburdenMobEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 状态效果（MobEffect）注册类。
 * 使用 DeferredRegister 延迟注册所有自定义状态效果，
 * 确保在 NeoForge 注册阶段由引擎统一处理。
 *
 * MobEffect 是效果的"类型定义"（如卸负、中毒），不包含持续时间和等级信息。
 * 具体的持续时间和等级由药水（Potion）或 MobEffectInstance 指定。
 */
public class SRMobEffects {

    /**
     * 状态效果的 DeferredRegister。
     * Registries.MOB_EFFECT 是 1.21.x 中状态效果的注册表键。
     */
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, SpectrumReclamation.MOD_ID);

    /**
     * 卸负（Unburden）状态效果。
     * 有害效果，使目标周期性地随机脱落身上的盔甲。
     * - 效果类别：HARMFUL（有害）
     * - 粒子颜色：0x6B2FA0（紫色，RGB 表示）
     *
     * 具体效果逻辑见 UnburdenMobEffect 类。
     */
    public static final DeferredHolder<MobEffect, MobEffect> UNBURDEN =
            MOB_EFFECTS.register(
                    "unburden",
                    () -> new UnburdenMobEffect(MobEffectCategory.HARMFUL, 0x6B2FA0)
            );

    /**
     * 将状态效果注册器绑定到模组事件总线（MOD_BUS）。
     * 必须在模组主类构造器中调用，否则注册项不会生效。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
