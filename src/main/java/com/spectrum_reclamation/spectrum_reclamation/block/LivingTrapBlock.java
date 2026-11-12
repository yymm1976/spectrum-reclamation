package com.spectrum_reclamation.spectrum_reclamation.block;

import com.spectrum_reclamation.spectrum_reclamation.block_entity.LivingTrapBlockEntity;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
 * - 使用 LivingTrapBlockEntity 持久化被困实体 UUID 与剩余时间
 * - 使用方块实体 ticker 实现 5 秒弹出和 15 秒冷却重置
 * - 使用方块实体 tick 中的 AABB 扫描替代 entityInside()（noCollision 方块不稳定触发该回调）
 * - 扫描频率：每 2 ticks 一次（避免每 tick 扫描，同时保持触发手感）
 *
 * 注意：setInvulnerableTicks() 是 WitherBoss 专属方法，
 * 此处使用 Entity.setInvulnerable(boolean) 实现等效的无敌效果。
 */
public class LivingTrapBlock extends Block implements EntityBlock {

    // ==================== 常量定义 ====================

    /** 方块状态属性：冷却中（true = 冷却中，不可触发；false = 待命，可触发） */
    public static final BooleanProperty COOLDOWN = BooleanProperty.create("cooldown");

    /** 吞入持续时间（5 秒 = 100 ticks） */
    public static final int SWALLOW_DURATION = 100;

    /** 冷却持续时间（15 秒 = 300 ticks） */
    public static final int COOLDOWN_DURATION = 300;

    /** 待命扫描间隔（2 ticks），由方块实体 ticker 负责循环计时。 */
    public static final int SCAN_INTERVAL = 2;

    /**
     * 低矮碰撞箱形状。
     * 类似感测体的扁平外观：宽 14/16 格（两侧各留 1 像素边框），高 2/16 格（约 0.125 格）。
     * 数值单位为 1/16 格（像素），Block 满格为 0~16。
     */
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 2.0, 15.0);

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
     * 返回 true 使 AI 寻路时不会绕开此方块，确保生物会自然走上来触发陷阱。
     */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }

    // ==================== 核心陷阱逻辑 ====================

    /**
     * 扫描方块上方 AABB 内的小型生物，触发吞入逻辑。
     *
     * 替代 entityInside() 的原因：LivingTrapBlock 使用 noOcclusion()，
     * 这会导致 entityInside() 不被调用（Minecraft 的碰撞系统要求方块有碰撞箱
     * 才会触发 entityInside 回调）。
     *
     * 扫描频率由 LivingTrapBlockEntity.SCAN_INTERVAL 控制。
     * 使用方块实体 ticker 的原因：它会随区块加载恢复，不依赖易丢失的延迟刻。
     *
     * @param state 当前方块状态
     * @param level 服务端世界
     * @param pos   方块位置
     */
    public static void scanAndTrapEntity(ServerLevel level, BlockPos pos, BlockState state) {
        // 构建扫描区域：方块位置 + 上方 1.5 格（覆盖站在方块上的实体，额外 0.5 格容错）
        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1.5, pos.getZ() + 1
        );

        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox);

        for (LivingEntity entity : candidates) {
            // 只处理存活的实体
            if (!entity.isAlive()) continue;
            // 小型生物判定：碰撞箱宽度 ≤ 0.6 格
            if (entity.getBbWidth() > 0.6f) continue;

            // 检查此位置是否有方块实体负责持久化状态；没有则不触发，避免吞入后无法释放
            if (!(level.getBlockEntity(pos) instanceof LivingTrapBlockEntity trapBlockEntity)) return;

            // === 执行吞入 ===
            trapEntity(entity, level, pos, state, trapBlockEntity);
            return; // 每次扫描最多吞入一个实体
        }
    }

    /**
     * 执行吞入逻辑 —— 将实体锁定在陷阱位置。
     *
     * 吞入步骤：
     * 1. 设为不可见（客户端跳过渲染，实体"消失"）
     * 2. 设为无敌（阻止所有伤害）
     * 3. 禁止重力，将实体"钉"在陷阱位置
     * 4. 将实体 UUID 和倒计时写入方块实体
     * 5. 设置方块进入冷却状态
     *
     * @param entity  被吞入的实体
     * @param level   世界
     * @param pos     方块位置
     * @param state   当前方块状态
     */
    private static void trapEntity(LivingEntity entity, ServerLevel level, BlockPos pos, BlockState state, LivingTrapBlockEntity trapBlockEntity) {
        // 1. 设为不可见
        entity.setInvisible(true);
        // 2. 设为无敌
        entity.setInvulnerable(true);
        // 3. 禁止重力，将实体"钉"在陷阱位置
        entity.setNoGravity(true);

        // 4. 记录被吞入实体的 UUID 和倒计时，方块实体会负责持久化到 NBT
        trapBlockEntity.beginSwallowing(entity);

        // 5. 设置方块进入冷却状态（flag = 3：通知客户端 + 发送方块更新）
        level.setBlock(pos, state.setValue(COOLDOWN, true), 3);
    }

    // ==================== 方块移除时的清理逻辑 ====================

    /**
     * 方块被移除时的清理回调 —— 确保被吞入的实体不会"永久消失"。
     *
     * 当玩家挖掘、爆炸摧毁或命令移除方块时，方块实体会被移除，
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
            // 仅服务端执行实体释放，客户端只接收服务端同步后的结果
            if (level instanceof ServerLevel serverLevel) {
                if (level.getBlockEntity(pos) instanceof LivingTrapBlockEntity trapBlockEntity) {
                    trapBlockEntity.releaseOnRemove(serverLevel, pos);
                }
                restoreStrayEntities(serverLevel, pos);
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
    public static void releaseTrappedEntity(LivingEntity entity, ServerLevel level, BlockPos pos) {
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
     * 扫描方块上方 1 格范围内的残留异常实体并恢复其状态。
     * 判定条件：invisible=true && invulnerable=true && noGravity=true 且非旁观者。
     * 这些状态组合极不自然，几乎只可能是活体陷阱造成的。
     */
    public static void restoreStrayEntities(ServerLevel level, BlockPos pos) {
        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(pos).inflate(1.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity.isInvisible() && entity.isInvulnerable() && entity.isNoGravity()
                    && !(entity instanceof net.minecraft.world.entity.player.Player p && p.isSpectator())) {
                entity.setInvisible(false);
                entity.setInvulnerable(false);
                entity.setNoGravity(false);
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0, false, true, true));
            }
        }
    }

    // ==================== EntityBlock 接口实现 ====================

    /**
     * 创建活体陷阱方块实体。
     * Minecraft 在方块加载或放置时调用，用它承载持久化状态与服务端 ticker。
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LivingTrapBlockEntity(pos, state);
    }

    /**
     * 提供服务端方块实体 ticker。
     * 客户端返回 null，避免客户端直接修改实体可见性、无敌状态等服务端权威数据。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SRBlockEntities.LIVING_TRAP.get()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> LivingTrapBlockEntity.serverTick(
                tickerLevel,
                pos,
                tickerState,
                (LivingTrapBlockEntity) blockEntity
        );
    }

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
