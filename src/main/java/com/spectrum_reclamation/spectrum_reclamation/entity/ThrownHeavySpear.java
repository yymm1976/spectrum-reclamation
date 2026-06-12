package com.spectrum_reclamation.spectrum_reclamation.entity;

import com.spectrum_reclamation.spectrum_reclamation.item.custom.HeavySpearItem;
import com.spectrum_reclamation.spectrum_reclamation.registry.SREntities;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 沉重之矛弹射物实体 —— 由陨星弩发射的重型投射物。
 *
 * 继承 AbstractArrow，获得箭矢基类的碰撞检测、弹道物理和伤害计算框架。
 *
 * 核心特性：
 * 1. 基础伤害 20.0（≈ 苦力怕爆炸伤害，远超普通箭矢的 2.0-6.0）
 * 2. 重力系数 0.08（原版箭矢为 0.05，矛更重所以弹道下坠更快）
 * 3. 强击退（3.0 倍击退强度，远超普通箭矢的 0-2）
 * 4. 钉穿机制：目标身后有实心方块时，目标被"钉"在墙上 1.5 秒
 * 5. 击中实体后变为掉落物，可被拾回重复使用
 * 6. 墨水涂装效果：16 种颜色各有独特效果
 *
 * 钉穿判定原理：
 * 检测目标沿矛飞行方向身后 2 格内是否有实心方块。
 * 如果有（例如墙、地面），说明目标被推到了障碍物上，
 * 此时施加 255 级缓慢 + 运动冻结，模拟"钉在墙上"的效果。
 *
 * 伤害机制：
 * 使用 AbstractArrow 内置的 baseDamage 进行伤害计算，
 * 包含暴击、力量附魔等原版伤害修饰器。
 *
 * 涂装系统：
 * 通过 SPEAR_COATING 数据组件存储颜色 ID（如 "red"、"blue"）。
 * 击中实体时读取涂装颜色，触发对应颜色的特殊效果。
 * 涂装数据通过 pickupItem 传递：构造时设置 → NBT 自动保存/加载 → 拾取时保留。
 *
 * API 说明（1.21.1 NeoForge）：
 * - 构造器需要提供 pickupItemStack 和 firedFromWeapon 两个 ItemStack 参数
 * - getDefaultGravity() 返回 double（不是 float）
 * - getDefaultPickupItem() 是抽象方法，必须覆盖
 * - doKnockback(LivingEntity, DamageSource) 可覆盖以自定义击退
 */
public class ThrownHeavySpear extends AbstractArrow {

    /** 基础伤害值（≈ 苦力怕爆炸伤害） */
    private static final double BASE_DAMAGE = 20.0;

    /** 击退强度（远超普通箭矢的 0-2，体现"重型矛"的力量感） */
    private static final double KNOCKBACK_STRENGTH = 3.0;

    /** 钉穿效果持续时间（ticks）：1.5 秒 = 30 ticks */
    private static final int PIN_DURATION = 30;

    /** 钉穿缓慢等级（amplifier 254 = 等级 255，完全无法移动） */
    private static final int PIN_SLOWNESS_AMPLIFIER = 254;

    /** 钉穿判定距离（格）：检测目标身后 2 格内是否有实心方块 */
    private static final int PIN_CHECK_DISTANCE = 2;

    /** 存储带涂装数据的拾取物品栈，用于拾回时保留涂装 */
    private ItemStack coatedPickupItem;

    /**
     * 反序列化构造器 —— 用于从存档加载实体。
     * NeoForge 在加载世界时通过 EntityType 工厂调用此构造器。
     * 涂装数据通过 AbstractArrow 的 pickupItem NBT 自动恢复。
     *
     * @param type  实体类型
     * @param level 实体所在世界
     */
    public ThrownHeavySpear(EntityType<? extends ThrownHeavySpear> type, Level level) {
        super(type, level);
        this.setBaseDamage(BASE_DAMAGE);
    }

