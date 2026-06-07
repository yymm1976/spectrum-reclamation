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
 * 铁锭纹饰效果处理器。
 *
 * 材料：minecraft:iron
 * 效果：+0.5 盔甲值/件
 *
 * 使用 ADD_VALUE 操作直接叠加盔甲值：
 * - 1 件：+0.5 盔甲
 * - 2 件：+1.0 盔甲
 * - 4 件：+2.0 盔甲
 *
 * 仅在装备变化时更新属性修饰器（onEquipmentChange），
 * 避免每 tick 重复操作属性系统。
 */
public class IronTrimEffect implements TrimEffectHandler {

    /** 每件纹饰提供的盔甲值 */
    private static final TrimCountedValue ARMOR_BONUS = TrimCountedValue.linear(0.0, 0.5);

    /** 4 个盔甲槽位各自的固定 ResourceLocation 标识符 */
    private static final ResourceLocation[] SLOT_IDS = {
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.iron_0"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.iron_1"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.iron_2"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.iron_3")
    };

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final ResourceLocation IRON_MATERIAL_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "iron");

    @Override
    public void onEquipmentChange(LivingEntity entity, int count) {
        if (entity.level().isClientSide()) return;
        updateArmorModifiers(entity);
    }

    private void updateArmorModifiers(LivingEntity entity) {
        AttributeInstance attrInstance = entity.getAttribute(Attributes.ARMOR);
        if (attrInstance == null) return;

        int trimCount = 0;
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            // 先移除旧修饰器
            attrInstance.removeModifier(SLOT_IDS[i]);

            // 检查该槽位是否有铁纹饰
            ItemStack armorStack = entity.getItemBySlot(ARMOR_SLOTS[i]);
            if (!armorStack.isEmpty()) {
                ArmorTrim trim = armorStack.get(DataComponents.TRIM);
                if (trim != null && trim.material().unwrapKey().isPresent()
                        && trim.material().unwrapKey().get().location().equals(IRON_MATERIAL_ID)) {
                    trimCount++;
                }
            }
        }

        if (trimCount > 0) {
            // 总盔甲值 = 0.5 × 件数，均分到每个有纹饰的槽位
            double totalArmor = ARMOR_BONUS.calc(trimCount);
            double perSlotValue = totalArmor / trimCount;
            int idx = 0;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack armorStack = entity.getItemBySlot(slot);
                if (!armorStack.isEmpty()) {
                    ArmorTrim trim = armorStack.get(DataComponents.TRIM);
                    if (trim != null && trim.material().unwrapKey().isPresent()
                            && trim.material().unwrapKey().get().location().equals(IRON_MATERIAL_ID)) {
                        attrInstance.addPermanentModifier(new AttributeModifier(
                                SLOT_IDS[idx], perSlotValue,
                                AttributeModifier.Operation.ADD_VALUE
                        ));
                    }
                }
                idx++;
            }
        }
    }
}
