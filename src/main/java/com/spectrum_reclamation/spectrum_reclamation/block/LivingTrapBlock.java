package com.spectrum_reclamation.spectrum_reclamation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * 活体陷阱方块 —— 可捕捉路过的小型生物。
 *
 * 外观为低矮的单格方块（类似感测体/活板门），实体踩上后触发陷阱。
 *
 * 核心机制：
 * 1. 实体踏入方块时，检查是否为"小型生物"（宽度 ≤ 0.6 格）
 * 2. 满足条件则"吞入"实体：设为不可见、无敌、锁定位置（禁止重力）
 * 3. 5 秒（100 ticks）后弹出实体，恢复可见和重力，施加 10 秒饥饿 I
 * 4. 方块进入 15 秒（300 ticks）冷却，冷却期间不可再次触发，外观通过烟雾粒子变化
 *
 * 技术实现说明：
 * - 使用 BooleanProperty COOLDOWN 方块状态控制冷却
 * - 使用 scheduleTick 延迟刻实现 5 秒弹出和 15 秒冷却重置
 * - 使用静态 HashMap 跟踪被吞入的实体（方块位置 → 实体引用）
 * - entityInside() 每 tick 触发，通过 COOLDOWN 状态防止重复触发
 *
 * 注意：setInvulnerableTicks() 是 WitherBoss 专属方法，
 * 此处使用 Entity.setInvulnerable(boolean) 实现等效的无敌效果。
 */
public class LivingTrapBlock extends Block {

    // ==================== 常量定义 ====================

    /** 方块状态属性：冷却中（true = 冷却中，不可触发；false = 待命，可触发） */
    public static final BooleanProperty COOLDOWN = BooleanProperty.create("cooldown");

    /** 吞入持续时间（5 秒 = 100 ticks） */
    private static final int SWALLOW_DURATION = 100;

    /** 冷却持续时间（15 秒 = 300 ticks） */
    private static final int COOLDOWN_DURATION = 300;

