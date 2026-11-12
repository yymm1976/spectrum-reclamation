package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

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
 * 特殊处理：覆写 calculatePerSlotValue，
 * 将百分比值乘以基础速度后再均分到每个槽位。
 */
public class RedstoneTrimEffect extends AbstractAttributeTrimEffect {

    /** 每件纹饰提供的速度百分比（0.03 = 3%） */
    private static final TrimCountedValue SPEED_BONUS = TrimCountedValue.linear(0.0, 0.03);

    @Override
    protected Holder<Attribute> getAttribute() {
        return Attributes.MOVEMENT_SPEED;
    }

    @Override
    protected ResourceLocation getModifierIdPrefix() {
        return ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.redstone");
    }

    @Override
    protected TrimCountedValue getCountedValue() {
        return SPEED_BONUS;
    }

    @Override
    protected AttributeModifier.Operation getOperation() {
        return AttributeModifier.Operation.ADD_VALUE;
    }

    @Override
    protected ResourceLocation getMaterialId() {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "redstone");
    }

    /**
     * 覆写：将百分比奖励乘以基础速度值后再均分。
     * 例如 4 件时：0.12（百分比总和） × 0.1（基础速度） ÷ 4 = 0.003/槽位。
     */
    @Override
    protected double calculatePerSlotValue(double totalBonus, int trimCount, AttributeInstance attrInstance) {
        return totalBonus * attrInstance.getBaseValue() / trimCount;
    }
}
