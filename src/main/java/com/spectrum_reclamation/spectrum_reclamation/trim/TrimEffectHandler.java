package com.spectrum_reclamation.spectrum_reclamation.trim;

import net.minecraft.world.entity.LivingEntity;

/**
 * 纹饰效果处理器接口。
 *
 * 每种纹饰材料（如石英、铁锭、钻石等）注册一个或多个 TrimEffectHandler，
 * 当玩家穿戴带有该纹饰的盔甲时，对应 handler 的方法会在合适的时机被调用。
 *
 * 所有方法均为 default 空实现 —— 调用方只需覆盖关心的方法，
 * 未覆盖的方法不会执行任何逻辑，避免强制实现不需要的回调。
 *
 * count 参数表示玩家身上携带该纹饰材料的盔甲件数（0-4），
 * handler 内部可通过 TrimCountedValue.linear(base, perPiece).calc(count) 计算最终效果值。
 */
public interface TrimEffectHandler {

    /**
     * 每 tick 或定时调用 —— 用于持续性效果（如属性修饰器的动态增减）。
     *
     * 适用场景：移速加成、护甲值加成等需要持续生效的效果。
     * 注意：高频调用，避免在此方法中执行耗时操作。
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  该纹饰材料在身上的件数（0-4）
     */
    default void onTick(LivingEntity entity, int count) {
        // 默认空实现，子类按需覆盖
    }

    /**
     * 受伤时调用 —— 用于与伤害相关的纹饰效果。
     *
     * 适用场景：石英纹饰的近战伤害加成、击退抗性等。
     * 对应 NeoForge 事件：LivingIncomingDamageEvent
     *
     * 返回值语义：伤害乘数加算值（1.0 = 无变化，0.08 = +8% 伤害加成）
     * 多个 handler 的返回值会在事件分发器中累加后统一应用。
     *
     * @param entity 受伤的实体
     * @param count  该纹饰材料在身上的件数（0-4）
     * @param damage 当前伤害值
     * @return 伤害乘数加算值（如 0.02 表示 +2% 伤害），默认 0.0f 表示无变化
     */
    default float onHurt(LivingEntity entity, int count, float damage) {
        return 0.0f; // 默认无伤害加成
    }

    /**
     * 暴击时调用 —— 用于暴击相关的纹饰效果。
     *
     * 适用场景：钻石纹饰的暴击伤害加成。
     * 对应 NeoForge 事件：CriticalHitEvent
     *
     * @param attacker 发起暴击的攻击者
     * @param target   被暴击的目标
     * @param count    攻击者身上该纹饰材料的件数（0-4）
     */
    default void onCriticalHit(LivingEntity attacker, LivingEntity target, int count) {
        // 默认空实现，子类按需覆盖
    }

    /**
     * 经验掉落时调用 —— 用于经验加成相关的纹饰效果。
     *
     * 适用场景：青金石纹饰的经验加成。
     * 对应 NeoForge 事件：LivingExperienceDropEvent
     *
     * 返回值语义：额外经验值（0 = 无变化，5 = 额外增加 5 点经验）
     * 多个 handler 的返回值会在事件分发器中累加后统一应用。
     *
     * @param entity 被击杀的实体
     * @param count  击杀者身上该纹饰材料的件数（0-4）
     * @param amount 当前经验掉落量
     * @return 额外经验值，0 表示无变化
     */
    default int onExperienceDrop(LivingEntity entity, int count, int amount) {
        return 0; // 默认无额外经验
    }

    /**
     * 摔落时调用 —— 用于摔落相关的纹饰效果。
     *
     * 适用场景：蜜脾纹饰的摔落有效高度减免。
     * 对应 NeoForge 事件：LivingFallEvent
     *
     * 返回值语义：摔落距离减免量（方块数），0 = 无变化
     * 多个 handler 的返回值会在事件分发器中累加后从距离中减去。
     *
     * @param entity   摔落的实体
     * @param count    该纹饰材料在身上的件数（0-4）
     * @param distance 摔落距离（方块数）
     * @return 摔落距离减免量（方块数），0 表示无减免
     */
    default float onFall(LivingEntity entity, int count, float distance) {
        return 0.0f; // 默认无摔落减免
    }

    /**
     * 装备变化时调用 —— 用于装备变化相关的纹饰效果。
     *
     * 适用场景：铜锭纹饰的耐久免消耗判定（需要在装备切换时重置计数器）。
     * 对应 NeoForge 事件：LivingEquipmentChangeEvent
     *
     * @param entity 装备变化的实体
     * @param count  该纹饰材料在身上的件数（0-4）
     */
    default void onEquipmentChange(LivingEntity entity, int count) {
        // 默认空实现，子类按需覆盖
    }
}
