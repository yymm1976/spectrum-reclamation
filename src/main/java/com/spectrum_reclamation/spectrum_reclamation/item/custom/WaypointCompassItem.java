package com.spectrum_reclamation.spectrum_reclamation.item.custom;

import com.spectrum_reclamation.spectrum_reclamation.registry.SRDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * 追溯指针物品 —— 可自定义坐标指向的指南针。
 *
 * 继承 CompassItem，利用其原版指南针指向逻辑。
 * 核心机制：
 * - 蹲下 + 右键：记录当前玩家的位置和维度到数据组件
 * - 指针方向：指向记录的 waypoint 坐标（同维度时）
 * - 跨维度：指针随机旋转（不工作）
 *
 * 数据组件：
 * - WAYPOINT_POS：记录的目标坐标（BlockPos）
 * - WAYPOINT_DIMENSION：记录的目标维度（ResourceLocation）
 *
 * 渲染原理：CompassItem 的指针渲染依赖 LODESTONE_TRACKER 数据组件。
 * 原版 CompassItemPropertyFunction 通过 CompassTarget 读取 LODESTONE_TRACKER
 * 获取 GlobalPos，然后计算指针旋转角度。我们在 inventoryTick 中同步
 * LODESTONE_TRACKER 与自定义 waypoint 数据，复用原版渲染逻辑，无需额外客户端代码。
 */
public class WaypointCompassItem extends CompassItem {

    /**
     * 构造器 —— 创建追溯指针物品。
     *
     * @param properties 物品属性
     */
    public WaypointCompassItem(Properties properties) {
        super(properties);
    }

    /**
     * 右键使用物品时调用。
     * 蹲下 + 右键：记录当前玩家位置和维度为 waypoint。
     * 普通右键：不做特殊处理（交给 CompassItem 基类）。
     *
     * @param level  世界
     * @param player 使用物品的玩家
     * @param hand   使用的手
     * @return 交互结果
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // 蹲下 + 右键 → 记录当前位置为 waypoint
        if (player.isShiftKeyDown()) {
            BlockPos currentPos = player.blockPosition();
            ResourceLocation currentDimension = level.dimension().location();

            // 写入自定义数据组件：记录坐标和维度
            itemStack.set(SRDataComponents.WAYPOINT_POS.get(), currentPos);
            itemStack.set(SRDataComponents.WAYPOINT_DIMENSION.get(), currentDimension);

            // 同步 LODESTONE_TRACKER，让原版指南针渲染器指向目标坐标
            // 原版渲染器读取此组件中的 GlobalPos 来计算指针旋转角度
            LodestoneTracker tracker = new LodestoneTracker(
                    Optional.of(GlobalPos.of(level.dimension(), currentPos)),
                    true // trackDistance：始终追踪
            );
            itemStack.set(DataComponents.LODESTONE_TRACKER, tracker);

            // 播放磁石锁定音效
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 记录使用统计
            player.awardStat(Stats.ITEM_USED.get(this));

            // 在 action bar 提示已记录位置（仅服务端发送，避免重复）
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.spectrum_reclamation.waypoint_recorded",
                                currentPos.getX(), currentPos.getY(), currentPos.getZ()),
                        true
                );
            }

            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
        }

        // 非蹲下右键：交给基类
        return super.use(level, player, hand);
    }

    /**
     * 物品每 tick 在背包中时调用。
     * 覆写此方法以同步 LODESTONE_TRACKER 与自定义 waypoint 数据。
     *
     * 关键：不调用 super.inventoryTick()，因为原版 CompassItem 的 inventoryTick
     * 会调用 LodestoneTracker.tick()，该方法检查磁石方块是否存在，不存在则重置追踪器。
     * 我们的 waypoint 不是磁石方块，所以必须跳过这个检查。
     *
     * @param stack      物品栈
     * @param level      世界
     * @param entity     持有者
     * @param itemSlot   背包槽位
     * @param isSelected 是否在主手选中
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int itemSlot, boolean isSelected) {
        BlockPos waypointPos = stack.get(SRDataComponents.WAYPOINT_POS.get());
        ResourceLocation waypointDim = stack.get(SRDataComponents.WAYPOINT_DIMENSION.get());

        if (waypointPos != null && waypointDim != null) {
            // 将 ResourceLocation 维度标识转换为 ResourceKey<Level>
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, waypointDim);

            // 同维度时：设置 LODESTONE_TRACKER 指向 waypoint
            if (level.dimension().equals(dimension)) {
                LodestoneTracker tracker = new LodestoneTracker(
                        Optional.of(GlobalPos.of(dimension, waypointPos)),
                        true
                );
                stack.set(DataComponents.LODESTONE_TRACKER, tracker);
            } else {
                // 跨维度：移除 LODESTONE_TRACKER，指针随机旋转
                stack.remove(DataComponents.LODESTONE_TRACKER);
            }
        }
    }

    /**
     * 物品提示文本 —— 鼠标悬停时显示 waypoint 信息。
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        BlockPos waypointPos = stack.get(SRDataComponents.WAYPOINT_POS.get());
        ResourceLocation waypointDim = stack.get(SRDataComponents.WAYPOINT_DIMENSION.get());

        if (waypointPos != null && waypointDim != null) {
            // 显示目标坐标
            tooltip.add(Component.literal(
                    ChatFormatting.GRAY + "目标坐标：("
                            + waypointPos.getX() + ", " + waypointPos.getY() + ", " + waypointPos.getZ() + ")"
            ));
            // 显示目标维度
            tooltip.add(Component.literal(
                    ChatFormatting.GRAY + "维度：" + waypointDim
            ));
        } else {
            // 未设置 waypoint 时显示操作提示
            tooltip.add(Component.translatable("tooltip.spectrum_reclamation.waypoint_compass.empty"));
        }
    }

    /**
     * 判断是否显示附魔光效。
     * 当有 waypoint 数据时显示光效，便于区分已设定目标的指针。
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(SRDataComponents.WAYPOINT_POS.get()) || super.isFoil(stack);
    }

    /**
     * 供子类 PreciseWaypointCompassItem 获取 waypoint 坐标。
     */
    protected BlockPos getWaypointPos(ItemStack stack) {
        return stack.get(SRDataComponents.WAYPOINT_POS.get());
    }

    /**
     * 供子类获取 waypoint 维度。
     */
    protected ResourceLocation getWaypointDimension(ItemStack stack) {
        return stack.get(SRDataComponents.WAYPOINT_DIMENSION.get());
    }
}
