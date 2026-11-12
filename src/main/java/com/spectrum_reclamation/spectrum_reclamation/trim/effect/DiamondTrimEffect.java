package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.entity.LivingEntity;

/**
 * 钻石纹饰效果处理器。
 *
 * 材料：minecraft:diamond
 * 效果：暴击伤害 +5%/件
 *
 * 使用 TrimCountedValue 计算暴击伤害加成百分比：
 * - 1 件：+5%（0.05）
 * - 2 件：+10%（0.10）
 * - 3 件：+15%（0.15）
 * - 4 件：+20%（0.20）
 *
 * 暴击伤害修正公式：最终暴击伤害 = 基础暴击伤害 × (1 + 加成百分比)
 *
 * 注意：onCriticalHit 保持 void 返回，暴击事件的处理需要
 * 单独的 CriticalHitEvent 监听器来完成。
 */
public class DiamondTrimEffect implements TrimEffectHandler {

    /**
     * 暴击伤害加成计算模型：基础值 0，每件 +5%（0.05）。
     * calc(count) 返回百分比值，如 calc(4) = 0.20（20%）
     */
    private static final TrimCountedValue CRIT_DAMAGE_BONUS = TrimCountedValue.linear(0.0, 0.05);

    /**
     * 暴击时计算额外暴击伤害加成。
     *
     * @param attacker 发起暴击的攻击者
     * @param target   被暴击的目标
     * @param count    攻击者身上钻石纹饰的盔甲件数（0-4）
     */
    @Override
    public void onCriticalHit(LivingEntity attacker, LivingEntity target, int count) {
        // 暴击伤害加成由 TrimEffectEventHandler 中的 CriticalHitEvent 监听器处理
    }

    /**
     * 获取暴击伤害乘数（多态接口方法）。
     * 返回值 = 1.0 + 加成百分比（如 4 件 = 1.0 + 0.20 = 1.20）。
     *
     * @param count 钻石纹饰的盔甲件数
     * @return 暴击伤害乘数
     */
    @Override
    public float getCritDamageMultiplier(int count) {
        return 1.0f + (float) CRIT_DAMAGE_BONUS.calc(count);
    }

    /**
     * 获取暴击伤害加成百分比（保留向后兼容）。
     *
     * @param count 钻石纹饰的盔甲件数
     * @return 暴击伤害加成百分比（如 0.20 = +20%）
     * @deprecated 使用 {@link #getCritDamageMultiplier(int)} 代替
     */
    @Deprecated
    public double getCritDamageBonus(int count) {
        return CRIT_DAMAGE_BONUS.calc(count);
    }
}