    /**
     * 发射构造器（无涂装） —— 由陨星弩在发射时创建（旧版兼容）。
     *
     * 1.21.1 AbstractArrow 要求提供 pickupItemStack（拾取物品）和 firedFromWeapon（发射武器）。
     * pickupItemStack 是矛本身的物品栈（用于拾取时返还），firedFromWeapon 是发射它的弩。
     *
     * @param level   实体所在世界
     * @param shooter 射手（通常是玩家）
     */
    public ThrownHeavySpear(Level level, LivingEntity shooter) {
        this(level, shooter, new ItemStack(SRItems.HEAVY_SPEAR.get()));
    }

    /**
     * 发射构造器（带涂装） —— 由陨星弩在发射时创建。
     * coatedStack 携带 SPEAR_COATING 数据组件，通过 AbstractArrow 的 pickupItem 机制
     * 自动保存到 NBT，确保实体存档/加载后涂装数据不丢失。
     *
     * @param level       实体所在世界
     * @param shooter     射手（通常是玩家）
     * @param coatedStack 带涂装数据的沉重之矛物品栈
     */
    public ThrownHeavySpear(Level level, LivingEntity shooter, ItemStack coatedStack) {
        super(SREntities.THROWN_HEAVY_SPEAR.get(), shooter, level,
                coatedStack.copy(),                      // 拾取物品：带涂装的矛（copy 避免引用污染）
                new ItemStack(Items.AIR));               // 发射武器：无（陨星弩不是原版弩）
        this.setBaseDamage(BASE_DAMAGE);
        this.coatedPickupItem = coatedStack.copy();
        // 禁用原版 AbstractArrow 的拾取系统（pickup = DISALLOWED）。
        // 本模组通过 spawnAtLocation 在 onHitEntity 中手动掉落矛，
        // 若不禁用，玩家可能在矛命中前拾取实体，导致物品复制（pickup + spawnAtLocation 双份掉落）。
        this.pickup = Pickup.DISALLOWED;
    }

    // ==================== 弹道属性 ====================

    /**
     * 返回默认重力系数（double 类型）。
     * AbstractArrow 默认为 0.05（轻盈的箭矢），我们改为 0.08（沉重的矛），
     * 弹道下坠更快，需要玩家更精准地预判弹道。
     *
     * @return 重力系数 0.08（原版箭矢 0.05，Entity 默认 0.08）
     */
    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    /**
     * 返回默认拾取物品（抽象方法，必须覆盖）。
     * 当矛以掉落物形式存在于世界中时，玩家拾取获得此物品。
     * 返回带涂装数据的物品栈，确保拾回后涂装保留。
     *
     * @return 带涂装数据的沉重之矛物品栈
     */
    @Override
    protected ItemStack getDefaultPickupItem() {
        // 优先使用带涂装的拾取物品（从存档恢复或发射时设置）
        if (this.coatedPickupItem != null) {
            return this.coatedPickupItem.copy();
        }
        // 无涂装时返回普通矛
        return new ItemStack(SRItems.HEAVY_SPEAR.get());
    }

    // ==================== NBT 存档/读取 ====================

