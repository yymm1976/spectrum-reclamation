package com.spectrum_reclamation.spectrum_reclamation.trim;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纹饰效果事件分发器。
 *
 * 监听 NeoForge 游戏运行时事件（GAME_BUS），将事件分发给
 * TrimEffectRegistry 中注册的所有纹饰效果处理器。
 *
 * 设计思路：
 * - 事件处理器只负责"分发"，不包含任何纹饰效果的具体逻辑
 * - 具体效果逻辑由各 TrimEffectHandler 实现类负责
 * - 分发时通过 TrimEffectRegistry.lookupFromArmor() 获取当前实体盔甲上
 *   所有纹饰对应的处理器列表，并统计每种处理器的件数（count）
 *
 * 侧安全说明：
 * - LivingIncomingDamageEvent / LivingExperienceDropEvent / LivingFallEvent：
 *   客户端和服务端都会触发，需显式检查 !level.isClientSide
 * - LivingEquipmentChangeEvent：NeoForge 保证仅在服务端触发，无需额外检查
 *
 * 注册方式：通过 NeoForge.EVENT_BUS.register(TrimEffectEventHandler.class) 注册，
 * 使用类注册（静态方法 + @SubscribeEvent），由 NeoForge 自动发现事件监听方法。
 */
public class TrimEffectEventHandler {

    // ==================== 事件监听方法 ====================

    /**
     * 监听实体受伤事件。
     *
     * 触发条件：任何 LivingEntity 即将受到伤害时。
     * 调用时机：在 LivingEntity.hurt() 中，无敌帧检查之后、伤害处理之前。
     *
     * NeoForge 事件机制：LivingIncomingDamageEvent 替代了旧版 LivingHurtEvent，
     * 通过 DamageContainer 管理伤害流程，支持添加伤害减免修正器。
     *
     * @param event 受伤事件，包含受伤实体和伤害信息
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();

        // 仅在服务端处理，避免客户端与服务端重复执行
        if (entity.level().isClientSide()) {
            return;
        }

        // 获取实体盔甲上所有纹饰对应的处理器（含重复件数）
        List<TrimEffectHandler> handlers = TrimEffectRegistry.lookupFromArmor(entity);
        if (handlers.isEmpty()) {
            return;
        }

        // 统计每种处理器的件数（同一处理器出现 N 次 = 该纹饰材料在 N 件盔甲上）
        // 使用 HashMap<对象引用, 次数> 进行计数，Object.equals() 默认用引用比较
        Map<TrimEffectHandler, Integer> countMap = new HashMap<>();
        for (TrimEffectHandler handler : handlers) {
            countMap.merge(handler, 1, Integer::sum);
        }

        // 累加所有处理器返回的伤害乘数加算值（多个纹饰效果的加成叠加）
        // 例：石英 +2% 和午夜碎片 +8% → 总加算 = 0.02 + 0.08 = 0.10
        float totalDamageBonus = 0.0f;
        for (Map.Entry<TrimEffectHandler, Integer> entry : countMap.entrySet()) {
            totalDamageBonus += entry.getKey().onHurt(entity, entry.getValue(), event.getAmount());
        }

        // 有伤害加成时才修改事件，避免不必要的调用
        // 最终伤害 = 原始伤害 × (1 + 总加算值)
        // setAmount() 是 LivingIncomingDamageEvent 修改伤害的正确方法
        if (totalDamageBonus != 0.0f) {
            event.setAmount(event.getAmount() * (1.0f + totalDamageBonus));
        }
    }

    /**
     * 监听经验掉落事件。
     *
     * 触发条件：任何 LivingEntity 死亡并掉落经验时。
     * 调用时机：在经验掉落计算完成后、实际生成经验球之前。
     *
     * NeoForge 事件机制：LivingExperienceDropEvent 提供攻击者（Player）信息，
     * 因此纹饰效果基于攻击者（玩家）身上的盔甲纹饰，而非被击杀实体。
     *
     * @param event 经验掉落事件，包含被击杀实体和攻击玩家
     */
    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();

        // 仅在服务端处理
        if (entity.level().isClientSide()) {
            return;
        }

