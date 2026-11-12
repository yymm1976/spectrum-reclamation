package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

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
 *
 * 重要：MobEffectInstance.update() 方法内部会拒绝更短时长的更新
 * （仅当 newDuration > currentDuration 时才接受），因此必须使用
 * removeEffect + addEffect 的方式替换效果，而非 update()。
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
     * 遍历实体所有效果，对 HARMFUL 类别的效果缩短剩余时长。
     * 使用 removeEffect + addEffect 替代 update()，因为 update() 会静默拒绝更短的时长。
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  紫水晶纹饰的盔甲件数（0-4）
     */
    @Override
    public void onTick(LivingEntity entity, int count) {
        if (entity.level().isClientSide() || count <= 0) return;

        double reduction = DURATION_REDUCTION.calc(count);
        // 每 tick 按缩减比例额外消耗负面效果时长（乘以 20 使效果可感知）
        int extraConsumption = Math.max(1, (int) Math.floor(reduction * 20));

        // 收集需要修改的效果（避免在遍历 activeEffects 时修改集合导致 ConcurrentModificationException）
        List<MobEffectInstance> toReplace = new ArrayList<>();

        for (MobEffectInstance effect : entity.getActiveEffects()) {
            if (effect.getEffect().value().isBeneficial()) continue;
            int remaining = effect.getDuration();
            if (remaining <= 1) continue;

            int consume = Math.min(extraConsumption, remaining - 1);
            int newDuration = remaining - consume;
            toReplace.add(new MobEffectInstance(
                    effect.getEffect(), newDuration,
                    effect.getAmplifier(), effect.isAmbient(), effect.isVisible()
            ));
        }

        // 应用修改：先移除旧效果，再添加缩减后的新效果
        for (MobEffectInstance replacement : toReplace) {
            entity.removeEffect(replacement.getEffect());
            entity.addEffect(replacement);
        }
    }
}
