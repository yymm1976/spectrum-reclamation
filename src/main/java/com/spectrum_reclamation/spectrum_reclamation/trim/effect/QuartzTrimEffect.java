package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.entity.LivingEntity;

/**
 * 石英纹饰效果处理器。
 *
 * 材料：minecraft:quartz
 * 效果：近战攻击伤害 +2%/件
 *
 * 使用 TrimCountedValue 计算伤害加成百分比：
 * - 1 件：+2%
 * - 2 件：+4%
 * - 3 件：+6%
 * - 4 件：+8%
 *
 * 伤害修正公式：最终伤害 = 原始伤害 × (1 + 加成百分比)
 *
 * 注意：onHurt 方法返回 void，实际伤害修改需要调用方
 * 在事件中通过 event.setDamage(damage * multiplier) 完成。
 */
public class QuartzTrimEffect implements TrimEffectHandler {

    /**
     * 伤害加成计算模型：基础值 0，每件 +2%（0.02）。
     * calc(count) 返回百分比值，如 calc(4) = 0.08（8%）
     */
    private static final TrimCountedValue DAMAGE_BONUS = TrimCountedValue.linear(0.0, 0.02);

    /**
     * 受伤时计算伤害加成百分比。
     *
     * 调用方应使用返回的乘数修正伤害：
     *   float multiplier = (float) (1.0 + DAMAGE_BONUS.calc(count));
     *   event.setDamage(damage * multiplier);
     *
     * @param entity 受伤的实体
     * @param count  石英纹饰的盔甲件数（0-4）
     * @param damage 当前伤害值
     */
    @Override
    public void onHurt(LivingEntity entity, int count, float damage) {
        // 石英纹饰的伤害加成由 TrimCountedValue 线性计算
        // 调用方需通过 event.setDamage() 应用 damage * (1 + DAMAGE_BONUS.calc(count))
    }
}
