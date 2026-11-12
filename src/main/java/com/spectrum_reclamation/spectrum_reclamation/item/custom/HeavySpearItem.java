package com.spectrum_reclamation.spectrum_reclamation.item.custom;

import com.spectrum_reclamation.spectrum_reclamation.registry.SRDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 沉重之矛物品 —— 陨星弩的专用弹药。
 *
 * 功能说明：
 * - 不是可投掷物品，而是作为弹药被陨星弩消耗来发射
 * - 右键无效果（继承 Item 默认行为，use() 不做任何处理）
 * - 最大堆叠 1（在 SRItems 注册时通过 Item.Properties.stacksTo(1) 设置）
 * - 支持墨水涂装：通过 SPEAR_COATING 数据组件存储颜色 ID
 *
 * 设计思路：
 * 沉重之矛作为弹药而非独立武器，本身不能投掷，
 * 必须搭配陨星弩使用。这种设计让陨星弩成为"重型弩"定位——
 * 消耗沉重之矛弹药，发射高伤害的 ThrownHeavySpear 实体。
 */
public class HeavySpearItem extends Item {

    /**
     * 构造器 —— 创建沉重之矛物品。
     *
     * @param properties 物品属性（在 SRItems 注册时设置 stacksTo(1)）
     */
    public HeavySpearItem(Properties properties) {
        super(properties);
    }

    /**
     * 获取矛的涂装颜色 ID。
     * 读取 SPEAR_COATING 数据组件，返回颜色字符串（如 "red"、"blue"）。
     * 未涂装时返回 null。
     *
     * @param stack 沉重之矛物品栈
     * @return 颜色 ID 字符串，未涂装返回 null
     */
    public static String getCoating(ItemStack stack) {
        return stack.get(SRDataComponents.SPEAR_COATING.get());
    }

    /**
     * 设置矛的涂装颜色。
     * 将颜色 ID 写入 SPEAR_COATING 数据组件。
     * 配方产出时由 potion_workshop_crafting 自动设置，此处仅供运行时动态修改使用。
     *
     * @param stack   沉重之矛物品栈
     * @param colorId 颜色 ID 字符串（如 "red"、"blue"），传 null 可清除涂装
     */
    public static void setCoating(ItemStack stack, String colorId) {
        if (colorId == null) {
            stack.remove(SRDataComponents.SPEAR_COATING.get());
        } else {
            stack.set(SRDataComponents.SPEAR_COATING.get(), colorId);
        }
    }

    // 不覆盖 use() 方法 —— 右键无效果，继承 Item 基类默认行为
}
