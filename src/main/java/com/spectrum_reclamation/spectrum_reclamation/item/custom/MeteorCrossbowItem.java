package com.spectrum_reclamation.spectrum_reclamation.item.custom;

import com.spectrum_reclamation.spectrum_reclamation.entity.ThrownHeavySpear;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRDataComponents;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.function.Predicate;

/**
 * 陨星弩物品 —— 继承 CrossbowItem 的重型弩。
 *
 * 与普通弩的区别：
 * 1. 仅接受沉重之矛（HeavySpearItem）作为弹药，不接受普通箭矢
 * 2. 装填速度比普通弩慢 2 倍（getUseDuration 返回 50，普通弩为 25）
 * 3. 自带瞄准镜效果（scope_attached 数据组件，触发 FOV 缩放）
 * 4. 发射 ThrownHeavySpear 实体（高伤害、强击退、钉穿机制）
 * 5. 射击速度 3.5（比普通弩 1.6 更强，匹配矛的重量）
 *
 * 实现思路：
 * 委托父类 CrossbowItem 处理完整的装填/发射状态机，
 * 通过覆盖以下可覆盖方法实现自定义行为：
 * - getAllSupportedProjectiles() → 只接受沉重之矛作为弹药
 * - getSupportedHeldProjectiles() → 同上
 * - createProjectile() → 创建 ThrownHeavySpear 实体而非原版箭矢
 * - performShooting() → 使用更高的射击速度 3.5
 * - getUseDuration() → 50 ticks（2.5 秒，普通弩的 2 倍）
 *
 * 不可覆盖的 private/static 方法（tryLoadProjectiles、draw、getPowerForTime）
 * 通过 getAllSupportedProjectiles() 间接影响其弹药搜索行为。
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

    // ==================== 弹药谓词覆盖 ====================

    /**
     * 覆盖背包弹药搜索谓词 —— 只接受沉重之矛。
     *
     * 父类 CrossbowItem 返回 ARROW_ONLY（只匹配箭矢 Tag）。
     * 此处替换为只匹配沉重之矛，使父类的 tryLoadProjectiles() → draw() 流程
     * 能在背包中找到沉重之矛并装填。
     *
     * LivingEntity.getProjectile() 内部调用此谓词扫描背包。
     *
     * @return 只匹配沉重之矛的谓词
     */
    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return (stack) -> stack.is(SRItems.HEAVY_SPEAR.get());
    }

    /**
     * 覆盖副手弹药搜索谓词 —— 只接受沉重之矛。
     *
     * 与 getAllSupportedProjectiles() 保持一致，
     * 确保副手持矛时也能被识别为有效弹药。
     *
     * @return 只匹配沉重之矛的谓词
     */
    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return (stack) -> stack.is(SRItems.HEAVY_SPEAR.get());
    }

    // ==================== 使用流程覆盖 ====================

    /**
     * 右键使用物品时调用。
     *
     * 瞄准镜 UX 改造：
     * - 已装填状态：不立即发射，而是进入瞄准模式（按住右键 = FOV 缩放瞄准）
     * - 松开右键时（releaseUsing）才发射
     * - 未装填状态：委托父类处理装填流程
     *
     * FOV 缩放由 SRClientScopeHandler.onComputeFovModifier() 自动处理：
     * 只要玩家正在使用物品（isUsingItem）且物品有 scope_attached，
     * 就会触发 0.5x FOV 缩放。
     *
     * @param level  世界
     * @param player 使用物品的玩家
     * @param hand   使用的手（主手/副手）
     * @return 交互结果
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack crossbowStack = player.getItemInHand(hand);

        // 自带瞄准镜效果：确保 scope_attached 数据组件存在
        if (!crossbowStack.has(SRDataComponents.SCOPE_ATTACHED.get())) {
            crossbowStack.set(SRDataComponents.SCOPE_ATTACHED.get(), true);
        }

        // 已装填 → 进入瞄准模式（按住右键），不立即发射
        // startUsingItem() 会设置 player 的使用物品状态，
        // 使 SRClientScopeHandler 的 FOV 缩放生效
        if (isCharged(crossbowStack)) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(crossbowStack);
        }

        // 未装填 → 委托父类处理装填流程
        return super.use(level, player, hand);
    }

    // ==================== 发射逻辑覆盖 ====================

    /**
     * 覆盖弹射物创建 —— 创建 ThrownHeavySpear 而非原版箭矢。
     *
     * 父类 CrossbowItem 在 performShooting() → shoot() → createProjectile() 时
     * 调用此方法为每个弹药创建弹射物实体。
     *
     * ammo 参数是装填时存储在 CHARGED_PROJECTILES 中的 ItemStack 副本，
     * 保留了原始沉重之矛的所有数据组件（包括涂装数据 SPEAR_COATING）。
     *
     * @param level    世界
     * @param shooter  射击者
     * @param weapon   弩物品栈
     * @param ammo     弹药物品栈（从 CHARGED_PROJECTILES 取出）
     * @param isCrit   是否暴击
     * @return ThrownHeavySpear 弹射物实体
     */
    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
        // ammo 已包含涂装数据（draw() 会 copyWithCount 保留所有组件）
        return new ThrownHeavySpear(level, shooter, ammo.copy());
    }

    /**
     * 覆盖射击方法 —— 使用更高的射击速度。
     *
     * 父类 CrossbowItem.use() 调用 performShooting() 时传入 getShootingPower() 的返回值
     * （普通箭矢 1.6，烟花火箭 1.1）。此处忽略传入的速度，强制使用 3.5。
     *
     * 3.5 的速度比普通弩（1.6）快一倍多，匹配沉重之矛的重量感。
     *
     * @param level       世界
     * @param shooter     射击者
     * @param hand        使用的手
     * @param weapon      弩物品栈
     * @param velocity    父类传入的速度（忽略）
     * @param inaccuracy  散射精度
     * @param target      目标实体（弩无锁定目标时为 null）
     */
    @Override
    public void performShooting(Level level, LivingEntity shooter, InteractionHand hand,
                                 ItemStack weapon, float velocity, float inaccuracy,
                                 LivingEntity target) {
        // 使用自定义射击速度 3.5，忽略父类传入的默认速度
        super.performShooting(level, shooter, hand, weapon, 3.5F, inaccuracy, target);
    }

    // ==================== 装填后处理 ====================

    /**
     * 覆盖释放使用回调。
     *
     * 两种场景：
     * 1. 瞄准模式（已装填）：松开右键 → 发射沉重之矛
     * 2. 装填模式（未装填）：松开右键 → 委托父类处理装填判定
     *
     * @param stack    弩物品栈
     * @param level    世界
     * @param shooter  使用者
     * @param timeLeft 剩余使用时间 ticks
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity shooter, int timeLeft) {
        if (isCharged(stack)) {
            // 瞄准模式：松开右键 → 发射
            if (shooter instanceof Player player) {
                // performShooting 内部会清空 CHARGED_PROJECTILES、播放音效、消耗耐久
                this.performShooting(level, player, player.getUsedItemHand(), stack, 3.5F, 1.0F, null);
                // 涂装数据已在装填时保存到弩上，performShooting → createProjectile 会读取
            }
        } else {
            // 装填模式：委托父类处理装填逻辑
            super.releaseUsing(stack, level, shooter, timeLeft);

            // 装填成功后，将涂装数据额外保存到弩上（冗余备份）
            if (isCharged(stack)) {
                ChargedProjectiles projectiles = stack.getOrDefault(
                        DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
                if (!projectiles.isEmpty()) {
                    ItemStack ammo = projectiles.getItems().get(0);
                    String coating = HeavySpearItem.getCoating(ammo);
                    if (coating != null) {
                        stack.set(SRDataComponents.SPEAR_COATING.get(), coating);
                    }
                }
            }
        }
    }

    // ==================== 属性覆盖 ====================

    /**
     * 返回使用持续时间。
     *
     * 两种模式：
     * - 已装填（瞄准模式）：返回 72000（1 小时），允许玩家无限期按住右键瞄准
     * - 未装填（装填模式）：返回 50 ticks（2.5 秒），装填时间是普通弩的 2 倍
     *
     * @param stack  物品栈
     * @param entity 使用者
     * @return 使用 ticks
     */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // 已装填时返回极大值，允许持续瞄准（类似弓的 hold-to-aim 机制）
        if (isCharged(stack)) {
            return 72000;
        }
        // 未装填时返回装填时间（50 ticks = 2.5 秒）
        return 50;
    }
}
