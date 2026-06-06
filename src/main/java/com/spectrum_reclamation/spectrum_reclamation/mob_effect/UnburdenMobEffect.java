package com.spectrum_reclamation.spectrum_reclamation.mob_effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;

/**
 * 卸负（Unburden）状态效果类。
 * 有害效果，使目标周期性地随机脱落身上的盔甲。
 *
 * 效果机制：
 * - 每 100 ticks（5 秒）触发一次
 * - 等级 0（I）：每个盔甲槽位 5% 概率脱落
 * - 等级 1（II）：每个盔甲槽位 10% 概率脱落
 *
 * 脱落的盔甲会作为 ItemEntity 弹出到世界中，模拟装备被强制卸下的效果。
 */
public class UnburdenMobEffect extends MobEffect {

    /**
     * 构造函数，初始化卸负效果的基本属性。
     *
     * @param category 效果类别，HARMFUL 表示有害效果
     * @param color    效果粒子颜色，使用 RGB 整数表示
     */
    public UnburdenMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    /**
     * 判断本次 tick 是否应该应用效果。
     * 使用 duration（剩余持续时间）的模运算实现每 100 ticks 触发一次。
     *
     * 注意：duration 是倒计时，每次 tick 会减少 1。
     * 当 duration % 100 == 0 时触发，确保在效果持续期间周期性执行。
     *
     * @param duration  效果剩余持续时间（ticks）
     * @param amplifier 效果等级（0 = I 级，1 = II 级）
     * @return true 表示应该在本 tick 应用效果
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // 每 100 ticks 触发一次（5 秒）
        return duration % 100 == 0;
    }

    /**
     * 应用效果逻辑：遍历 4 个盔甲槽位，按概率使盔甲脱落。
     *
     * 脱落机制：
     * 1. 获取当前槽位的物品
     * 2. 根据等级计算脱落概率（等级 0: 5%，等级 1: 10%）
     * 3. 若触发脱落，将物品弹出为 ItemEntity 并清空槽位
     *
     * @param livingEntity 受效果影响的实体
     * @param amplifier    效果等级（0 = I 级，1 = II 级）
     * @return true 表示效果已成功应用（1.21.1 NeoForge 要求返回布尔值）
     */
    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        // 仅在服务端处理，客户端不做逻辑（避免重复弹出物品）
        if (livingEntity.level().isClientSide()) {
            return false;
        }

        // 获取实体所在世界的随机数源
        RandomSource random = livingEntity.getRandom();

        // 定义 4 个盔甲槽位：头盔、胸甲、护腿、靴子
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        // 脱落概率：等级 0 = 5%（0.05），等级 1 = 10%（0.10）
        // amplifier 0 对应等级 I，amplifier 1 对应等级 II
        float dropChance = 0.05f * (amplifier + 1);

        // 遍历每个盔甲槽位
        for (EquipmentSlot slot : armorSlots) {
            // 获取该槽位当前的物品
            ItemStack itemStack = livingEntity.getItemBySlot(slot);

            // 跳过空槽位
            if (itemStack.isEmpty()) {
                continue;
            }

            // 随机判定是否脱落
            if (random.nextFloat() < dropChance) {
                // 在实体位置创建掉落物 ItemEntity
                // 参数：世界、x、y、z、物品栈
                ItemEntity itemEntity = new ItemEntity(
                        livingEntity.level(),
                        livingEntity.getX(),
                        livingEntity.getY(),
                        livingEntity.getZ(),
                        itemStack.copy()  // 复制物品栈，避免引用问题
                );

                // 设置掉落物的拾取延迟为 40 ticks（2 秒），防止立即被拾取
                itemEntity.setPickUpDelay(40);

                // 将掉落物添加到世界中
                livingEntity.level().addFreshEntity(itemEntity);

                // 清空该槽位，移除装备
                livingEntity.setItemSlot(slot, ItemStack.EMPTY);
            }
        }

        // 效果已应用
        return true;
    }
}
