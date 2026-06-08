package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 海龟鳞甲纹饰效果处理器。
 *
 * 材料：minecraft:turtle
 * 效果：游泳速度 +15%/件
 *
 * WATER_MOVEMENT_EFFICIENCY 基础值为 1.0，使用 ADD_VALUE 直接叠加：
 * - 1 件：+0.15
 * - 4 件：+0.60
 *
 * 仅在装备变化时更新属性修饰器。
 */
public class TurtleTrimEffect extends AbstractAttributeTrimEffect {

    /** 每件纹饰提供的游泳速度增量 */
    private static final TrimCountedValue WATER_SPEED_BONUS = TrimCountedValue.linear(0.0, 0.15);

    @Override
    protected Holder<Attribute> getAttribute() {
        return Attributes.WATER_MOVEMENT_EFFICIENCY;
    }

    @Override
    protected ResourceLocation getModifierIdPrefix() {
        return ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.turtle");
    }

    @Override
    protected TrimCountedValue getCountedValue() {
        return WATER_SPEED_BONUS;
    }

    @Override
    protected AttributeModifier.Operation getOperation() {
        return AttributeModifier.Operation.ADD_VALUE;
    }

    @Override
    protected ResourceLocation getMaterialId() {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "turtle");
    }
}
