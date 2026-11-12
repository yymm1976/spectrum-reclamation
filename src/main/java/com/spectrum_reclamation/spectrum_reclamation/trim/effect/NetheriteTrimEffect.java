package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 下界合金纹饰效果处理器。
 *
 * 材料：minecraft:netherite
 * 效果：+0.25 击退抗性/件
 *
 * 击退抗性范围：0.0（正常击退）到 1.0（完全免疫）。
 * 使用 ADD_VALUE 操作直接叠加击退抗性值。
 *
 * 仅在装备变化时更新属性修饰器。
 */
public class NetheriteTrimEffect extends AbstractAttributeTrimEffect {

    /** 每件纹饰提供的击退抗性 */
    private static final TrimCountedValue KNOCKBACK_BONUS = TrimCountedValue.linear(0.0, 0.25);

    @Override
    protected Holder<Attribute> getAttribute() {
        return Attributes.KNOCKBACK_RESISTANCE;
    }

    @Override
    protected ResourceLocation getModifierIdPrefix() {
        return ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.netherite");
    }

    @Override
    protected TrimCountedValue getCountedValue() {
        return KNOCKBACK_BONUS;
    }

    @Override
    protected AttributeModifier.Operation getOperation() {
        return AttributeModifier.Operation.ADD_VALUE;
    }

    @Override
    protected ResourceLocation getMaterialId() {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "netherite");
    }
}
