package com.spectrum_reclamation.spectrum_reclamation.entity;

import com.spectrum_reclamation.spectrum_reclamation.registry.SRBlocks;
import com.spectrum_reclamation.spectrum_reclamation.registry.SREntities;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 炽光炸弹弹射物实体。
 *
 * 继承 ThrowableItemProjectile（可投掷物品弹射物基类），
 * 该基类提供了完整的弹道物理、物品渲染同步和碰撞检测。
 * 雪球、经验瓶等原版弹射物均使用此基类。
 *
 * 着弹效果：
 * 1. 半径 8 格内所有 LivingEntity 获得 10 秒发光效果（GLOWING）
 * 2. 亡灵生物（isInvertedHealAndHarm() == true）额外着火 100 ticks（5 秒）
 * 3. 在着弹点放置炽光灯方块（亮度 15，30 秒自毁）
 */
public class BlazingBombEntity extends ThrowableItemProjectile {

    /** 效果作用半径（格） */
    private static final double EFFECT_RADIUS = 8.0;
    /** 发光效果持续时间（ticks）：10 秒 = 200 ticks */
    private static final int GLOWING_DURATION = 200;
    /** 亡灵生物着火时间（ticks）：5 秒 = 100 ticks */
    private static final int UNDEAD_FIRE_TICKS = 100;

    /**
     * 反序列化构造器 —— 用于从存档加载实体。
     * NeoForge 在加载世界时通过 EntityType 工厂调用此构造器。
     *
     * @param type  实体类型
     * @param level 实体所在世界
     */
    public BlazingBombEntity(EntityType<? extends BlazingBombEntity> type, Level level) {
        super(type, level);
    }

    /**
     * 投掷构造器 —— 玩家右键使用时创建实体。
     * 调用父类 ThrowableItemProjectile 的 (EntityType, LivingEntity, Level) 构造器，
     * 设置投掷者。物品栈通过 getDefaultItem() 在实体数据初始化时自动设置。
     *
     * @param level  实体所在世界
     * @param thrower 投掷者（玩家）
     */
    public BlazingBombEntity(Level level, LivingEntity thrower) {
        super(SREntities.BLAZING_BOMB.get(), thrower, level);
    }

    /**
     * 返回默认渲染物品。
     * ThrowableItemProjectile 使用此物品在客户端进行渲染（旋转的物品模型）。
     * 同时作为实体数据的默认值，用于客户端同步。
     *
     * @return 炽光炸弹物品实例
     */
    @Override
    protected Item getDefaultItem() {
        return SRItems.BLAZING_BOMB.get();
    }

    /**
     * 着弹处理 —— 弹射物碰到任何东西时调用。
     *
     * 执行流程：
     * 1. 调用 super.onHit() 让基类处理弹道分发和实体销毁
     * 2. 仅在服务端执行效果逻辑（避免客户端重复处理）
     * 3. 计算着弹点坐标，搜索范围内实体
     * 4. 对范围内所有生物施加发光效果
     * 5. 对亡灵生物额外施加燃烧
     * 6. 在着弹点放置炽光灯方块
     * 7. 播放音效和粒子
     *
     * @param result 着弹结果（包含着弹类型和位置信息）
     */
    @Override
    protected void onHit(HitResult result) {
        // 基类处理：分发到 onHitEntity/onHitBlock，然后销毁弹射物（仅服务端）
        super.onHit(result);

        // 仅在服务端执行效果逻辑
        if (!this.level().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) this.level();
            Vec3 hitPos = result.getLocation();
            BlockPos hitBlockPos = BlockPos.containing(hitPos);

            // === 1. 搜索范围内所有生物实体并施加效果 ===
            // 创建以着弹点为中心、边长 16 的立方体搜索区域（半径 8）
            AABB searchArea = new AABB(hitBlockPos).inflate(EFFECT_RADIUS);
            List<LivingEntity> nearbyEntities = serverLevel.getEntitiesOfClass(LivingEntity.class, searchArea);

            for (LivingEntity entity : nearbyEntities) {
                // 效果 1：施加发光效果（等级 0，持续 200 ticks = 10 秒）
                // 参数：效果 Holder、持续时间、等级、非环境效果、显示粒子、显示图标
                entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOWING_DURATION, 0, false, true, true));

                // 效果 2：亡灵生物额外着火
                // isInvertedHealAndHarm() 在亡灵生物（僵尸、骷髅等）上返回 true，
                // 因为亡灵生物对伤害药水免疫、对治疗药水受伤（反转的治疗/伤害机制）
                if (entity.isInvertedHealAndHarm()) {
                    entity.setRemainingFireTicks(UNDEAD_FIRE_TICKS);
                }
            }

            // === 2. 在着弹点放置炽光灯方块 ===
            placeLightBlock(result);

            // === 3. 播放着弹音效（烈焰人射击声，贴合"炽光"主题） ===
            serverLevel.playSound(
                    null,
                    hitPos.x, hitPos.y, hitPos.z,
                    SoundEvents.BLAZE_SHOOT,
                    SoundSource.AMBIENT,
                    1.0F, 0.8F
            );

            // === 4. 生成粒子效果（增强视觉反馈） ===
            serverLevel.sendParticles(
                    ParticleTypes.FLAME,
                    hitPos.x, hitPos.y, hitPos.z,
                    20,           // 粒子数量
                    0.5, 0.5, 0.5,  // 扩散范围
                    0.02          // 速度
            );
        }
    }

    /**
     * 在着弹点放置炽光灯方块。
     *
     * 放置逻辑：
     * - 方块着弹：放置在被击中面的相邻位置（避免卡在方块内部）
     * - 实体着弹：放置在着弹坐标对应的方块位置
     * - 仅在目标位置为空气时放置（避免替换重要方块）
     *
     * @param result 着弹结果
     */
    private void placeLightBlock(HitResult result) {
        BlockPos placePos;

        if (result instanceof BlockHitResult blockHit) {
            // 方块着弹：在被击中面的外侧放置（direction 指向被击中面的法线方向）
            placePos = blockHit.getBlockPos().relative(blockHit.getDirection());
        } else {
            // 实体着弹：在着弹坐标对应的方块位置放置
            placePos = BlockPos.containing(result.getLocation());
        }

        // 仅在目标位置为空气时放置，避免替换玩家建筑或其他重要方块
        BlockState targetState = this.level().getBlockState(placePos);
        if (targetState.isAir()) {
            this.level().setBlock(placePos, SRBlocks.BLAZING_LIGHT.get().defaultBlockState(), 3);
        }
    }

    /**
     * 定义实体的同步数据。
     * ThrowableItemProjectile 已定义物品栈的同步数据（DATA_ITEM_STACK），
     * 本实体无需额外的同步数据，因此不重写此方法。
     */
}
