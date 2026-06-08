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
     * 最小化覆盖：仅添加自带瞄准镜效果，然后委托父类处理。
     * 父类 CrossbowItem.use() 的完整逻辑：
     * 1. 已装填 → 调用 performShooting() 发射弹射物
     * 2. 未装填且背包有弹药 → player.startUsingItem() 开始蓄力
     * 3. 无弹药 → 返回 fail
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
        // 首次使用时设置，之后一直保留
        if (!crossbowStack.has(SRDataComponents.SCOPE_ATTACHED.get())) {
            crossbowStack.set(SRDataComponents.SCOPE_ATTACHED.get(), true);
        }

        // 委托父类处理装填/发射状态机
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
     * 覆盖释放使用回调 —— 装填完成后保存涂装数据到弩上。
     *
     * 父类 CrossbowItem.releaseUsing() 在蓄力完成后调用
     * tryLoadProjectiles() → draw() 从背包消耗沉重之矛并存入 CHARGED_PROJECTILES。
     *
     * draw() 使用 ammo.copyWithCount(1) 复制弹药，保留所有数据组件。
     * 因此涂装数据已经保存在 CHARGED_PROJECTILES 中的矛物品栈上。
     * 但为了冗余安全（防止 ChargedProjectiles 序列化丢失自定义组件），
     * 此处额外将涂装数据保存到弩本身的 SPEAR_COATING 组件上。
     *
     * @param stack    弩物品栈
     * @param level    世界
     * @param shooter  使用者
     * @param timeLeft 剩余使用时间 ticks
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity shooter, int timeLeft) {
        // 委托父类处理装填逻辑（蓄力判定 + tryLoadProjectiles + draw）
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

    // ==================== 属性覆盖 ====================

    /**
     * 返回装填所需时间（ticks）。
     * 普通弩为 25 ticks（1.25 秒），陨星弩为 50 ticks（2.5 秒），
     * 体现"重型弩"装填更慢的设计。
     *
     * 父类 getUseDuration() 返回 getChargeDuration() + 3。
     * 此处直接返回 50，跳过附魔修改和 +3 缓冲。
     *
     * @param stack  物品栈
     * @param entity 使用者
     * @return 装填 ticks（50 = 普通弩的 2 倍）
     */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 50;
    }
}
