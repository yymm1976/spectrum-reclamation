package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 紫水晶纹饰效果处理器。
 *
 * 材料：minecraft:amethyst
 * 效果：负面效果时长 -10%/件
 *
 * 使用 TrimCountedValue 计算时长缩减百分比：
 * - 1 件：-10%（0.10）
 * - 2 件：-20%（0.20）
 * - 3 件：-30%（0.30）
 * - 4 件：-40%（0.40）
 *
 * 实现方式：在 onTick 中检查实体当前所有负面效果，
 * 将时长缩减为原来的 (1 - reduction) 倍。
 */
public class AmethystTrimEffect implements TrimEffectHandler {

    /**
     * 负面效果时长缩减计算模型：基础值 0，每件 -10%（0.10）。
     * calc(count) 返回缩减比例，如 calc(4) = 0.40（40% 缩减）
     */
    private static final TrimCountedValue DURATION_REDUCTION = TrimCountedValue.linear(0.0, 0.10);

    /**
     * 每 tick 缩减负面效果时长。
     *
     * 遍历实体所有当前效果，对 HARMFUL 类别的效果缩短剩余时长。
     * 每 tick 执行一次缩减，确保效果时长持续被压制。
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  紫水晶纹饰的盔甲件数（0-4）
     */
    @Override
    public void onTick(LivingEntity entity, int count) {
        if (entity.level().isClientSide() || count <= 0) return;

        double reduction = DURATION_REDUCTION.calc(count);
        // 每 tick 缩减 1 tick 的 (reduction) 比例
        // 即每 tick 额外消耗 reduction tick 的时长
        int extraConsumption = (int) Math.floor(reduction * 2); // 每 2 tick 额外消耗 1 tick
        if (extraConsumption <= 0) return;

        for (MobEffectInstance effect : entity.getActiveEffects()) {
            // getEffect() 返回 Holder<MobEffect>，需通过 .value() 获取 MobEffect 实例再调用 isBeneficial()
            if (effect.getEffect().value().isBeneficial()) continue;
            // 缩短剩余时长
            int remaining = effect.getDuration();
            if (remaining > 1) {
                effect.update(new MobEffectInstance(
                        effect.getEffect(), remaining - extraConsumption,
                        effect.getAmplifier(), effect.isAmbient(), effect.isVisible()
                ));
            }
        }
    }
}
