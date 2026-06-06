package com.spectrum_reclamation.spectrum_reclamation.event;

import com.spectrum_reclamation.spectrum_reclamation.registry.SRMobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Spectrum Reclamation 事件处理器。
 * 监听游戏运行时事件（GAME_BUS），实现卸负效果的附加机制：
 * - 受击时按概率随机脱落盔甲
 * - 死亡时确保所有盔甲加入掉落物列表（去重）
 *
 * 注册方式：NeoForge.EVENT_BUS.register(SREventHandler.class)，
 * 使用类注册静态事件方法，无需实例化。
 */
public class SREventHandler {

    /**
     * 监听 LivingIncomingDamageEvent（等价于旧版 LivingHurtEvent）。
     * 当带卸负效果的实体受到伤害时，按等级概率随机脱落一件盔甲。
     *
     * LivingIncomingDamageEvent 在 LivingEntity.hurt() 中触发，
     * 位于无敌帧检查之后、伤害减免计算之前，适合在受伤时执行附加效果。
     *
     * @param event 受击事件，包含受伤实体和伤害信息
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        // 仅在服务端处理，避免客户端重复弹出物品
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // 检查受伤者是否携带卸负效果
        if (!event.getEntity().hasEffect(SRMobEffects.UNBURDEN)) {
            return;
        }

        // 获取卸负效果的等级（amplifier：0 = I 级，1 = II 级）
        int amplifier = event.getEntity().getEffect(SRMobEffects.UNBURDEN).getAmplifier();

        // 脱落概率：等级 I（amplifier=0）为 10%，等级 II（amplifier=1）为 20%
        float dropChance = 0.10f * (amplifier + 1);

        RandomSource random = event.getEntity().getRandom();

        // 定义 4 个盔甲槽位：头盔、胸甲、护腿、靴子
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        // 遍历每个盔甲槽位，按概率触发脱落
        for (EquipmentSlot slot : armorSlots) {
            ItemStack itemStack = event.getEntity().getItemBySlot(slot);

            // 跳过空槽位
            if (itemStack.isEmpty()) {
                continue;
            }

            // 随机判定是否脱落
            if (random.nextFloat() < dropChance) {
                // 在实体位置创建掉落物 ItemEntity
                ItemEntity itemEntity = new ItemEntity(
                        event.getEntity().level(),
                        event.getEntity().getX(),
                        event.getEntity().getY(),
                        event.getEntity().getZ(),
                        itemStack.copy()
                );

                // 设置拾取延迟 40 ticks（2 秒），防止立即被拾取
                itemEntity.setPickUpDelay(40);

                // 将掉落物添加到世界中
                event.getEntity().level().addFreshEntity(itemEntity);

                // 清空该槽位
                event.getEntity().setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /**
     * 监听 LivingDropsEvent，确保卸负效果死亡时所有盔甲都加入掉落物。
     * 原版 LivingEntity.dropEquipment() 可能已将部分盔甲加入 drops，
     * 因此必须先检查去重，避免同一件盔甲出现两次。
     *
     * @param event 死亡掉落事件，包含已死亡实体和掉落物集合
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        // 仅在服务端处理
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // 检查死亡者是否携带卸负效果
        if (!event.getEntity().hasEffect(SRMobEffects.UNBURDEN)) {
            return;
        }

        // 获取当前已有的掉落物集合（原版 dropEquipment 已放入的条目）
        java.util.Collection<ItemEntity> drops = event.getDrops();

        // 定义 4 个盔甲槽位
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        // 遍历 4 个盔甲槽位
        for (EquipmentSlot slot : armorSlots) {
            ItemStack itemStack = event.getEntity().getItemBySlot(slot);

            // 跳过空槽位（原版可能已将盔甲从槽位移除并加入 drops）
            if (itemStack.isEmpty()) {
                continue;
            }

            // === 去重逻辑 ===
            // 检查该槽位物品是否已存在于 drops 列表中。
            // 使用 ItemStack.isSameItemSameComponents() 进行严格比较：
            // 同时检查物品类型和 Data Components 是否完全一致。
            // 如果原版 dropEquipment() 已将该物品加入 drops，则跳过，避免重复。
            boolean alreadyInDrops = drops.stream().anyMatch(
                    existing -> ItemStack.isSameItemSameComponents(existing.getItem(), itemStack)
            );

            // 不在已有掉落物中，才添加
            if (!alreadyInDrops) {
                ItemEntity itemEntity = new ItemEntity(
                        event.getEntity().level(),
                        event.getEntity().getX(),
                        event.getEntity().getY(),
                        event.getEntity().getZ(),
                        itemStack.copy()
                );

                itemEntity.setPickUpDelay(40);

                // 将盔甲加入死亡掉落物集合
                drops.add(itemEntity);
            }
        }
    }
}
