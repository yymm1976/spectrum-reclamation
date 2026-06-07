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
 * 海龟鳞甲纹饰效果处理器。
 *
 * 材料：minecraft:turtle
 * 效果：游泳速度 +15%/件
 *
 * 通过 addAttributeModifier 向盔甲添加 WATER_MOVEMENT_EFFICIENCY 属性修饰器。
 * Attributes.WATER_MOVEMENT_EFFICIENCY 基础值为 1.0，使用 ADD_MULTIPLIED_BASE 操作：
 * - 1 件：1.0 + 1.0 × 0.15 = 1.15（+15% 水中效率）
 * - 2 件：1.30（+30%）
 * - 4 件：1.60（+60%）
 *
 * WATER_MOVEMENT_EFFICIENCY 控制实体在水中的移动速度衰减程度：
 * - 值为 1.0 时为正常水中速度
 * - 值越高，水中速度越快（接近陆地速度）
 * - 超过 1.0 的部分直接加速水中移动
 *
 * NeoForge 1.21.x 中使用 ResourceLocation 作为修饰器标识符。
 */
public class TurtleTrimEffect implements TrimEffectHandler {

    /** 每件纹饰提供的水中移动效率加成百分比 */
    private static final TrimCountedValue WATER_SPEED_BONUS = TrimCountedValue.linear(0.0, 0.15);

    /** 4 个盔甲槽位各自的固定 ResourceLocation 标识符 */
    private static final ResourceLocation[] SLOT_IDS = {
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.turtle_0"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.turtle_1"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.turtle_2"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.turtle_3")
    };

    /**
     * 每 tick 更新水中移动效率属性修饰器。
     *
     * 仅在服务端操作属性，避免客户端属性重复叠加。
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  海龟鳞甲纹饰的盔甲件数（0-4）
     */
    @Override
    public void onTick(LivingEntity entity, int count) {
        if (entity.level().isClientSide()) {
            return;
        }

        // 计算水中效率加成百分比
        double waterSpeedValue = WATER_SPEED_BONUS.calc(count);

        for (int i = 0; i < SLOT_IDS.length; i++) {
            AttributeInstance attrInstance = entity.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
            if (attrInstance == null) continue;

            if (count > 0) {
                // 添加或更新水中移动效率修饰器
                // WATER_MOVEMENT_EFFICIENCY 基础值为 1.0，ADD_MULTIPLIED_BASE 使其按比例叠加
                // +15% × count 意味着 4 件时水中效率为基础值的 160%
                attrInstance.addPermanentModifier(new AttributeModifier(
                        SLOT_IDS[i],
                        waterSpeedValue,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ));
            } else {
                // 移除修饰器，恢复原版水中速度
                attrInstance.removeModifier(SLOT_IDS[i]);
            }
        }
    }
}
