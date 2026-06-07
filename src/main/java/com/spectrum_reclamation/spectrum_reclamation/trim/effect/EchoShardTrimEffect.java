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
     * - count < 4：不主动设置 false（避免覆盖其他来源的静音状态）
     *
     * 静音恢复由 onEquipmentChange 处理。
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  回响碎片纹饰的盔甲件数（0-4）
     */
    @Override
    public void onTick(LivingEntity entity, int count) {
        if (entity.level().isClientSide()) return;

        // 仅在穿满 4 件时设置静音，不主动设置 false
        if (count >= FULL_SET_COUNT) {
            entity.setSilent(true);
        }
    }

    /**
     * 装备变化时恢复静音状态。
     *
     * 当件数从 4 降到 < 4 时，恢复实体的声音。
     * 仅恢复由本模组设置的静音——通过检查实体当前是否静音来判断。
     * 如果实体被其他机制静音（如深灰涂装），此处不会干扰。
     *
     * @param entity 装备变化的实体
     * @param count  回响碎片纹饰的盔甲件数（0-4）
     */
    @Override
    public void onEquipmentChange(LivingEntity entity, int count) {
        if (entity.level().isClientSide()) return;

        // 件数减少到 < 4 且实体当前被静音 → 恢复声音
        if (count < FULL_SET_COUNT && entity.isSilent()) {
            entity.setSilent(false);
        }
    }
}
