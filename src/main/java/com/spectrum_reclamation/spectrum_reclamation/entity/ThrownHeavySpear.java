package com.spectrum_reclamation.spectrum_reclamation.entity;

import com.spectrum_reclamation.spectrum_reclamation.registry.SREntities;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

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

    /**
     * 反序列化构造器 —— 用于从存档加载实体。
     * NeoForge 在加载世界时通过 EntityType 工厂调用此构造器。
     *
     * @param type  实体类型
     * @param level 实体所在世界
     */
    public ThrownHeavySpear(EntityType<? extends ThrownHeavySpear> type, Level level) {
        super(type, level);
        this.setBaseDamage(BASE_DAMAGE);
    }

    /**
     * 发射构造器 —— 由陨星弩在发射时创建。
     *
     * 1.21.1 AbstractArrow 要求提供 pickupItemStack（拾取物品）和 firedFromWeapon（发射武器）。
     * pickupItemStack 是矛本身的物品栈（用于拾取时返还），firedFromWeapon 是发射它的弩。
     *
     * @param level   实体所在世界
     * @param shooter 射手（通常是玩家）
     */
    public ThrownHeavySpear(Level level, LivingEntity shooter) {
        super(SREntities.THROWN_HEAVY_SPEAR.get(), shooter, level,
                new ItemStack(SRItems.HEAVY_SPEAR.get()),   // 拾取物品：沉重之矛
                new ItemStack(Items.AIR));                   // 发射武器：无（陨星弩不是原版弩）
        this.setBaseDamage(BASE_DAMAGE);
    }

    // ==================== 弹道属性 ====================

    /**
     * 返回默认重力系数（double 类型）。
     * AbstractArrow 默认为 0.05（轻盈的箭矢），我们改为 0.08（沉重的矛），
     * 弹道下坠更快，需要玩家更精准地预判弹道。
     *
     * 注意：1.21.1 的 Entity.getDefaultGravity() 返回 double 而非 float，
     * 也没有公开的 setGravity(float) 方法，只能通过覆盖此方法实现。
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
     *
     * @return 沉重之矛物品栈
     */
    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(SRItems.HEAVY_SPEAR.get());
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
        // 计算矛的水平飞行方向
        Vec3 flightDir = this.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize();

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
     * 6. 生成沉重之矛掉落物（玩家可拾回）
     *
     * 注意：super.onHitEntity() 内部已调用 this.discard()（pierceLevel <= 0 时），
     * discard() 仅标记实体在 tick 结束时移除，此时仍可调用 spawnAtLocation。
     *
     * @param result 实体碰撞结果
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        // 记录碰撞前的飞行方向（normalize 后为单位向量）
        // multiply(1.0, 0.0, 1.0) 忽略垂直分量，只取水平方向
        Vec3 flightDir = this.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize();

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
        }

        // === 矛变为掉落物 ===
        // spawnAtLocation 在实体位置生成 ItemEntity，玩家可拾回重复使用
        // 此时 this.discard() 已由 super.onHitEntity() 调用，实体将在 tick 结束时移除
        // 但 spawnAtLocation 仍可正常工作（实体位置仍然有效）
        this.spawnAtLocation(this.getPickupItem(), 0.1F);
    }
}
