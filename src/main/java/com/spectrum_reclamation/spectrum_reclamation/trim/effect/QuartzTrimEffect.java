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
 * onHurt() 返回伤害乘数加算值，由 TrimEffectEventHandler 统一累加并应用。
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
     * 返回值由事件分发器累加后统一应用到事件：
     *   event.setDamage(damage * (1.0f + totalBonus))
     *
     * @param entity 受伤的实体
     * @param count  石英纹饰的盔甲件数（0-4）
     * @param damage 当前伤害值
     * @return 伤害乘数加算值（如 4 件时返回 0.08，表示 +8% 伤害）
     */
    @Override
    public float onHurt(LivingEntity entity, int count, float damage) {
        return 0.0f; // 石英纹饰是攻击侧效果，防御方不生效
    }

    /**
     * 攻击者穿戴石英纹饰时，增加造成伤害。
     * 由 TrimEffectEventHandler 检查攻击者护甲后调用。
     */
    @Override
    public float onDealDamage(LivingEntity attacker, LivingEntity target, int count, float damage) {
        return (float) DAMAGE_BONUS.calc(count);
    }
}
