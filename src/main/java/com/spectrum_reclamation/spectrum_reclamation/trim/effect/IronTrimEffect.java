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
 * 铁锭纹饰效果处理器。
 *
 * 材料：minecraft:iron
 * 效果：+0.5 盔甲值/件
 *
 * 通过 addAttributeModifier 向盔甲添加 ARMOR 属性修饰器。
 * Attributes.ARMOR 基础值为 0，使用 ADD_MULTIPLIED_BASE 操作：
 * - 1 件：0 + 1.0 × 0.5 = +0.5 盔甲
 * - 2 件：+1.0 盔甲
 * - 4 件：+2.0 盔甲
 *
 * NeoForge 1.21.x 中 AttributeModifier 使用 ResourceLocation 而非 UUID 作为标识符，
 * 每个盔甲槽位使用不同的 ResourceLocation（防止合并计算错误）。
 */
public class IronTrimEffect implements TrimEffectHandler {

    /** 每件纹饰提供的盔甲值 */
    private static final TrimCountedValue ARMOR_BONUS = TrimCountedValue.linear(0.0, 0.5);

    /** 4 个盔甲槽位各自的固定 ResourceLocation 标识符，防止同一实体上多个槽位的修饰器互相覆盖 */
    private static final ResourceLocation[] SLOT_IDS = {
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.iron_0"), // 头盔
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.iron_1"), // 胸甲
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.iron_2"), // 护腿
            ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "trim.iron_3")  // 靴子
    };

    /**
     * 每 tick 更新盔甲属性修饰器。
     *
     * 根据铁锭纹饰件数动态调整 ARMOR 属性值。
     * 仅在服务端执行，避免客户端属性重复叠加。
     *
     * NeoForge 1.21.x 属性修饰器原理：
     * - AttributeInstance 存储某个属性在实体上的所有修饰器
     * - 每个修饰器有固定 ResourceLocation 标识符，同一 ID 会被替换而非叠加
     * - 当 count=0 时移除修饰器，恢复原版属性值
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  铁锭纹饰的盔甲件数（0-4）
     */
    @Override
    public void onTick(LivingEntity entity, int count) {
        // 仅在服务端操作属性，避免客户端重复叠加
        if (entity.level().isClientSide()) {
            return;
        }

        // 计算当前纹饰件数对应的总盔甲加成
        double armorValue = ARMOR_BONUS.calc(count);

        for (int i = 0; i < SLOT_IDS.length; i++) {
            AttributeInstance attrInstance = entity.getAttribute(Attributes.ARMOR);
            if (attrInstance == null) continue;

            if (count > 0) {
                // count > 0 时，添加或更新属性修饰器
                // ADD_MULTIPLIED_BASE：基于属性基础值进行乘法加算
                // ARMOR 基础值为 0，此处用 ADD_MULTIPLIED_BASE + value=0.5 来叠加
                // 实际效果：每件 +0.5 盔甲值
                attrInstance.addPermanentModifier(new AttributeModifier(
                        SLOT_IDS[i],
                        armorValue,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ));
            } else {
                // count=0 时移除修饰器，恢复原版属性值
                attrInstance.removeModifier(SLOT_IDS[i]);
            }
        }
    }
}
