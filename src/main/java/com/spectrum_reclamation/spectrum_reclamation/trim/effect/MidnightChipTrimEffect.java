package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.entity.LivingEntity;

/**
 * 午夜碎片纹饰效果处理器。
 *
 * 材料：spectrum_reclamation:midnight_chip
 * 效果：攻击无视目标 6%/件 护甲
 *
 * 使用 TrimCountedValue 计算护甲穿透百分比：
 * - 1 件：无视 6%（0.06）
 * - 2 件：无视 12%（0.12）
 * - 3 件：无视 18%（0.18）
 * - 4 件：无视 24%（0.24）
 *
 * 实现原理：
 * Minecraft 护甲减伤公式中，护甲值越高减伤越明显。
 * 通过在伤害乘数中叠加一个正向加成，等效于"忽略部分护甲"的效果。
 * 这是一种简化的护甲穿透实现 —— 与真正按护甲值百分比移除的方式有细微差异，
 * 但对游戏体验来说效果接近，且实现成本最低。
 *
 * onHurt() 返回伤害乘数加算值，由 TrimEffectEventHandler 统一累加并应用。
 */
public class MidnightChipTrimEffect implements TrimEffectHandler {

    /**
     * 护甲穿透伤害加成计算模型：基础值 0，每件 +6%（0.06）。
     * calc(count) 返回百分比值，如 calc(4) = 0.24（24%）
     */
    private static final TrimCountedValue ARMOR_PENETRATION = TrimCountedValue.linear(0.0, 0.06);

    /**
     * 受伤时计算护甲穿透带来的伤害加成。
     *
     * 无视目标护甲等效于提升伤害输出：
     * 返回的加成百分比会叠加到最终伤害乘数中，
     * 效果类似忽略目标部分护甲值后的伤害提升。
     *
     * @param entity 受伤的实体
     * @param count  午夜碎片纹饰的盔甲件数（0-4）
     * @param damage 当前伤害值
     * @return 伤害乘数加算值（如 4 件时返回 0.24，表示等效 +24% 伤害）
     */
    @Override
    public float onHurt(LivingEntity entity, int count, float damage) {
        return (float) ARMOR_PENETRATION.calc(count); // 护甲穿透百分比
    }
}
