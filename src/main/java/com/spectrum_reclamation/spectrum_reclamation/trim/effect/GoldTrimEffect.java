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

    /** 金纹饰只需要在吸收效果快过期前刷新，按秒执行即可降低每 tick 分发压力。 */
    private static final int TICK_INTERVAL = 20;

    /**
     * 获取金纹饰持续效果的执行间隔。
     *
     * 吸收效果每次持续 40 ticks，本类已有剩余时间检查，按 20 ticks 刷新不会中断效果。
     *
     * @return 每 20 ticks 执行一次
     */
    @Override
    public int getTickInterval() {
        return TICK_INTERVAL;
    }

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
        if (entity.level().isClientSide() || count <= 0) return;

        int amplifier = count - 1;

        // 不覆盖更高等级的吸收效果（如金苹果的 Absorption IV）
        MobEffectInstance existing = entity.getEffect(MobEffects.ABSORPTION);
        if (existing != null) {
            // 如果已有更高等级的效果，跳过
            if (existing.getAmplifier() > amplifier) return;
            // 如果效果持续时间充足（> 20 ticks），无需重新施加，避免每 tick 创建 MobEffectInstance
            if (existing.getDuration() > 20) return;
        }

        entity.addEffect(new MobEffectInstance(
                MobEffects.ABSORPTION,
                EFFECT_DURATION,
                amplifier
        ));
    }
}
