package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.entity.LivingEntity;

/**
 * 回响碎片纹饰效果处理器。
 *
 * 材料：minecraft:echo_shard
 * 效果：潜行完全无声（4 件时）
 *
 * 当玩家穿戴 4 件带有回响碎片纹饰的盔甲时，
 * 实体的所有声音将被静音（setSilent(true)）。
 * 这使得玩家在潜行时不会发出任何脚步声或其他声音。
 *
 * 原理：
 * - LivingEntity.setSilent(true) 阻止实体发出所有声音事件
 * - 仅在 count >= 4（穿满 4 件）时激活
 * - 移除纹饰后（count < 4）恢复声音
 *
 * 注意：setSilent 在客户端和服务端均有影响，
 * 但为避免服务端属性冲突，仅在服务端执行。
 *
 * // CONCERN: [RISK] setSilent(true) 会静音实体的所有声音，
 * // 不仅仅是脚步声。这可能影响玩家体验（如受伤音效、攻击音效也会被静音）。
 * // 如果只希望静音脚步声，需要使用 Mixin 在 SoundEvent 播放时进行过滤，
 * // 或使用 PlayerTickEvent 中的 isShift() + setSilent() 组合。
 */
public class EchoShardTrimEffect implements TrimEffectHandler {

    /** 激活完全无声效果所需的最低纹饰件数 */
    private static final int FULL_SET_COUNT = 4;

    /**
     * 每 tick 检查纹饰件数，控制静音状态。
     *
     * - count >= 4：设置 entity.setSilent(true)，静音所有声音
     * - count < 4：设置 entity.setSilent(false)，恢复声音
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  回响碎片纹饰的盔甲件数（0-4）
     */
    @Override
    public void onTick(LivingEntity entity, int count) {
        // 仅在服务端操作，避免客户端状态同步问题
        if (entity.level().isClientSide()) {
            return;
        }

        // 当穿满 4 件回响碎片纹饰时，完全静音
        // setSilent(true) 阻止实体发出所有声音事件（脚步、受伤、攻击等）
        entity.setSilent(count >= FULL_SET_COUNT);
    }
}