    /**
     * 低矮碰撞箱形状。
     * 类似感测体的扁平外观：宽 14/16 格（两侧各留 1 像素边框），高 2/16 格（约 0.125 格）。
     * 数值单位为 1/16 格（像素），Block 满格为 0~16。
     */
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 2.0, 15.0);

    /**
     * 被吞入实体的追踪表。
     * Key = "维度ID:方块位置" 字符串，确保跨维度不冲突。
     * Value = 被吞入的实体引用。
     */
    private static final Map<String, LivingEntity> TRAPPED_ENTITIES = new HashMap<>();

    /** 生成维度感知的追踪键 */
    private static String trapKey(Level level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ==================== 构造与状态定义 ====================

    public LivingTrapBlock(Properties properties) {
        super(properties);
        // 设置默认状态：COOLDOWN = false（待命状态）
        this.registerDefaultState(this.stateDefinition.any().setValue(COOLDOWN, false));
    }

    /**
     * 注册方块状态属性。
     * Minecraft 方块状态系统通过 Property 组合管理同一方块的不同视觉/逻辑状态。
     * 此处仅定义一个布尔属性 cooldown，共产生 2 种状态变体。
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COOLDOWN);
    }

    // ==================== 碰撞与寻路 ====================

    /**
     * 返回低矮碰撞箱。
     * 实体可以踩上去，但碰撞箱足够低矮，不会阻挡移动。
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * 此方块不阻挡寻路。
     * AI 寻路时不会绕开此方块，确保生物会自然走上来触发陷阱。
     */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ==================== 核心陷阱逻辑 ====================

    /**
     * 实体碰撞回调 —— 每 tick 检测实体是否在方块内部。
     *
     * 触发条件（全部满足才执行）：
     * 1. 仅在服务端执行（!level.isClientSide）
     * 2. 方块未处于冷却状态（!state.getValue(COOLDOWN)）
     * 3. 实体是 LivingEntity 且存活
     * 4. 实体碰撞箱宽度 ≤ 0.6（"小型生物"判定标准，涵盖鸡、猫、蝙蝠等）
     *
     * NeoForge 的 entityInside 机制：当实体的碰撞箱与方块碰撞箱有重叠时，
     * 每 tick 都会调用此方法。通过 COOLDOWN 状态防止重复触发。
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // 仅服务端处理，客户端不做逻辑
        if (level.isClientSide) return;
        // 冷却中不触发
        if (state.getValue(COOLDOWN)) return;
        // 只处理存活的 LivingEntity
        if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isAlive()) return;
        // 小型生物判定：碰撞箱宽度 ≤ 0.6 格
        if (livingEntity.getBbWidth() > 0.6f) return;

        // === 执行吞入 ===

        // 1. 设为不可见（客户端会跳过渲染，实体"消失"）
        livingEntity.setInvisible(true);

        // 2. 设为无敌（Entity.setInvulnerable(true) 阻止所有伤害）
        //    注意：setInvulnerableTicks() 是 WitherBoss 专属方法，不可用于通用实体
        livingEntity.setInvulnerable(true);

        // 3. 禁止重力，将实体"钉"在陷阱位置，防止被推开或自然移动
        livingEntity.setNoGravity(true);

        // 4. 记录被吞入的实体，供延迟 tick 释放时查找
        TRAPPED_ENTITIES.put(trapKey(level, pos), livingEntity);

        // 5. 调度 5 秒（100 ticks）后释放实体的延迟 tick
        level.scheduleTick(pos, this, SWALLOW_DURATION);

        // 6. 设置方块进入冷却状态（flag = 3：通知客户端 + 发送方块更新）
        level.setBlock(pos, state.setValue(COOLDOWN, true), 3);
    }

    // ==================== 延迟 Tick 逻辑 ====================

    /**
     * 延迟 tick 入口 —— 根据当前方块状态决定执行释放实体或重置冷却。
     *
     * NeoForge 的 scheduleTick 机制：调用 level.scheduleTick(pos, block, delay) 后，
     * ServerLevel 会在 delay ticks 后调用此 tick() 方法。
     * 如果方块在 tick 前被破坏，此 tick 不会触发（方块已不存在）。
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(COOLDOWN)) {
            // 冷却状态：尝试释放实体
            LivingEntity trapped = TRAPPED_ENTITIES.get(trapKey(level, pos));
            if (trapped != null) {
                releaseEntity(trapped, level, pos);
                TRAPPED_ENTITIES.remove(trapKey(level, pos));
            }
            // 调度 15 秒后重置冷却（再过 200 ticks 才能恢复待命状态）
            level.scheduleTick(pos, this, COOLDOWN_DURATION - SWALLOW_DURATION);
        } else {
            // 非冷却状态（冷却到期）：恢复方块为待命状态
            level.setBlock(pos, state.setValue(COOLDOWN, false), 3);
        }
    }

    // ==================== 方块移除时的清理逻辑 ====================

    /**
     * 方块被移除时的清理回调 —— 确保被吞入的实体不会"永久消失"。
     *
     * 当玩家挖掘、爆炸摧毁或命令移除方块时，scheduleTick 的延迟 tick 不会再触发，
     * 因此必须在此处手动释放被困实体，避免实体状态永久异常（不可见、无敌、无重力）。
     *
     * NeoForge 的 onRemove 机制：
     * - 当方块被替换或移除时由 Level 调用
     * - 参数 oldState = 被移除前的方块状态，newState = 新方块状态
     * - 若方块被同类型方块替换（如通过 setBlock 更新状态），newBlock == this 不触发释放
     *
     * @param oldState  被移除前的旧方块状态
     * @param level     所在世界
     * @param pos       方块位置
     * @param newState  新的方块状态（可能是空气，也可能是其他方块）
     * @param movedByPiston 是否因活塞移动而移除
     */
    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 仅在方块真正被替换为不同类型时执行清理（排除自身状态更新的情况）
        if (!oldState.is(newState.getBlock())) {
            // 仅在冷却状态（即有实体被吞入时）才需要清理
            if (oldState.getValue(COOLDOWN)) {
                LivingEntity trapped = TRAPPED_ENTITIES.remove(trapKey(level, pos));
                if (trapped != null) {
                    // 恢复实体正常状态，防止实体永久卡在不可见/无敌/无重力状态
                    trapped.setInvisible(false);
                    trapped.setInvulnerable(false);
                    trapped.setNoGravity(false);
                }
                // trapped == null 的情况：服务器重启后 TRAPPED_ENTITIES 被清空，
                // 实体引用丢失，此处无法恢复，但实体会在重启时自然重新加载，状态重置
            }
        }
        // 必须调用父类实现，确保方块移除的其他清理逻辑正常执行
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }

    // ==================== 实体释放逻辑 ====================

    /**
     * 释放被吞入的实体 —— 恢复可见、重力、可受伤，并施加饥饿 I。
     *
     * 释放流程：
     * 1. 恢复实体可见性
     * 2. 关闭无敌状态
     * 3. 恢复重力（实体可以正常移动和掉落）
     * 4. 施加 10 秒饥饿 I（amplifier = 0 → 等级 I）
     * 5. 播放音效和生成粒子作为视觉/听觉反馈
     */
    private void releaseEntity(LivingEntity entity, ServerLevel level, BlockPos pos) {
        // 恢复实体正常状态
        entity.setInvisible(false);
        entity.setInvulnerable(false);
        entity.setNoGravity(false);

        // 施加 10 秒饥饿 I（200 ticks，amplifier = 0 → 等级 I）
        // 参数：效果 Holder、持续时间、等级、非环境效果、显示粒子、显示图标
        entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0, false, true, true));

        // 服务端生成释放粒子（在实体当前位置上方）
        level.sendParticles(
                ParticleTypes.SMOKE,
                entity.getX(), entity.getY() + 0.5, entity.getZ(),
                10,            // 粒子数量
                0.3, 0.2, 0.3, // X/Y/Z 扩散范围
                0.02           // 速度
        );

        // 播放陷阱释放音效（蜘蛛声，贴合"陷阱"主题）
        level.playSound(
                null,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.SPIDER_HURT,
                SoundSource.BLOCKS,
                1.0F, 0.8F
        );
    }

    // ==================== 客户端渲染 ====================

    /**
     * 客户端粒子动画 —— 冷却期间在方块上方生成烟雾粒子。
     * 表示陷阱正在"消化/恢复"，给玩家直观的冷却反馈。
     *
     * animateTick 仅在客户端调用，每帧执行一次。
     * 使用随机概率（20%）避免粒子过于密集。
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(COOLDOWN)) return;

        // 20% 概率生成烟雾粒子
        if (random.nextFloat() < 0.2f) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double y = pos.getY() + 0.3;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            // 烟雾粒子缓慢上升
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.02, 0.0);
        }
    }

    // ==================== 方块行为 ====================

    /**
     * 方块被破坏时掉落自身。
     * 未设置 noLootTable()，Minecraft 会尝试从战利品表获取掉落物。
     * 需配合 data/spectrum_reclamation/loot_table/blocks/living_trap.json 定义掉落规则。
     */
    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        // 不可被活塞推动（陷阱方块不应被轻易移动）
        return PushReaction.BLOCK;
    }
}
