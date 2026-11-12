package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.entity.LivingEntity;

/**
 * 青金石纹饰效果处理器。
 *
 * 材料：minecraft:lapis
 * 效果：击杀经验 +8%/件
 *
 * 使用 TrimCountedValue 计算经验加成百分比：
 * - 1 件：+8%（0.08）
 * - 2 件：+16%（0.16）
 * - 3 件：+24%（0.24）
 * - 4 件：+32%（0.32）
 *
 * 经验修正公式：额外经验 = 原始经验 × 加成百分比
 * onExperienceDrop() 返回额外经验值，由 TrimEffectEventHandler 统一累加并应用。
 *
 * 此效果实际触发的是击杀者的纹饰件数，而非被击杀实体的。
 * 因此 TrimEffectRegistry.lookupFromArmor() 应查询攻击方（玩家），
 * 而非被击杀的实体。
 */
public class LapisTrimEffect implements TrimEffectHandler {

    /**
     * 经验加成计算模型：基础值 0，每件 +8%（0.08）。
     * calc(count) 返回百分比值，如 calc(4) = 0.32（32%）
     */
    private static final TrimCountedValue EXP_BONUS = TrimCountedValue.linear(0.0, 0.08);

    /**
     * 计算击杀经验加成。
     *
     * 返回的额外经验值由事件分发器累加后统一应用：
     *   event.setDroppedExperience(originalExp + totalExtraExp)
     *
     * @param entity 被击杀的实体（纹饰效果来自攻击者）
     * @param count  击杀者身上青金石纹饰的盔甲件数（0-4）
     * @param amount 当前经验掉落量
     * @return 额外经验值（如原始经验 5，4 件青金石 → 5 × 0.32 = 1.6 → 向下取整为 1）
     */
    @Override
    public int onExperienceDrop(LivingEntity entity, int count, int amount) {
        return (int) Math.floor(amount * EXP_BONUS.calc(count)); // 向下取整，避免经验膨胀
    }
}
