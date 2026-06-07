package com.spectrum_reclamation.spectrum_reclamation.item.custom;

import com.spectrum_reclamation.spectrum_reclamation.entity.ThrownHeavySpear;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRDataComponents;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 陨星弩物品 —— 继承 CrossbowItem 的重型弩。
 *
 * 与普通弩的区别：
 * 1. 仅接受沉重之矛（HeavySpearItem）作为弹药，不接受普通箭矢
 * 2. 装填速度比普通弩慢 2 倍（getUseDuration 返回 50，普通弩为 25）
 * 3. 自带瞄准镜效果（scope_attached 数据组件，触发 FOV 缩放）
 * 4. 发射 ThrownHeavySpear 实体（高伤害、强击退、钉穿机制）
 *
 * 实现思路：
 * 由于 CrossbowItem 内部的弹药选择逻辑（getProjectile）是 private 的，
 * 我们无法直接覆盖。因此完全重写 use()、releaseUsing() 和 onUseTick()，
 * 自行管理装填和发射流程，同时复用 CrossbowItem 的静态工具方法
 * （isCharged、setCharged、addChargedProjectiles 等）来管理装填状态。
 *
 * 注意：CrossbowItem 的 getProjectile()、loadProjectile() 等方法为 private，
 * 无法在子类中直接覆盖。我们通过重写公开方法来实现自定义弹药逻辑。
 */
public class MeteorCrossbowItem extends CrossbowItem {

    /**
     * 构造器 —— 创建陨星弩物品。
     *
     * @param properties 物品属性（在 SRItems 注册时设置）
     */
    public MeteorCrossbowItem(Properties properties) {
        super(properties);
    }

    // ==================== 使用流程（右键交互） ====================

