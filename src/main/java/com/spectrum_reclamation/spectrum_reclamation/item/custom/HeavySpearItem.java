package com.spectrum_reclamation.spectrum_reclamation.item.custom;

import net.minecraft.world.item.Item;

/**
 * 沉重之矛物品 —— 陨星弩的专用弹药。
 *
 * 功能说明：
 * - 不是可投掷物品，而是作为弹药被陨星弩消耗来发射
 * - 右键无效果（继承 Item 默认行为，use() 不做任何处理）
 * - 最大堆叠 1（在 SRItems 注册时通过 Item.Properties.stacksTo(1) 设置）
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

    // 不覆盖 use() 方法 —— 右键无效果，继承 Item 基类默认行为
}
