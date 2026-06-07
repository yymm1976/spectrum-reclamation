package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.entity.LivingEntity;

/**
 * 蜜脾纹饰效果处理器。
 *
 * 材料：minecraft:honeycomb
 * 效果：摔落有效高度 -1 格/件
 *
 * 使用 TrimCountedValue 计算摔落高度减免：
 * - 1 件：-1 格
 * - 2 件：-2 格
 * - 3 件：-3 格
 * - 4 件：-4 格
 *
 * 摔落伤害计算原理：
 * - Minecraft 摔落伤害 = (摔落距离 - 3) × 1
 * - 蜜脾纹饰通过减少有效摔落距离来降低伤害
 * - 例：从 10 格高摔落，4 件蜜脾纹饰 → 有效距离 = 10 - 4 = 6 格
 * - 伤害从 (10-3)=7 降低为 (6-3)=3
 *
 * 注意：onFall 方法返回 void，实际摔落距离修改需要调用方
 * 在 LivingFallEvent 中通过 event.setDistance(distance - reduction) 完成。
 * 当修改后距离 ≤ 0 时，实体将不受到任何摔落伤害。
 */
public class HoneycombTrimEffect implements TrimEffectHandler {

    /**
     * 摔落高度减免计算模型：基础值 0，每件 -1 格（1.0）。
     * calc(count) 返回减免格数，如 calc(4) = 4.0（4 格）
     */
    private static final TrimCountedValue FALL_REDUCTION = TrimCountedValue.linear(0.0, 1.0);

    /**
     * 摔落时计算有效摔落距离减免。
     *
     * 调用方应使用返回的减免值修正摔落事件的距离：
     *   float reduction = (float) FALL_REDUCTION.calc(count);
     *   float newDistance = Math.max(0, distance - reduction);
     *   event.setDistance(newDistance);
     *
     * @param entity   摔落的实体
     * @param count    蜜脾纹饰的盔甲件数（0-4）
     * @param distance 原始摔落距离（方块数）
     */
    @Override
    public void onFall(LivingEntity entity, int count, float distance) {
        // 蜜脾纹饰的摔落高度减免由 TrimCountedValue 线性计算
        // 调用方需通过 LivingFallEvent 的 setDistance() 应用距离减免
        // 当 count=4 且 distance ≤ 4 时，有效摔落距离为 0，不受到摔落伤害
    }
}
