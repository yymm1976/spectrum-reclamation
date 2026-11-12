package com.spectrum_reclamation.spectrum_reclamation.item.custom;

import com.spectrum_reclamation.spectrum_reclamation.entity.BlazingBombEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 炽光炸弹物品 —— 可投掷的炽光炸弹。
 *
 * 右键使用时发射 BlazingBombEntity 弹射物。
 * 弹射物着弹后在半径 8 格内施加发光效果，并对亡灵生物附加燃烧。
 *
 * 使用模式：右键即投掷（无需蓄力），与雪球/经验瓶相同。
 * 最大堆叠 16 个（在 Item.Properties 中设置）。
 *
 * 动画：使用 UseAnim.NONE（Item 基类默认值），无特殊使用动画。
 * 投掷动作通过音效和弹射物生成来表现，无需额外动画。
 */
public class BlazingBombItem extends Item {

    public BlazingBombItem(Properties properties) {
        super(properties);
    }

    /**
     * 右键使用物品时调用。
     *
     * 执行流程：
     * 1. 播放投掷音效（雪球投掷音效，服务端和客户端都会播放）
     * 2. 在服务端创建 BlazingBombEntity 并发射
     * 3. 非创造模式玩家消耗 1 个物品
     * 4. 返回 sidedSuccess（客户端返回 SUCCESS，服务端返回 CONSUME）
     *
     * shootFromRotation 参数说明：
     * - player.getXRot() / getYRot()：玩家当前视角角度
     * - 0.0F：无额外俯仰偏移
     * - 1.5F：发射速度（雪球为 1.5F）
     * - 1.0F：散射度（1.0 = 中等偏移，模拟投掷手感）
     *
     * @param level  世界
     * @param player 使用物品的玩家
     * @param hand   使用的手（主手/副手）
     * @return 交互结果
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // 播放投掷音效（使用雪球音效，音量 0.5，音调随机偏移模拟手感）
        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW,
                SoundSource.NEUTRAL,
                0.5F,
                0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
        );

        // 仅在服务端创建和发射弹射物（避免客户端重复生成）
        if (!level.isClientSide) {
            BlazingBombEntity bomb = new BlazingBombEntity(level, player);
            bomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(bomb);
        }

        // 记录使用统计
        player.awardStat(Stats.ITEM_USED.get(this));

        // 非创造模式玩家消耗 1 个物品
        if (!player.getAbilities().instabuild) {
            itemStack.shrink(1);
        }

        // sidedSuccess：客户端返回 SUCCESS（触发动画），服务端返回 CONSUME（触发消耗）
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
    }
}
