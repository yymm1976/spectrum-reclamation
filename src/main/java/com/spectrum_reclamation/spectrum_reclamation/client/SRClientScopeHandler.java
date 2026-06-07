package com.spectrum_reclamation.spectrum_reclamation.client;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.item.custom.PreciseWaypointCompassItem;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * 客户端专属事件处理器 —— 处理瞄准镜 FOV 缩放和精准追溯指针距离显示。
 *
 * 为什么单独创建此类（不放在 SREventHandler 中）：
 * ComputeFovModifierEvent 是客户端专属事件类（net.neoforged.neoforge.client.event 包），
 * 如果在通用侧类（SREventHandler）中引用，服务端加载该类时会因找不到客户端类而崩溃
 * （NoClassDefFoundError）。因此必须放在 Dist.CLIENT 标记的客户端专属类中。
 *
 * 注册到 GAME_BUS（NeoForge.EVENT_BUS），使用 @EventBusSubscriber 自动注册。
 */
@EventBusSubscriber(modid = SpectrumReclamation.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class SRClientScopeHandler {

    /**
     * 监听 ComputeFovModifierEvent —— 修改 FOV 乘数实现瞄准镜缩放效果。
     *
     * ComputeFovModifierEvent 在每帧计算 FOV 时触发，位于客户端渲染线程。
     * 事件提供 getFovModifier()（原始 FOV 乘数）和 setNewFovModifier()（修改后的值）。
     *
     * 缩放原理：
     * - FOV 乘数 < 1.0 = 视野缩小（放大效果）
     * - FOV 乘数 > 1.0 = 视野扩大（速度效果等）
     * - 我们将乘数乘以 0.5，实现 2 倍放大效果
     *
     * @param event FOV 修饰事件
     */
    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();

        // 仅在玩家正在使用物品（拉弓/装弩）时生效
        if (!player.isUsingItem()) {
            return;
        }

        // 获取正在使用的物品
        ItemStack useItem = player.getUseItem();

        // 检查是否附着了瞄准镜
        if (useItem.has(SRDataComponents.SCOPE_ATTACHED.get())) {
            // 将当前 FOV 乘数乘以 0.5，实现缩小视野（模拟望远镜放大）
            // 例如：原版拉弓 FOV 轻微缩小 → 乘以 0.5 后进一步缩小 → 明显放大效果
            event.setNewFovModifier(event.getNewFovModifier() * 0.5f);
        }
    }

    /**
     * 监听 ItemTooltipEvent —— 为精准追溯指针添加距离信息。
     *
     * appendHoverText() 方法没有 Player 参数，无法计算实时距离。
     * ItemTooltipEvent 在 ItemStack.getTooltipLines() 中触发，
     * 提供了 Player 引用，可以在客户端安全地计算玩家到 waypoint 的距离。
     *
     * 距离计算：使用欧几里得距离（三维空间直线距离）。
     *
     * @param event 物品提示事件
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // 仅处理精准追溯指针
        if (!(stack.getItem() instanceof PreciseWaypointCompassItem)) {
            return;
        }

        // 获取玩家（启动时可能为 null）
        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        // 获取 waypoint 数据
        BlockPos waypointPos = stack.get(SRDataComponents.WAYPOINT_POS.get());
        ResourceLocation waypointDim = stack.get(SRDataComponents.WAYPOINT_DIMENSION.get());

        if (waypointPos == null || waypointDim == null) {
            return;
        }

        List<Component> tooltip = event.getToolTip();

        // 检查当前维度是否与 waypoint 维度匹配
        if (!player.level().dimension().location().equals(waypointDim)) {
            // 跨维度：显示"跨维度，距离未知"
            tooltip.add(Component.literal(
                    ChatFormatting.GOLD + "距离：跨维度，无法计算"
            ));
        } else {
            // 同维度：计算欧几里得距离
            Vec3 playerPos = player.position();
            Vec3 waypointVec = new Vec3(waypointPos.getX() + 0.5, waypointPos.getY() + 0.5, waypointPos.getZ() + 0.5);
            double distance = playerPos.distanceTo(waypointVec);

            // 四舍五入到整数格
            int roundedDistance = (int) Math.round(distance);

            tooltip.add(Component.literal(
                    ChatFormatting.GOLD + "距离：约 " + roundedDistance + " 格"
            ));
        }
    }
}
