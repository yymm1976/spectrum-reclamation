package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;

/**
 * 红石粉纹饰效果处理器。
 *
 * 材料：minecraft:redstone
 * 效果：移动速度 +3%/件
 *
 * MOVEMENT_SPEED 基础值为 0.1，使用 ADD_VALUE 直接叠加：
 * - 1 件：+0.003（+3% of 0.1）
 * - 4 件：+0.012（+12% of 0.1，最终速度 0.112）
 *
 * 仅在装备变化时更新属性修饰器。
 */
public class RedstoneTrimEffect implements TrimEffectHandler {

    private static final TrimCountedValue SPEED_BONUS = TrimCountedValue.linear(0.0, 0.03);

    private static final ResourceLocation[] SLOT_IDS = {
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.redstone_0"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.redstone_1"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.redstone_2"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.redstone_3")
    };

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final ResourceLocation MATERIAL_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "redstone");

    @Override
    public void onEquipmentChange(LivingEntity entity, int count) {
        if (entity.level().isClientSide()) return;
        updateModifiers(entity);
    }

    private void updateModifiers(LivingEntity entity) {
        AttributeInstance attrInstance = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attrInstance == null) return;

        int trimCount = 0;
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            attrInstance.removeModifier(SLOT_IDS[i]);
            ItemStack armorStack = entity.getItemBySlot(ARMOR_SLOTS[i]);
            if (!armorStack.isEmpty()) {
                ArmorTrim trim = armorStack.get(DataComponents.TRIM);
                if (trim != null && trim.material().unwrapKey().isPresent()
                        && trim.material().unwrapKey().get().location().equals(MATERIAL_ID)) {
                    trimCount++;
                }
            }
        }

        if (trimCount > 0) {
            // SPEED_BONUS.calc(count) 返回百分比（如 0.12 = 12%）
            // 乘以基础速度 0.1 得到绝对速度增量
            double baseSpeed = attrInstance.getBaseValue();
            double totalBonus = SPEED_BONUS.calc(trimCount) * baseSpeed;
            double perSlot = totalBonus / trimCount;
            int idx = 0;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack armorStack = entity.getItemBySlot(slot);
                if (!armorStack.isEmpty()) {
                    ArmorTrim trim = armorStack.get(DataComponents.TRIM);
                    if (trim != null && trim.material().unwrapKey().isPresent()
                            && trim.material().unwrapKey().get().location().equals(MATERIAL_ID)) {
                        attrInstance.addPermanentModifier(new AttributeModifier(
                                SLOT_IDS[idx], perSlot,
                                AttributeModifier.Operation.ADD_VALUE
                        ));
                    }
                }
                idx++;
            }
        }
    }
}
