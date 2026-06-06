package com.spectrum_reclamation.spectrum_reclamation.item.custom;

import com.spectrum_reclamation.spectrum_reclamation.block.LivingTrapBlock;
import net.minecraft.world.item.BlockItem;

/**
 * 活体陷阱物品 —— 可放置于地面的活体陷阱方块的物品形态。
 *
 * 继承 BlockItem，自动提供右键放置方块功能。
 * 放置时消耗 1 个物品（BlockItem 默认行为，无需额外逻辑）。
 *
 * 绑定关系：此物品通过构造器参数绑定 LivingTrapBlock，
 * 使得玩家手持此物品右键点击地面时，会在目标位置放置活体陷阱方块。
 *
 * 物品堆叠上限由 Item.Properties 控制（在 SRItems 注册时设置）。
 */
public class LivingTrapItem extends BlockItem {

    /**
     * 构造器 —— 绑定 LivingTrapBlock 并设置物品属性。
     *
     * @param block    绑定的活体陷阱方块实例
     * @param properties 物品属性（堆叠上限等）
     */
    public LivingTrapItem(LivingTrapBlock block, Properties properties) {
        super(block, properties);
    }
}