        // 获取攻击者（击杀者）玩家 —— 纹饰效果基于玩家身上的盔甲
        net.minecraft.world.entity.player.Player attackingPlayer = event.getAttackingPlayer();
        if (attackingPlayer == null) {
            return;
        }

        // 从攻击者的盔甲纹饰中查找处理器
        List<TrimEffectHandler> handlers = TrimEffectRegistry.lookupFromArmor(attackingPlayer);
        if (handlers.isEmpty()) {
            return;
        }

        // 统计每种处理器的件数
        Map<TrimEffectHandler, Integer> countMap = new HashMap<>();
        for (TrimEffectHandler handler : handlers) {
            countMap.merge(handler, 1, Integer::sum);
        }

        // 累加所有处理器返回的额外经验值（多个纹饰效果的经验加成叠加）
        int totalExtraExp = 0;
        for (Map.Entry<TrimEffectHandler, Integer> entry : countMap.entrySet()) {
            totalExtraExp += entry.getKey().onExperienceDrop(entity, entry.getValue(), event.getDroppedExperience());
        }

        // 有额外经验时才修改事件
        if (totalExtraExp > 0) {
            event.setDroppedExperience(event.getDroppedExperience() + totalExtraExp);
        }
    }

    /**
     * 监听摔落事件。
     *
     * 触发条件：任何 LivingEntity 因摔落而受到伤害时。
     * 调用时机：在 LivingEntity.causeFallDamage() 中。
     *
     * NeoForge 事件机制：LivingFallEvent 允许修改摔落距离和伤害乘数。
     * 取消此事件可完全阻止摔落伤害。
     *
     * @param event 摔落事件，包含摔落实体和距离信息
     */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();

        // 仅在服务端处理
        if (entity.level().isClientSide()) {
            return;
        }

        // 获取该实体盔甲上所有纹饰对应的处理器
        List<TrimEffectHandler> handlers = TrimEffectRegistry.lookupFromArmor(entity);
        if (handlers.isEmpty()) {
            return;
        }

        // 统计每种处理器的件数
        Map<TrimEffectHandler, Integer> countMap = new HashMap<>();
        for (TrimEffectHandler handler : handlers) {
            countMap.merge(handler, 1, Integer::sum);
        }

        // 累加所有处理器返回的摔落距离减免量（多个纹饰效果的减免叠加）
        float totalFallReduction = 0.0f;
        for (Map.Entry<TrimEffectHandler, Integer> entry : countMap.entrySet()) {
            totalFallReduction += entry.getKey().onFall(entity, entry.getValue(), event.getDistance());
        }

        // 有摔落减免时才修改事件，且确保距离不会变为负数
        if (totalFallReduction > 0.0f) {
            float newDistance = Math.max(0.0f, event.getDistance() - totalFallReduction);
            event.setDistance(newDistance);
        }
    }

    /**
     * 监听装备变化事件。
     *
     * 触发条件：任何 LivingEntity 的装备槽位发生变化时（穿戴、脱下、替换）。
     * 调用时机：在 LivingEntity.tick() 中检测到装备变化后。
     * 注意：此事件仅在服务端触发（NeoForge 保证），无需额外检查 isClientSide。
     *
     * NeoForge 事件机制：LivingEquipmentChangeEvent 提供变化前后的物品信息，
     * 可用于重置计数器、更新属性修饰器等需要在装备切换时执行的逻辑。
     *
     * @param event 装备变化事件，包含变化的槽位和前后物品
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();

        // 获取该实体盔甲上所有纹饰对应的处理器
        List<TrimEffectHandler> handlers = TrimEffectRegistry.lookupFromArmor(entity);
        if (handlers.isEmpty()) {
            return;
        }

        // 统计每种处理器的件数
        Map<TrimEffectHandler, Integer> countMap = new HashMap<>();
        for (TrimEffectHandler handler : handlers) {
            countMap.merge(handler, 1, Integer::sum);
        }

        // 分发给每个处理器
        for (Map.Entry<TrimEffectHandler, Integer> entry : countMap.entrySet()) {
            entry.getKey().onEquipmentChange(entity, entry.getValue());
        }
    }
}
