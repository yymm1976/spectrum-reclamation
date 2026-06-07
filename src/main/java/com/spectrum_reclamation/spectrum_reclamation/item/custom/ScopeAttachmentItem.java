package com.spectrum_reclamation.spectrum_reclamation.item.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 瞄准镜物品 —— 可附加到弓/弩上的瞄准镜附件。
 *
 * 功能：
 * - 最大堆叠 1（附件类物品不可堆叠）
 * - 通过右键交互将瞄准镜附着到弓/弩上（在 SREventHandler 中处理）
 * - 附着后弓/弩获得 scope_attached 数据组件
 * - 附着效果：拉弓时 FOV 缩小（模拟望远镜视野），箭矢重力降低（弹道更直）
 *
 * 合成配方：望远镜 + 铜锭 + 紫水晶碎片 → 瞄准镜（工作台无序合成）
 */
public class ScopeAttachmentItem extends Item {

    /**
     * 构造器 —— 创建瞄准镜物品。
     *
     * @param properties 物品属性（在 SRItems 注册时设置 stacksTo(1)）
     */
    public ScopeAttachmentItem(Properties properties) {
        super(properties);
    }

    /**
     * 物品提示文本 —— 鼠标悬停时显示使用说明。
     * 显示如何将瞄准镜附着到弓/弩上。
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // 显示使用提示：副手持瞄准镜 + 主手弓/弩右键即可附着
        tooltip.add(Component.translatable("tooltip.spectrum_reclamation.scope_attachment"));
    }
}
