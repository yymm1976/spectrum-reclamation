package com.spectrum_reclamation.spectrum_reclamation.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 精准追溯指针 —— 追溯指针的升级版本，额外显示到目标的距离。
 *
 * 继承 WaypointCompassItem，复用所有基础功能（蹲下右键记录坐标、指针指向等）。
 * 额外功能：
 * - 物品提示文本中显示"约 N 格"距离信息
 * - 距离由客户端事件处理器（SRClientEvents）通过 ItemTooltipEvent 动态计算并添加
 *
 * 距离计算原理：
 * - 需要玩家当前位置（仅客户端可获取 Minecraft.getInstance().player）
 * - 使用 player.position().distanceTo(Vec3) 计算欧几里得距离
 * - 跨维度时距离无法计算（显示"跨维度"）
 *
 * 合成配方：追溯指针 + 回响碎片 → 精准追溯指针（锻造台）
 */
public class PreciseWaypointCompassItem extends WaypointCompassItem {

    /**
     * 构造器 —— 创建精准追溯指针物品。
     *
     * @param properties 物品属性
     */
    public PreciseWaypointCompassItem(Properties properties) {
        super(properties);
    }

    /**
     * 物品提示文本 —— 显示 waypoint 信息（距离由客户端事件动态添加）。
     *
     * 精准版与普通版的区别：
     * - 普通版只显示坐标和维度
     * - 精准版额外显示距离（由 SRClientEvents 中的 ItemTooltipEvent 处理）
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        // 精准版显示额外的"精准"标识
        BlockPos waypointPos = getWaypointPos(stack);
        if (waypointPos != null) {
            // 使用翻译键替代硬编码中文，支持多语言
            tooltip.add(Component.translatable(
                    "spectrum_reclamation.waypoint.precise_mode"
            ));
            // 距离信息由 SRClientEvents 中的 ItemTooltipEvent 在客户端动态添加
            // 因为 appendHoverText 没有 Player 参数，无法计算实时距离
        }
    }
}