    /**
     * 保存附加数据到 NBT。
     * 覆盖此方法以确保涂装物品栈在实体存档时被正确保存。
     *
     * @param tagCompound NBT 标签
     */
    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tagCompound) {
        super.addAdditionalSaveData(tagCompound);
        // 保存带涂装的拾取物品（确保存档后涂装不丢失）
        if (this.coatedPickupItem != null && !this.coatedPickupItem.isEmpty()) {
            tagCompound.put("CoatedPickupItem", this.coatedPickupItem.save(this.registryAccess()));
        }
    }

    /**
     * 从 NBT 读取附加数据。
     * 覆盖此方法以确保涂装物品栈在实体加载时被正确恢复。
     *
     * @param tagCompound NBT 标签
     */
    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tagCompound) {
        super.readAdditionalSaveData(tagCompound);
        // 读取带涂装的拾取物品
        if (tagCompound.contains("CoatedPickupItem")) {
            this.coatedPickupItem = ItemStack.parse(this.registryAccess(),
                    tagCompound.getCompound("CoatedPickupItem")).orElse(ItemStack.EMPTY);
        }
    }

    // ==================== 击退覆盖 ====================

    /**
     * 覆盖父类击退逻辑，使用 3.0 倍击退强度。
     *
     * 原版 doKnockback 仅在武器有击退附魔时才施加击退（通过 EnchantmentHelper.modifyKnockback）。
     * 陨星弩的沉重之矛自带强击退，不需要附魔也能击退目标。
     *
     * 击退方向：沿矛飞行方向（水平分量），目标被推离射击者。
     *
     * @param target      被击退的目标
     * @param damageSource 伤害来源
     */
    @Override
    protected void doKnockback(LivingEntity target, DamageSource damageSource) {
        // 计算矛的水平飞行方向（防止零向量 normalize 产生 NaN）
        Vec3 horizontal = this.getDeltaMovement().multiply(1.0, 0.0, 1.0);
        Vec3 flightDir;
        if (horizontal.lengthSqr() > 1.0E-7) {
            flightDir = horizontal.normalize();
        } else {
            // 矛几乎垂直下落，使用朝向兜底
            flightDir = this.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
            if (flightDir.lengthSqr() > 1.0E-7) {
                flightDir = flightDir.normalize();
            } else {
                flightDir = new Vec3(1.0, 0.0, 0.0); // 最终兜底
            }
        }

        // 计算击退抗性修正（击退抗性属性会减免部分击退）
        double resistance = Math.max(0.0, 1.0 - target.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));

        // 沿飞行方向施加击退（strength * resistance）
        double knockbackX = flightDir.x * KNOCKBACK_STRENGTH * resistance;
        double knockbackZ = flightDir.z * KNOCKBACK_STRENGTH * resistance;

        // push() 方法直接将力加到目标运动上
        // y=0.1 提供微量上推，避免目标贴地滑行
        target.push(knockbackX, 0.1, knockbackZ);
    }

    // ==================== 着弹处理 ====================

    /**
     * 击中实体时调用。
     *
     * 执行流程：
     * 1. 记录矛的飞行方向（用于钉穿判定）
     * 2. 调用父类处理伤害计算 + 击退（doKnockback 已覆盖为 3.0 倍强度）
     * 3. 父类处理完毕后，矛已被标记为 discard（pierceLevel <= 0 时）
     * 4. 执行钉穿判定：检测目标身后实心方块
     * 5. 如触发钉穿：施加 255 级缓慢 + 运动冻结
     * 6. 读取涂装颜色，触发对应颜色的特殊效果
     * 7. 生成带涂装的沉重之矛掉落物（玩家可拾回）
     *
     * 注意：super.onHitEntity() 内部已调用 this.discard()（pierceLevel <= 0 时），
     * discard() 仅标记实体在 tick 结束时移除，此时仍可调用 spawnAtLocation。
     *
     * @param result 实体碰撞结果
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        // 记录碰撞前的飞行方向（防止零向量 normalize 产生 NaN）
        Vec3 horizontal = this.getDeltaMovement().multiply(1.0, 0.0, 1.0);
        Vec3 flightDir;
        if (horizontal.lengthSqr() > 1.0E-7) {
            flightDir = horizontal.normalize();
        } else {
            flightDir = this.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
            if (flightDir.lengthSqr() > 1.0E-7) {
                flightDir = flightDir.normalize();
            } else {
                flightDir = new Vec3(1.0, 0.0, 0.0);
            }
        }

        // === 保存射手引用（涂装效果需要，如绿/浅蓝需要对射手施加效果） ===
        LivingEntity shooter = this.getOwner() instanceof LivingEntity living ? living : null;

        // 调用父类处理伤害计算 + 击退（AbstractArrow 内部处理暴击、力量附魔、doKnockback 等）
        super.onHitEntity(result);

        // 仅在服务端执行后续逻辑（客户端只做渲染）
        if (this.level().isClientSide) {
            return;
        }

        // 获取被击中的实体（可能已被击杀）
        if (result.getEntity() instanceof LivingEntity target && target.isAlive()) {
            // === 钉穿判定 ===
            // 沿矛飞行方向检测目标身后 PIN_CHECK_DISTANCE 格内是否有实心方块
            boolean pinned = false;
            for (int i = 1; i <= PIN_CHECK_DISTANCE; i++) {
                BlockPos checkPos = BlockPos.containing(
                        target.getX() + flightDir.x * i,
                        target.getY(),
                        target.getZ() + flightDir.z * i
                );
                // 非空气方块视为"实心"（墙、地板、天花板等）
                if (!this.level().getBlockState(checkPos).isAir()) {
                    pinned = true;
                    break;
                }
            }

            // === 施加钉穿效果 ===
            if (pinned) {
                // 施加 255 级缓慢效果（30 ticks = 1.5 秒）
                // amplifier 254 = 显示等级 255，目标完全无法移动
                // ambient=false（不透明粒子），showIcon=false（不显示效果图标）
                target.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        PIN_DURATION,
                        PIN_SLOWNESS_AMPLIFIER,
                        false, false, false
                ));
                // 冻结目标运动（设速度为零，模拟"钉在墙上"）
                target.setDeltaMovement(0, 0, 0);
            }

            // === 墨水涂装效果 ===
            // 从矛的拾取物品中读取涂装颜色 ID，触发对应效果
            String coating = HeavySpearItem.getCoating(this.getDefaultPickupItem());
            if (coating != null) {
                applyCoatingEffect(target, shooter, coating);
            }
        }

        // === 矛变为掉落物（保留涂装数据） ===
        // 使用 getDefaultPickupItem() 获取带涂装的物品栈
        // spawnAtLocation 在实体位置生成 ItemEntity，玩家可拾回重复使用
        // 此时 this.discard() 已由 super.onHitEntity() 调用，实体将在 tick 结束时移除
        // 但 spawnAtLocation 仍可正常工作（实体位置仍然有效）
        this.spawnAtLocation(this.getDefaultPickupItem(), 0.1F);
    }

    // ==================== 16 色涂装效果 ====================

    /**
     * 根据涂装颜色 ID 施加对应效果。
     * 所有效果仅在服务端执行（调用方已确认 !level().isClientSide）。
     *
     * @param target  被击中的目标
     * @param shooter 射手（部分效果需要，如绿/浅蓝），可能为 null
     * @param color   涂装颜色 ID（如 "red"、"blue"）
     */
    private void applyCoatingEffect(LivingEntity target, @Nullable LivingEntity shooter, String color) {
        switch (color) {
            case "red" -> applyRedEffect(target);           // 红：点燃 4 秒
            case "orange" -> applyOrangeEffect(target);     // 橙：虚弱 I 3 秒
            case "yellow" -> applyYellowEffect(target);     // 黄：发光 5 秒
            case "green" -> applyGreenEffect(target, shooter); // 绿：攻击者回复 4 HP
            case "cyan" -> applyCyanEffect(target);         // 青：缓慢 II 3 秒
            case "blue" -> applyBlueEffect(target);         // 蓝：挖掘疲劳 II 5 秒
            case "purple" -> applyPurpleEffect(target, shooter); // 紫：交换位置
            case "magenta" -> applyMagentaEffect(target);   // 品红：弹飞 5 格
            case "pink" -> applyPinkEffect(target);         // 粉：周围生物发光 3 秒
            case "white" -> applyWhiteEffect(target);       // 白：清除正面效果
            case "black" -> applyBlackEffect(target);       // 黑：凋零 I 6 秒
            case "light_gray" -> applyLightGrayEffect(target); // 浅灰：召唤蠹虫
            case "dark_gray" -> applyDarkGrayEffect(target);   // 深灰：沉默 2 秒
            case "brown" -> applyBrownEffect(target);       // 棕色：随机脱落装备
            case "light_blue" -> applyLightBlueEffect(target, shooter); // 浅蓝：攻击者速度 I 10 秒
            case "lime" -> applyLimeEffect(target);         // 黄绿：脚下蜘蛛网
        }
    }

    // ==================== 单色效果实现 ====================

    /**
     * 红色涂装 —— 点燃目标 4 秒（80 ticks）。
     * setRemainingFireTicks 直接设置实体的燃烧倒计时。
     */
    private void applyRedEffect(LivingEntity target) {
        target.setRemainingFireTicks(80);
    }

    /**
     * 橙色涂装 —— 虚弱 I 3 秒（60 ticks）。
     * amplifier 0 = 等级 I（虚弱 I 降低 4 点攻击伤害）。
     */
    private void applyOrangeEffect(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
    }

    /**
     * 黄色涂装 —— 发光 5 秒（100 ticks）。
     * 发光效果使目标轮廓可见（穿墙可视），持续 5 秒。
     */
    private void applyYellowEffect(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
    }

    /**
     * 绿色涂装 —— 攻击者回复 4 HP（2 颗心）。
     * heal() 恢复指定血量，上限为最大生命值。
     * 如果没有射手引用（如发射器触发），效果不触发。
     */
    private void applyGreenEffect(LivingEntity target, @Nullable LivingEntity shooter) {
        if (shooter != null && shooter.isAlive()) {
            shooter.heal(4.0f);
        }
    }

    /**
     * 青色涂装 —— 缓慢 II 3 秒（60 ticks）。
     * amplifier 1 = 等级 II（缓慢 II 移动速度降至原版的 40%）。
     */
    private void applyCyanEffect(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
    }

    /**
     * 蓝色涂装 —— 挖掘疲劳 II 5 秒（100 ticks）。
     * amplifier 1 = 等级 II（挖掘速度大幅降低，攻击速度也受影响）。
     */
    private void applyBlueEffect(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1));
    }

    /**
     * 紫色涂装 —— 与目标交换位置。
     * Boss 生物（末影龙/凋灵）不触发交换，退回普通击退。
     *
     * 实现原理：使用 teleportTo() 交换双方坐标，并清空交换后的残余速度。
     * 服务端 teleportTo() 会同步坐标，hurtMarked 会要求客户端同步运动状态。
     */
    private void applyPurpleEffect(LivingEntity target, @Nullable LivingEntity shooter) {
        if (shooter == null) return;

        // Boss 生物不触发交换（末影龙/凋灵）
        if (target instanceof EnderDragon || target instanceof WitherBoss) {
            return;
        }

        // 先捕获双方原始坐标，再执行交换（避免 teleportTo 后坐标被引擎修正导致不同步）
        double shooterX = shooter.getX();
        double shooterY = shooter.getY();
        double shooterZ = shooter.getZ();
        double targetX = target.getX();
        double targetY = target.getY();
        double targetZ = target.getZ();

        Vec3 shooterMovement = shooter.getDeltaMovement();
        Vec3 targetMovement = target.getDeltaMovement();

        // 使用 teleportTo 确保位置变更正确同步到所有追踪客户端
        target.teleportTo(shooterX, shooterY, shooterZ);
        shooter.teleportTo(targetX, targetY, targetZ);

        // 交换位置后同步交换双方运动，避免客户端继续沿旧速度预测导致橡皮筋回弹。
        target.setDeltaMovement(shooterMovement);
        shooter.setDeltaMovement(targetMovement);
        target.hurtMarked = true;
        shooter.hurtMarked = true;
    }

    /**
     * 品红色涂装 —— 目标向上弹飞 5 格。
     * setDeltaMovement 直接设置实体运动向量，y=5 提供约 5 格的上升高度。
     * 重力会在后续 tick 中将其拉回地面。
     */
    private void applyMagentaEffect(LivingEntity target) {
        target.setDeltaMovement(0, 5, 0);
        // 通知客户端更新运动（否则客户端不知道实体被弹飞了）
        target.hurtMarked = true;
    }

    /**
     * 粉色涂装 —— 目标周围 3 格内所有生物发光 3 秒（60 ticks）。
     * 使用 AABB（轴对齐包围盒）查询目标周围 3 格范围内的所有活体生物。
     * 注意：不包含目标自身（已有发光效果或其他涂装处理）。
     */
    private void applyPinkEffect(LivingEntity target) {
        // 构建 3 格范围的搜索包围盒
        AABB searchBox = new AABB(target.blockPosition()).inflate(3.0);
        // 获取范围内所有活体生物
        List<LivingEntity> nearbyEntities = this.level().getEntitiesOfClass(
                LivingEntity.class, searchBox, e -> e.isAlive() && e != target
        );
        // 对每个附近生物施加发光效果（3 秒 = 60 ticks）
        for (LivingEntity entity : nearbyEntities) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
        }
    }

    /**
     * 白色涂装 —— 清除目标所有正面效果。
     *
     * 实现要点：
     * - 先收集待移除的 Holder → 再逐个 removeEffect()
     * - 避免在遍历过程中修改集合导致 ConcurrentModificationException
     * - isBeneficial() 判断效果是否为正面（如速度、力量、防火等）
     */
    private void applyWhiteEffect(LivingEntity target) {
        // 第一步：收集所有正面效果的 Holder（快照）
        List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> toRemove = new ArrayList<>();
        for (MobEffectInstance instance : target.getActiveEffects()) {
            if (instance.getEffect().value().isBeneficial()) {
                toRemove.add(instance.getEffect());
            }
        }
        // 第二步：逐个移除（在快照上操作，不会 ConcurrentModificationException）
        for (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect : toRemove) {
            target.removeEffect(effect);
        }
    }

    /**
     * 黑色涂装 —— 凋零 I 6 秒（120 ticks）。
     * 凋零效果每 2 秒（40 ticks）对目标造成伤害，6 秒内共造成 3 次伤害。
     * amplifier 0 = 等级 I（每次造成 2 点伤害）。
     */
    private void applyBlackEffect(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 0));
    }

    /**
     * 浅灰色涂装 —— 在目标位置召唤 3 只蠹虫。
     * addFreshEntity 在世界中生成新实体，蠹虫会自动攻击最近的玩家。
     * 使用 EntityType.SILVERFISH 调用蠹虫实体构造器。
     */
    private void applyLightGrayEffect(LivingEntity target) {
        Level level = this.level();
        for (int i = 0; i < 3; i++) {
            var silverfish = EntityType.SILVERFISH.create(level);
            if (silverfish != null) {
                // 在目标附近随机偏移位置生成，避免重叠
                double offsetX = (this.random.nextDouble() - 0.5) * 2.0;
                double offsetZ = (this.random.nextDouble() - 0.5) * 2.0;
                silverfish.moveTo(target.getX() + offsetX, target.getY(), target.getZ() + offsetZ,
                        this.random.nextFloat() * 360.0F, 0.0F);
                // 设定攻击目标，使蠹虫主动攻击被击中的实体
                silverfish.setTarget(target);
                level.addFreshEntity(silverfish);
            }
        }
    }

    /**
     * 深灰色涂装 —— 目标沉默 2 秒（40 ticks）。
     * setSilent(true) 使目标不发出任何声音（包括脚步声、受伤声等）。
     *
     * 注意：setSilent(true) 是持久化到 NBT 的，如果服务器在延迟恢复前重启，
     * TickTask 会丢失，导致实体永久沉默。因此使用 MobEffect 机制作为保底：
     * 如果实体在服务端重启后重新加载，setSilent 仍为 true，
     * 此时通过检查实体的自定义持久数据来恢复。
     * 当前简化方案：使用 scheduleTick 延迟恢复，在实体 tick 中增加兜底检查。
     */
    private void applyDarkGrayEffect(LivingEntity target) {
        target.setSilent(true);
        // 40 ticks = 2 秒后恢复声音
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                    serverLevel.getServer().getTickCount() + 40,
                    () -> {
                        // 安全检查：实体仍存活且仍在同一世界
                        if (target.isAlive() && target.level() == serverLevel) {
                            target.setSilent(false);
                        }
                    }
            ));
        }
        // 兜底机制：在服务端实体的自定义数据中记录"应在何 tick 恢复声音"
        // 实体 tick 时可检查此数据并恢复（防止 TickTask 因重启丢失）
        target.getPersistentData().putLong("spectrum_reclamation:silent_until_tick",
                target.level().getGameTime() + 40);
    }

    /**
     * 棕色涂装 —— 目标随机脱落一件装备。
     * 遍历所有装备槽位（主手、副手、头盔、胸甲、护腿、靴子），
     * 收集非空装备后随机选择一件生成掉落物，并清空对应槽位。
     */
    private void applyBrownEffect(LivingEntity target) {
        // 收集目标所有非空装备及其槽位
        List<EquipmentSlot> equippedSlots = new ArrayList<>();
        List<ItemStack> equippedItems = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipment = target.getItemBySlot(slot);
            if (!equipment.isEmpty()) {
                equippedSlots.add(slot);
                equippedItems.add(equipment.copy());
            }
        }
        // 至少有一件装备才随机脱落
        if (!equippedItems.isEmpty()) {
            int chosenIndex = this.random.nextInt(equippedItems.size());
            ItemStack dropped = equippedItems.get(chosenIndex);
            EquipmentSlot chosenSlot = equippedSlots.get(chosenIndex);

            // 清空目标对应槽位（防止物品复制）
            target.setItemSlot(chosenSlot, ItemStack.EMPTY);

            // 在目标位置生成掉落物
            ItemEntity itemEntity = new ItemEntity(this.level(),
                    target.getX(), target.getY() + 0.5, target.getZ(), dropped);
            // 给掉落物一个随机速度，模拟"脱落"效果
            itemEntity.setDeltaMovement(
                    this.random.triangle(0.0, 0.11485000171133876),
                    this.random.triangle(0.05, 0.05742500085566938),
                    this.random.triangle(0.0, 0.11485000171133876)
            );
            this.level().addFreshEntity(itemEntity);
        }
    }

    /**
     * 浅蓝色涂装 —— 攻击者获得速度 I 10 秒（200 ticks）。
     * amplifier 0 = 等级 I（移动速度提升 20%）。
     * 如果没有射手引用（如发射器触发），效果不触发。
     */
    private void applyLightBlueEffect(LivingEntity target, @Nullable LivingEntity shooter) {
        if (shooter != null && shooter.isAlive()) {
            shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
        }
    }

    /**
     * 黄绿色涂装 —— 目标脚下生成蜘蛛网。
     * setBlock 在目标脚下方块位置放置蜘蛛网，限制目标移动。
     * 仅在该位置当前为空气或可替换方块时放置，避免破坏已有方块。
     */
    private void applyLimeEffect(LivingEntity target) {
        BlockPos targetPos = BlockPos.containing(target.getX(), target.getY(), target.getZ());
        BlockState targetState = this.level().getBlockState(targetPos);
        // 仅在空气方块或可替换方块位置放置蜘蛛网
        if (targetState.isAir() || targetState.canBeReplaced()) {
            this.level().setBlock(targetPos, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState(), 3);
        }
    }
}