    /**
     * 右键使用物品时调用。
     *
     * 流程：
     * 1. 确保 scope_attached 数据组件存在（首次使用时自动设置）
     * 2. 如果已装填 → 发射 ThrownHeavySpear → 清除装填状态
     * 3. 如果未装填且背包有沉重之矛 → 开始装填蓄力
     * 4. 如果没有弹药 → 返回失败
     *
     * @param level  世界
     * @param player 使用物品的玩家
     * @param hand   使用的手（主手/副手）
     * @return 交互结果
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack crossbowStack = player.getItemInHand(hand);

        // === 自带瞄准镜效果 ===
        // 确保 scope_attached 数据组件存在，触发客户端 FOV 缩放
        // 首次使用时设置，之后一直保留
        if (!crossbowStack.has(SRDataComponents.SCOPE_ATTACHED.get())) {
            crossbowStack.set(SRDataComponents.SCOPE_ATTACHED.get(), true);
        }

        // === 已装填 → 发射 ===
        if (CrossbowItem.isCharged(crossbowStack)) {
            // 仅在服务端创建和发射弹射物（避免客户端重复生成）
            if (!level.isClientSide()) {
                // 从弩上读取装填时保存的涂装数据
                // ChargedProjectiles 不保留数据组件，所以涂装数据单独存储在弩上
                String savedCoating = crossbowStack.get(SRDataComponents.SPEAR_COATING.get());
                ItemStack spearStack;
                if (savedCoating != null) {
                    // 有涂装数据：创建带涂装的矛物品栈
                    spearStack = new ItemStack(SRItems.HEAVY_SPEAR.get());
                    spearStack.set(SRDataComponents.SPEAR_COATING.get(), savedCoating);
                    // 清除弩上的临时涂装数据（已转移到弹射物上）
                    crossbowStack.remove(SRDataComponents.SPEAR_COATING.get());
                } else {
                    // 无涂装：普通矛
                    spearStack = new ItemStack(SRItems.HEAVY_SPEAR.get());
                }

                // 创建沉重之矛弹射物实体（传入带涂装的物品栈）
                // ThrownHeavySpear 的三参数构造器会将涂装数据传递给 pickupItem
                ThrownHeavySpear spear = new ThrownHeavySpear(level, player, spearStack);
                // shootFromRotation 参数：投射者、俯仰角、偏航角、偏移、速度、散射
                // 速度 3.0F 比雪球（1.5F）快，适合重型弩的射击感
                spear.shootFromRotation(player, player.getXRot(), player.getYRot(),
                        0.0F, 3.0F, 1.0F);
                level.addFreshEntity(spear);

                // 清除装填状态（通过 DataComponents 直接操作 ChargedProjectiles 组件）
                crossbowStack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            }

            // 播放射击音效（弩射击声，客户端和服务端都播放）
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 记录使用统计
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(crossbowStack, level.isClientSide());
        }

        // === 未装填 → 检查弹药并开始装填 ===
        if (!findHeavySpear(player).isEmpty()) {
            // 背包有沉重之矛，开始装填蓄力（播放拉弓动画）
            player.startUsingItem(hand);
            return InteractionResultHolder.sidedSuccess(crossbowStack, level.isClientSide());
        }

        // 没有弹药，无法使用
        return InteractionResultHolder.fail(crossbowStack);
    }

    // ==================== 装填释放逻辑 ====================

    /**
     * 玩家释放右键（松开按键）时调用。
     *
     * 蓄力完成判定：已蓄力 ticks >= 装填所需 ticks（50）。
     * 普通弩只需 25 ticks，陨星弩需要 50 ticks（慢 2 倍），
     * 体现"重型弩"的装填手感。
     *
     * @param stack     陨星弩物品栈
     * @param level     世界
     * @param shooter   使用者（通常是玩家）
     * @param timeLeft  剩余使用时间 ticks（从 getUseDuration 递减到 0）
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity shooter, int timeLeft) {
        if (!(shooter instanceof Player player)) {
            return;
        }

        int chargeDuration = this.getUseDuration(stack, shooter);
        int chargedTicks = chargeDuration - timeLeft;

        // 蓄力时间足够（>= chargeDuration），才能装填
        if (chargedTicks >= chargeDuration) {
            // 在背包中查找沉重之矛
            ItemStack ammo = findHeavySpear(player);

            if (!ammo.isEmpty()) {
                // 装填成功：通过 DataComponents 设置已装填的弹药
                // ChargedProjectiles 存储在 DataComponents.CHARGED_PROJECTILES 中，
                // isCharged() 检查此组件是否非空
                stack.set(DataComponents.CHARGED_PROJECTILES,
                        ChargedProjectiles.of(List.of(new ItemStack(SRItems.HEAVY_SPEAR.get()))));

                // === 保存涂装数据到弩上 ===
                // ChargedProjectiles 不保留数据组件，所以涂装数据需要单独存储
                // 在发射时从弩上读取，转移到弹射物上
                String coating = HeavySpearItem.getCoating(ammo);
                if (coating != null) {
                    stack.set(SRDataComponents.SPEAR_COATING.get(), coating);
                }

                // 消耗弹药（非创造模式下从背包移除 1 个）
                if (!player.getAbilities().instabuild) {
                    ammo.shrink(1);
                }

                // 播放装填完成音效（仅服务端播放，避免重复）
                if (!level.isClientSide()) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
        }
    }

    // ==================== 蓄力动画与音效 ====================

    /**
     * 装填蓄力过程中每 tick 调用。
     *
     * 播放装填中间音效（每 6 ticks 一次），模拟弩弦拉紧的手感。
     * 音调随蓄力进度逐渐升高，增强蓄力反馈感。
     *
     * @param level            世界
     * @param entity           使用者
     * @param stack            物品栈
     * @param remainingUseTicks 剩余使用 ticks
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        // 已装填状态不播放装填音效
        if (CrossbowItem.isCharged(stack)) {
            return;
        }

        int elapsed = this.getUseDuration(stack, entity) - remainingUseTicks;

        // 每 6 ticks 播放一次装填中间音效（与原版弩节奏一致）
        if (elapsed > 0 && elapsed % 6 == 0) {
            // 音调随蓄力进度从 0.5 逐渐升高到 1.5，增强进度感
            float pitch = 0.5F + (float) elapsed / (float) this.getUseDuration(stack, entity);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS, 0.5F, pitch);
        }
    }

    // ==================== 属性覆盖 ====================

    /**
     * 返回装填所需时间（ticks）。
     * 普通弩为 25 ticks（1.25 秒），陨星弩为 50 ticks（2.5 秒），
     * 体现"重型弩"装填更慢的设计。
     *
     * @param stack  物品栈
     * @param entity 使用者
     * @return 装填 ticks（50 = 普通弩的 2 倍）
     */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 50;
    }

    /**
     * 返回使用动画类型。
     * 使用 CROSSBOW 动画（与普通弩相同的拉弓/装填动画）。
     *
     * @param stack 物品栈
     * @return UseAnim.CROSSBOW
     */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CROSSBOW;
    }

    // ==================== 工具方法 ====================

    /**
     * 在玩家背包中查找沉重之矛弹药。
     * 遍历全部背包槽位（包括快捷栏、主背包、副手），返回第一个匹配项。
     *
     * @param player 要搜索的玩家
     * @return 找到的沉重之矛 ItemStack，未找到返回 ItemStack.EMPTY
     */
    private ItemStack findHeavySpear(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(SRItems.HEAVY_SPEAR.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
