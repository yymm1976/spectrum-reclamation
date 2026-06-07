package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * 金锭纹饰效果处理器。
 *
 * 材料：minecraft:gold
 * 效果：+1 伤害吸收等级/件
 *
 * 通过 addEffect 施加吸收（Absorption）效果。
 * Absorption 效果为玩家提供额外的黄色心形血条（伤害吸收）：
 * - 1 件：Absorption I（amplifier=0），4 颗黄心
 * - 2 件：Absorption II（amplifier=1），8 颗黄心
 * - 3 件：Absorption III（amplifier=2），12 颗黄心
 * - 4 件：Absorption IV（amplifier=3），16 颗黄心
 *
 * 每 tick 重刷效果持续时间（40 ticks = 2 秒），确保效果不中断。
 * 使用 addEffect 而非 setEffect，避免覆盖其他来源的更高等级效果。
 */
public class GoldTrimEffect implements TrimEffectHandler {

    /** 效果持续时间（ticks），每次重刷 40 ticks（2 秒），确保持续生效 */
    private static final int EFFECT_DURATION = 40;

    /**
     * 每 tick 为实体施加吸收效果。
     *
     * 等级（amplifier）= count - 1：
     * - count=1 → amplifier=0（等级 I）
     * - count=4 → amplifier=3（等级 IV）
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  金锭纹饰的盔甲件数（0-4）
     */
    @Override
    public void onTick(LivingEntity entity, int count) {
        // count=0 时不施加任何效果
        if (count <= 0) {
            return;
        }

        // amplifier = count - 1：1 件对应等级 I（amplifier=0），4 件对应等级 IV（amplifier=3）
        int amplifier = count - 1;

        // 施加吸收效果，持续 40 ticks（2 秒）
        // MobEffects.ABSORPTION 是原版吸收效果的 Holder
        // addEffect 会自动合并同类型效果（取更高等级或刷新持续时间）
        entity.addEffect(new MobEffectInstance(
                MobEffects.ABSORPTION,
                EFFECT_DURATION,
                amplifier
        ));
    }
}
