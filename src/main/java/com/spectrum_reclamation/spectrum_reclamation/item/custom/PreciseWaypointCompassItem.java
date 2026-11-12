package com.spectrum_reclamation.spectrum_reclamation.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 精准追溯指针 —— 追溯指针的升级版本，额外显示距离并支持吟唱传送。
 *
 * 继承 WaypointCompassItem，复用基础功能（指针指向、物品提示等）。
 * 额外功能：
 * - 物品提示文本中显示"约 N 格"距离信息
 * - 蹲下 + 长按右键 3 秒吟唱传送至记录坐标
 *
 * 传送机制：
 * - 使用 Minecraft 的"持续使用物品"系统：use() → startUsingItem → onUseTick → finishUsingItem
 * - getUseDuration 返回 60（3 秒 × 20 ticks/秒）
 * - finishUsingItem 在吟唱满 3 秒后触发传送
 * - onUseTick 每 20 ticks 播放递增音调的音效作为吟唱反馈
 * - 传送消耗 3 级经验，冷却 60 秒
 *
 * 合成配方：追溯指针 + 回响碎片 → 精准追溯指针（锻造台）
 */
public class PreciseWaypointCompassItem extends WaypointCompassItem {

    /**
     * 冷却追踪表 —— 记录每个玩家的传送冷却到期游戏刻。
     * 键：玩家 UUID，值：冷却到期的 gameTime tick。
     * 仅在服务端访问，无需并发安全。
     */
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    /** 冷却时长：60 秒 = 1200 ticks */
    private static final long COOLDOWN_TICKS = 1200;

    /** 吟唱时长：3 秒 = 60 ticks */
    private static final int USE_DURATION = 60;

    /**
     * 构造器 —— 创建精准追溯指针物品。
     *
     * @param properties 物品属性
     */
    public PreciseWaypointCompassItem(Properties properties) {
        super(properties);
    }

    // ==================== 传送相关：持续使用物品系统 ====================

    /**
     * 右键使用 —— 蹲下时尝试启动吟唱传送，非蹲下走父类逻辑。
     *
     * 精准指针的蹲下右键行为与父类不同：
     * - 父类（追溯指针）蹲下右键 → 记录当前位置
     * - 本类（精准指针）蹲下右键 → 吟唱传送（不记录位置）
     *
     * 前置检查（全部在 use() 中完成，失败立即返回）：
     * 1. 是否已记录坐标
     * 2. 是否同维度
     * 3. 是否在冷却中
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 蹲下 + 右键 → 尝试吟唱传送
        if (player.isShiftKeyDown()) {
            BlockPos waypointPos = getWaypointPos(stack);
            ResourceLocation waypointDim = getWaypointDimension(stack);

            // 检查：是否已记录位置
            if (waypointPos == null || waypointDim == null) {
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("spectrum_reclamation.waypoint.no_position"), true);
                }
                return InteractionResultHolder.fail(stack);
            }

            // 检查：是否跨维度
            if (!level.dimension().location().equals(waypointDim)) {
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("spectrum_reclamation.waypoint.cross_dimension_block"), true);
                }
                return InteractionResultHolder.fail(stack);
            }

            // 检查：是否在冷却中（比较当前游戏刻与冷却到期刻）
            Long cooldownEnd = COOLDOWNS.get(player.getUUID());
            if (cooldownEnd != null && level.getGameTime() < cooldownEnd) {
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("spectrum_reclamation.waypoint.cooldown"), true);
                }
                return InteractionResultHolder.fail(stack);
            }

            // 所有检查通过，开始吟唱（进入持续使用状态）
            player.startUsingItem(hand);

            // 提示玩家正在吟唱
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("spectrum_reclamation.waypoint.teleporting"), true);
            }

            return InteractionResultHolder.consume(stack);
        }

        // 非蹲下右键：交给父类处理（CompassItem 基类逻辑）
        return super.use(level, player, hand);
    }

    /**
     * 返回物品持续使用时长 —— 60 ticks（3 秒）。
     * Minecraft 以 20 ticks/秒 运行，60 ticks = 3 秒吟唱时间。
     */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    /**
     * 持续使用期间每 tick 调用 —— 播放递增音调的吟唱音效。
     *
     * 音调从 0.5 线性增长到 1.5（3 秒内），每 20 ticks（1 秒）播放一次。
     * 仅在服务端播放，由服务端同步给附近客户端。
     */
    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide) return;

        int elapsed = USE_DURATION - remainingUseDuration;

        // 每 20 ticks 播放一次音效（elapsed 为 0、20、40 时触发）
        if (elapsed % 20 == 0) {
            // 音调线性插值：progress 0.0 → 1.0，pitch 0.5 → 1.5
            float progress = (float) elapsed / USE_DURATION;
            float pitch = 0.5f + progress * 1.0f;
            level.playSound(null,
                    livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, pitch);
        }
    }

    /**
     * 吟唱完成 —— 传送玩家至记录坐标。
     *
     * 当 getUseDuration 返回的 60 ticks 全部耗尽时，Minecraft 自动调用此方法。
     * 这是"持续使用物品"系统的正常结束路径（非松手释放）。
     *
     * 传送流程：
     * 1. 仅服务端执行
     * 2. 传送到目标坐标（+0.5 偏移到方块中心，Y+1 避免卡进地面）
     * 3. 消耗 3 级经验
     * 4. 播放末影人传送音效
     * 5. 设置 60 秒冷却
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        // 仅服务端执行传送
        if (!level.isClientSide && livingEntity instanceof Player player) {
            BlockPos waypointPos = getWaypointPos(stack);
            ResourceLocation waypointDim = getWaypointDimension(stack);

            // 二次验证：坐标和维度（防止吟唱期间物品数据变更）
            if (waypointPos != null && waypointDim != null
                    && level.dimension().location().equals(waypointDim)) {

                // 传送到目标位置：X/Z 偏移 0.5 到方块中心，Y+1 避免卡进方块
                player.teleportTo(
                        waypointPos.getX() + 0.5,
                        waypointPos.getY() + 1,
                        waypointPos.getZ() + 0.5);

                // 消耗 3 级经验
                player.giveExperienceLevels(-3);

                // 播放末影人传送音效
                level.playSound(null,
                        player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);

                // 设置冷却：当前游戏刻 + 1200 ticks（60 秒）
                COOLDOWNS.put(player.getUUID(), level.getGameTime() + COOLDOWN_TICKS);

                // 记录物品使用统计
                player.awardStat(Stats.ITEM_USED.get(this));
            }
        }

        return stack;
    }

    /**
     * 提前松开右键 —— 中断吟唱，不传送。
     * 玩家在吟唱期间松开右键时调用，无需额外逻辑，仅阻止默认行为。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        // 吟唱中断，不做任何事（不传送）
    }

    // ==================== 物品提示文本 ====================

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
            // 使用翻译键和样式系统，避免把中文文本与颜色代码硬编码在 Java 代码中
            tooltip.add(Component.translatable("spectrum_reclamation.waypoint.precise_mode")
                    .withStyle(ChatFormatting.AQUA));
            // 距离信息由 SRClientEvents 中的 ItemTooltipEvent 在客户端动态添加
            // 因为 appendHoverText 没有 Player 参数，无法计算实时距离
        }
    }
}
