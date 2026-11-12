package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

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
public class IronTrimEffect extends AbstractAttributeTrimEffect {

    /** 每件纹饰提供的盔甲值 */
    private static final TrimCountedValue ARMOR_BONUS = TrimCountedValue.linear(0.0, 0.5);

    @Override
    protected Holder<Attribute> getAttribute() {
        return Attributes.ARMOR;
    }

    @Override
    protected ResourceLocation getModifierIdPrefix() {
        return ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.iron");
    }

    @Override
    protected TrimCountedValue getCountedValue() {
        return ARMOR_BONUS;
    }

    @Override
    protected AttributeModifier.Operation getOperation() {
        return AttributeModifier.Operation.ADD_VALUE;
    }

    @Override
    protected ResourceLocation getMaterialId() {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "iron");
    }
}
