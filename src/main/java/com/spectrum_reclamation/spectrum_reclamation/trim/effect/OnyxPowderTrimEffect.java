package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.entity.LivingEntity;

/**
 * 黑曜石粉纹饰效果处理器。
 *
 * 材料：spectrum_reclamation:onyx_powder
 * 效果：对满血目标首击 +8%/件
 *
 * 使用 TrimCountedValue 计算伤害加成百分比：
 * - 1 件：+8%（0.08）
 * - 2 件：+16%（0.16）
 * - 3 件：+24%（0.24）
 * - 4 件：+32%（0.32）
 *
 * 触发条件：目标当前生命值 == 最大生命值（满血状态）。
 * 满血时返回伤害加成百分比，否则返回 0（无加成）。
 *
 * 伤害修正公式：最终伤害 = 原始伤害 × (1 + 加成百分比)
 * onHurt() 返回伤害乘数加算值，由 TrimEffectEventHandler 统一累加并应用。
 */
public class OnyxPowderTrimEffect implements TrimEffectHandler {

    /**
     * 伤害加成计算模型：基础值 0，每件 +8%（0.08）。
     * calc(count) 返回百分比值，如 calc(4) = 0.32（32%）
     */
    private static final TrimCountedValue DAMAGE_BONUS = TrimCountedValue.linear(0.0, 0.08);

    /**
     * 受伤时计算伤害加成 —— 仅对满血目标生效。
     *
     * 检查 entity（受伤目标）是否处于满血状态：
     * - 满血时返回伤害加成百分比（如 4 件 = +32%）
     * - 非满血时返回 0（无加成）
     *
     * @param entity 受伤的实体（即被攻击的目标）
     * @param count  黑曜石粉纹饰的盔甲件数（0-4）
     * @param damage 当前伤害值
     * @return 伤害乘数加算值；满血时返回加成百分比，否则返回 0
     */
    @Override
    public float onHurt(LivingEntity entity, int count, float damage) {
        return 0.0f; // 黑曜石粉是攻击侧效果，防御方不生效
    }

    /**
     * 攻击者穿戴黑曜石粉纹饰时，对满血目标造成额外伤害。
     * 检查目标（entity 参数，即被攻击方）是否满血。
     */
    @Override
    public float onDealDamage(LivingEntity attacker, LivingEntity target, int count, float damage) {
        // 检查目标是否满血
        if (target.getHealth() >= target.getMaxHealth() - 0.001f) {
            return (float) DAMAGE_BONUS.calc(count);
        }
        return 0.0f;
    }
}
