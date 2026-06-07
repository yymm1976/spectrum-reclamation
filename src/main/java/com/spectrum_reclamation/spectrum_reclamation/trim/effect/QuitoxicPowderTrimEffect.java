package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 毒紫粉纹饰效果处理器。
 *
 * 材料：spectrum_reclamation:quitoxic_powder
 * 效果：被攻击时，攻击者中毒；每件 +1 中毒等级
 *
 * 触发条件：被穿戴此纹饰盔甲的玩家受到攻击时。
 * - 1 件：攻击者获得中毒 I（amplifier=0），持续 5 秒
 * - 2 件：攻击者获得中毒 II（amplifier=1），持续 5 秒
 * - 3 件：攻击者获得中毒 III（amplifier=2），持续 5 秒
 * - 4 件：攻击者获得中毒 IV（amplifier=3），持续 5 秒
 *
 * 实现原理：
 * 使用 TrimEffectHandler 新增的 onHurt(entity, count, damage, DamageSource) 重载方法，
 * 通过 DamageSource.getEntity() 获取攻击者实体，然后对其施加中毒效果。
 *
 * 中毒等级 = count（件数），amplifier 在 Minecraft 中从 0 开始计算：
 * - amplifier=0 → 中毒 I，amplifier=1 → 中毒 II，以此类推
 *
 * 持续时间固定为 100 tick（5 秒），可通过 count 调整等级而非时长，
 * 保持效果强度可控。
 *
 * 此效果不修改伤害数值（返回 0.0f），仅施加副作用（中毒）。
 */
public class QuitoxicPowderTrimEffect implements TrimEffectHandler {

    /** 中毒效果持续时间（tick），100 tick = 5 秒 */
    private static final int POISON_DURATION = 100;

    /**
     * 受伤时对攻击者施加中毒效果。
     *
     * 通过 DamageSource 获取攻击者实体：
     * - source.getEntity() 返回造成伤害的实体（通常为攻击者）
     * - 仅当攻击者是 LivingEntity 时才施加中毒（排除箭、爆炸等非生物伤害源）
     *
     * 使用重载的 onHurt(entity, count, damage, DamageSource) 方法，
     * 该方法由 TrimEffectEventHandler 从 LivingIncomingDamageEvent 中传递。
     *
     * @param entity 受伤的实体（穿戴毒紫粉纹饰盔甲的玩家）
     * @param count  毒紫粉纹饰的盔甲件数（0-4）
     * @param damage 当前伤害值
     * @param source 伤害来源，包含攻击者信息
     * @return 0.0f —— 此效果不修改伤害数值，仅施加中毒副作用
     */
    @Override
    public float onHurt(LivingEntity entity, int count, float damage, DamageSource source) {
        // 仅在服务端执行，避免客户端效果同步问题
        if (entity.level().isClientSide()) {
            return 0.0f;
        }

        // 从伤害来源获取攻击者实体
        // getEntity() 返回造成伤害的实体，可能是直接攻击者或远程攻击者
        if (source.getEntity() instanceof LivingEntity attacker) {
            // 中毒等级 = 纹饰件数（amplifier 从 0 开始，count=1 → amplifier=0 → 中毒 I）
            // 使用 max(0, count - 1) 确保 amplifier 不为负数
            int amplifier = Math.max(0, count - 1);

            // 对攻击者施加中毒效果
            // addEffect 会自动合并同类型效果：如果攻击者已有中毒，会选择更强等级或延长持续时间
            attacker.addEffect(new MobEffectInstance(
                    MobEffects.POISON,       // 中毒效果类型
                    POISON_DURATION,         // 持续时间：100 tick（5 秒）
                    amplifier,               // 中毒等级（amplifier=0 为中毒 I）
                    false,                   // 不显示粒子（避免视觉混乱）
                    true                     // 显示在 HUD 上（让玩家知道被中毒了）
            ));
        }

        // 不修改伤害数值，仅施加中毒副作用
        return 0.0f;
    }
}
