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
 * 下界合金纹饰效果处理器。
 *
 * 材料：minecraft:netherite
 * 效果：+1 击退抗性/件
 *
 * 通过 addAttributeModifier 向盔甲添加 KNOCKBACK_RESISTANCE 属性修饰器。
 * Attributes.KNOCKBACK_RESISTANCE 基础值为 0，使用 ADD_MULTIPLIED_BASE 操作：
 * - 1 件：+1.0 击退抗性（半免疫）
 * - 2 件：+2.0（完全免疫击退）
 * - 4 件：+4.0（完全免疫，有冗余）
 *
 * 击退抗性范围：0.0（正常击退）到 1.0（完全免疫），
 * 超过 1.0 的值视为完全免疫。所以 1 件即可显著减少击退。
 *
 * NeoForge 1.21.x 中使用 ResourceLocation 作为修饰器标识符。
 */
public class NetheriteTrimEffect implements TrimEffectHandler {

    /** 每件纹饰提供的击退抗性值 */
    private static final TrimCountedValue KNOCKBACK_BONUS = TrimCountedValue.linear(0.0, 1.0);

    /** 4 个盔甲槽位各自的固定 ResourceLocation 标识符 */
    private static final ResourceLocation[] SLOT_IDS = {
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.netherite_0"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.netherite_1"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.netherite_2"),
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.netherite_3")
    };

    /**
     * 每 tick 更新击退抗性属性修饰器。
     *
     * 仅在服务端操作属性，避免客户端属性重复叠加。
     * 当 count=0 时移除修饰器，恢复正常击退。
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  下界合金纹饰的盔甲件数（0-4）
     */
    @Override
    public void onTick(LivingEntity entity, int count) {
        if (entity.level().isClientSide()) {
            return;
        }

        double knockbackValue = KNOCKBACK_BONUS.calc(count);

        for (int i = 0; i < SLOT_IDS.length; i++) {
            AttributeInstance attrInstance = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (attrInstance == null) continue;

            if (count > 0) {
                // 添加或更新击退抗性修饰器
                // KNOCKBACK_RESISTANCE 基础值为 0，ADD_MULTIPLIED_BASE 使其叠加
                attrInstance.addPermanentModifier(new AttributeModifier(
                        SLOT_IDS[i],
                        knockbackValue,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ));
            } else {
                // 移除修饰器，恢复正常击退
                attrInstance.removeModifier(SLOT_IDS[i]);
            }
        }
    }
}
