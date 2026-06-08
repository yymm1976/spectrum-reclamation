package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;

/**
 * 属性类纹饰效果的抽象基类。
 *
 * 提取自 IronTrimEffect / NetheriteTrimEffect / RedstoneTrimEffect / TurtleTrimEffect，
 * 四者的 updateModifiers 逻辑几乎完全一致，唯一差异在于：
 * - 目标属性（ARMOR / KNOCKBACK_RESISTANCE / MOVEMENT_SPEED / WATER_MOVEMENT_EFFICIENCY）
 * - 修饰器 ID 前缀（如 "spectrum_reclamation:trim.iron"）
 * - 奖励值计算模型（TrimCountedValue）
 * - 属性操作类型（通常为 ADD_VALUE）
 * - 匹配的纹饰材料 ID（如 "minecraft:iron"）
 *
 * 子类只需通过抽象方法提供上述差异参数，其余逻辑全部复用。
 * RedstoneTrimEffect 因需要"百分比 × 基础速度"的特殊计算，
 * 通过覆写 {@link #calculatePerSlotValue} 实现。
 */
public abstract class AbstractAttributeTrimEffect implements TrimEffectHandler {

    /** 4 个盔甲槽位，遍历顺序：头、胸、腿、脚 */
    protected static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // ==================== 抽象方法：子类必须实现 ====================

    /**
     * 获取要修改的目标属性（如 Attributes.ARMOR）。
     * 1.21.1 中 Attributes 常量为 Holder&lt;Attribute&gt; 类型。
     */
    protected abstract Holder<Attribute> getAttribute();

    /**
     * 获取修饰器 ID 前缀，基类会自动拼接 "_0" ~ "_3" 生成 4 个槽位 ID。
     * 例如 "spectrum_reclamation:trim.iron" → "spectrum_reclamation:trim.iron_0" 等。
     */
    protected abstract ResourceLocation getModifierIdPrefix();

    /**
     * 获取奖励值计算模型，用于根据纹饰件数计算总奖励。
     */
    protected abstract TrimCountedValue getCountedValue();

    /**
     * 获取属性修饰器的操作类型，通常为 ADD_VALUE。
     */
    protected abstract AttributeModifier.Operation getOperation();

    /**
     * 获取要匹配的纹饰材料 ID（如 "minecraft:iron"）。
     */
    protected abstract ResourceLocation getMaterialId();

    // ==================== 模板方法：可由子类覆写 ====================

    /**
     * 计算每个槽位的单件修饰器值。
     *
     * 默认实现：总奖励 ÷ 有纹饰的件数，确保所有纹饰槽位共享同一总奖励。
     * RedstoneTrimEffect 覆写此方法，将百分比乘以基础速度后再均分。
     *
     * @param totalBonus  getCountedValue().calc(trimCount) 计算出的总奖励
     * @param trimCount   身上有匹配纹饰的盔甲件数
     * @param attrInstance 属性实例，可获取 baseValue 等信息
     * @return 每个槽位应该添加的修饰器值
     */
    protected double calculatePerSlotValue(double totalBonus, int trimCount, AttributeInstance attrInstance) {
        return totalBonus / trimCount;
    }

    // ==================== TrimEffectHandler 接口实现 ====================

    /**
     * 装备变化时调用，触发属性修饰器更新。
     * 客户端侧直接跳过，避免客户端和服务端属性系统冲突。
     */
    @Override
    public void onEquipmentChange(LivingEntity entity, int count) {
        if (entity.level().isClientSide()) return;
        updateModifiers(entity);
    }

    // ==================== 核心逻辑 ====================

    /**
     * 更新属性修饰器的完整流程：
     * 1. 获取目标属性实例
     * 2. 移除 4 个槽位的旧修饰器
     * 3. 遍历盔甲，统计有匹配纹饰的件数
     * 4. 若有匹配纹饰：计算总奖励 → 均分到每个槽位 → 为匹配槽位添加新修饰器
     *
     * 设计为 protected，允许子类在需要时直接调用（虽然通常不需要）。
     */
    protected void updateModifiers(LivingEntity entity) {
        // 获取目标属性实例（如 ARMOR、MOVEMENT_SPEED 等）
        AttributeInstance attrInstance = entity.getAttribute(getAttribute());
        if (attrInstance == null) return;

        // 根据前缀生成 4 个槽位的修饰器 ID
        ResourceLocation[] slotIds = buildSlotIds();

        int trimCount = 0;
        // 第一轮遍历：移除旧修饰器 + 统计匹配纹饰件数
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            attrInstance.removeModifier(slotIds[i]);
            if (hasMatchingTrim(entity, ARMOR_SLOTS[i])) {
                trimCount++;
            }
        }

        // 有匹配纹饰时，计算并添加新修饰器
        if (trimCount > 0) {
            double totalBonus = getCountedValue().calc(trimCount);
            // 子类可覆写此方法实现特殊计算逻辑（如红石的"百分比×基础速度"）
            double perSlotValue = calculatePerSlotValue(totalBonus, trimCount, attrInstance);

            // 第二轮遍历：为有匹配纹饰的槽位添加修饰器
            int idx = 0;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                if (hasMatchingTrim(entity, slot)) {
                    attrInstance.addPermanentModifier(new AttributeModifier(
                            slotIds[idx], perSlotValue, getOperation()
                    ));
                }
                idx++;
            }
        }
    }

    /**
     * 检查指定槽位的盔甲是否携带匹配的纹饰材料。
     *
     * @param entity 实体
     * @param slot   盔甲槽位
     * @return true 表示该槽位有匹配纹饰
     */
    private boolean hasMatchingTrim(LivingEntity entity, EquipmentSlot slot) {
        ItemStack armorStack = entity.getItemBySlot(slot);
        if (armorStack.isEmpty()) return false;
        ArmorTrim trim = armorStack.get(DataComponents.TRIM);
        return trim != null
                && trim.material().unwrapKey().isPresent()
                && trim.material().unwrapKey().get().location().equals(getMaterialId());
    }

    /**
     * 根据修饰器 ID 前缀，生成 4 个槽位各自的 ResourceLocation。
     * 例如前缀 "spectrum_reclamation:trim.iron" →
     *   "spectrum_reclamation:trim.iron_0" ~ "spectrum_reclamation:trim.iron_3"
     */
    private ResourceLocation[] buildSlotIds() {
        String prefix = getModifierIdPrefix().toString();
        return new ResourceLocation[]{
                ResourceLocation.parse(prefix + "_0"),
                ResourceLocation.parse(prefix + "_1"),
                ResourceLocation.parse(prefix + "_2"),
                ResourceLocation.parse(prefix + "_3")
        };
    }
}
