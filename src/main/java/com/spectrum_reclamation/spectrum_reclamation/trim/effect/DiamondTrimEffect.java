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
 * 注意：onCriticalHit 方法返回 void，实际暴击伤害修改需要调用方
 * 在 CriticalHitEvent 中通过 event.setDamageModifier() 完成。
 * CriticalHitEvent 默认暴击伤害乘数为 1.5（50% 加成），
 * 钻石纹饰在此基础上额外叠加。
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
     * 调用方应使用返回的加成值修正暴击事件的伤害乘数：
     *   float bonus = (float) CRIT_DAMAGE_BONUS.calc(count);
     *   event.setDamageModifier(event.getDamageModifier() + bonus);
     *
     * @param attacker 发起暴击的攻击者
     * @param target   被暴击的目标
     * @param count    攻击者身上钻石纹饰的盔甲件数（0-4）
     */
    @Override
    public void onCriticalHit(LivingEntity attacker, LivingEntity target, int count) {
        // 钻石纹饰的暴击伤害加成由 TrimCountedValue 线性计算
        // 调用方需通过 CriticalHitEvent 的 setDamageModifier() 应用额外暴击加成
    }
}
