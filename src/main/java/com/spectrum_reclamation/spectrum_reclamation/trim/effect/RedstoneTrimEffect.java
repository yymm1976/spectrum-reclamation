package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimCountedValue;
import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 红石粉纹饰效果处理器。
 *
 * 材料：minecraft:redstone
 * 效果：移动速度 +3%/件
 *
 * 通过 addAttributeModifier 向盔甲添加 MOVEMENT_SPEED 属性修饰器。
 * Attributes.MOVEMENT_SPEED 基础值为 0.1，使用 ADD_MULTIPLIED_BASE 操作：
 * - 1 件：0.1 + 0.1 × 0.03 = 0.103（+3% 基础速度）
 * - 2 件：0.106（+6%）
 * - 4 件：0.112（+12%）
 *
 * NeoForge 1.21.x 中使用 ResourceLocation 作为修饰器标识符。
 */
public class RedstoneTrimEffect implements TrimEffectHandler {

    /** 每件纹饰提供的速度加成百分比（基于属性基础值） */
    private static final TrimCountedValue SPEED_BONUS = TrimCountedValue.linear(0.0, 0.03);

    /** 4 个盔甲槽位各自的固定 ResourceLocation 标识符 */
    private static final ResourceLocation[] SLOT_IDS = {
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.redstone_0"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.redstone_1"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.redstone_2"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.redstone_3")
    };

    /**
     * 每 tick 更新移动速度属性修饰器。
     *
     * 仅在服务端操作属性，避免客户端属性重复叠加。
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  红石粉纹饰的盔甲件数（0-4）
     */
    @Override
    public void onTick(LivingEntity entity, int count) {
        if (entity.level().isClientSide()) {
            return;
        }

        // 计算速度加成百分比
        double speedValue = SPEED_BONUS.calc(count);

        for (int i = 0; i < SLOT_IDS.length; i++) {
            AttributeInstance attrInstance = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attrInstance == null) continue;

            if (count > 0) {
                // 添加或更新移动速度修饰器
                // MOVEMENT_SPEED 基础值为 0.1，ADD_MULTIPLIED_BASE 使其按比例叠加
                // +3% × count 意味着 4 件时总加成为基础速度的 12%
                attrInstance.addPermanentModifier(new AttributeModifier(
                        SLOT_IDS[i],
                        speedValue,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ));
            } else {
                // 移除修饰器，恢复原版移动速度
                attrInstance.removeModifier(SLOT_IDS[i]);
            }
        }
    }
}
