package com.spectrum_reclamation.spectrum_reclamation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 铜管方块的公共基类 —— 提取自 CopperPipeBlock 和 CopperPipeEndpointBlock 的共享逻辑。
 *
 * 包含：
 * - 6 个方向的连接属性（NORTH/EAST/SOUTH/WEST/UP/DOWN）+ WATERLOGGED + WAXED
 * - 连接检测（canConnectTo）与方向计算（getDirectionFromPos）
 * - 碰撞箱动态组合（CORE + 方向臂）
 * - 放置、移除、邻居变化时的连接状态同步
 * - 含水逻辑（getFluidState / updateShape）
 *
 * 子类需要：
 * - 提供 CORE 和各方向臂形状（通过 getCoreShape / getArmShape）
 * - 重写 defineAdditionalProperties() 注册额外属性（如 FACING、MODE）
 * - 重写 newBlockEntity() 创建对应的方块实体
 * - 可选：重写 shouldConnectTo() 限制特定方向的连接（如 Endpoint 跳过 FACING 方向）
 * - 可选：重写 getStateForPlacement() 设置额外放置属性（如 FACING）
 */
public abstract class AbstractCopperPipeBlock extends Block implements SimpleWaterloggedBlock, EntityBlock {

    // ==================== 共享方块状态属性（6 方向连接 + 含水 + 涂蜡） ====================

    /** 北面（-Z）是否连接 */
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    /** 东面（+X）是否连接 */
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    /** 南面（+Z）是否连接 */
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    /** 西面（-X）是否连接 */
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    /** 上面（+Y）是否连接 */
    public static final BooleanProperty UP = BooleanProperty.create("up");
    /** 下面（-Y）是否连接 */
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    /** 原版含水属性（方块是否含水，SimpleWaterloggedBlock 接口要求） */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** 自定义属性：是否被蜜脾涂蜡（涂蜡后含水时不会流水） */
    public static final BooleanProperty WAXED = BooleanProperty.create("waxed");

    /** 方向 → 连接属性映射，方便遍历各方向的连接状态 */
    protected static final java.util.Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION =
            java.util.Map.of(
                    Direction.NORTH, NORTH,
                    Direction.EAST, EAST,
                    Direction.SOUTH, SOUTH,
                    Direction.WEST, WEST,
                    Direction.UP, UP,
                    Direction.DOWN, DOWN
            );

    // ==================== 构造与状态定义 ====================

    protected AbstractCopperPipeBlock(Properties properties) {
        super(properties);
    }

    /**
     * 注册方块状态属性。
     * 先添加 8 个公共属性（6 方向 + WATERLOGGED + WAXED），
     * 再调用 defineAdditionalProperties() 让子类添加自己的属性（如 FACING、MODE）。
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED, WAXED);
        defineAdditionalProperties(builder);
    }

    // ==================== 碰撞箱与渲染形状 ====================

    /**
     * 根据连接状态动态构建碰撞箱。
     * 管道中心核 + 各连接方向的管道臂，组合成不同形状。
     * 无连接时仅有中心核，6 面全连接时为满格。
     *
     * 子类可以通过 shouldAddArm() 控制是否为某个方向添加臂（如 Endpoint 跳过 FACING 方向）。
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = getCoreShape();
        for (Direction direction : Direction.values()) {
            if (shouldAddArm(direction, state) && state.getValue(PROPERTY_BY_DIRECTION.get(direction))) {
                shape = Shapes.or(shape, getArmShape(direction));
            }
        }
        return shape;
    }

    // ==================== 放置逻辑 ====================

    /**
     * 方块被放置时调用 —— 确定放置时的初始状态。
     *
     * 遍历所有方向检测相邻方块类型并建立连接，同时处理含水情况。
     * 子类可重写此方法设置额外属性（如 FACING），然后调用 super 完成公共逻辑。
     *
     * @return 放置时的初始 BlockState，若返回 null 则取消放置
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        FluidState fluidState = level.getFluidState(pos);

        // 初始化状态：根据相邻方块计算各方向连接
        BlockState state = this.defaultBlockState();
        for (var entry : PROPERTY_BY_DIRECTION.entrySet()) {
            Direction direction = entry.getKey();
            BooleanProperty property = entry.getValue();
            state = state.setValue(property, canConnectTo(level, pos, direction));
        }

        // 含水处理：如果放置位置有水，则设置 WATERLOGGED
        state = state.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);

        return state;
    }

    /**
     * 方块被放置到世界后调用 —— 通知相邻方块更新连接。
     *
     * 当玩家放置铜管/铜管接口时，相邻的铜管/铜管接口也需要更新它们的连接状态，
     * 使它们朝向新放置的方块建立连接。
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) {
            updateNeighborConnections(level, pos);
        }
    }

    // ==================== 连接更新逻辑 ====================

    /**
     * 相邻方块变化时调用 —— 重新计算此方块的连接状态。
     *
     * 当相邻方块发生变化（放置、破坏、状态更新）时，Minecraft 会通知当前方块。
     * 在此重新检测该方向的连接状态，通过 shouldConnectTo() 钩子过滤不需要连接的方向。
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;

        Direction direction = getDirectionFromPos(pos, neighborPos);
        if (direction == null) return;

        // 通过钩子判断是否需要连接此方向（Endpoint 会跳过 FACING 方向）
        if (!shouldConnectTo(direction, state)) return;

        // 更新该方向的连接状态
        BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);
        boolean canConnect = canConnectTo(level, pos, direction);
        if (state.getValue(property) != canConnect) {
            // 状态发生变化，flag=3：通知客户端 + 发送方块更新
            level.setBlock(pos, state.setValue(property, canConnect), 3);
        }
    }

    /**
     * 方块被移除时调用 —— 通知相邻铜管更新连接 + 从网络中移除自身。
     *
     * 当铜管/接口被破坏时，相邻的铜管需要断开朝向此方块的连接。
     * 同时从管道网络中移除自身（由 CopperPipeBlockEntity.setRemoved() 处理）。
     */
    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!oldState.is(newState.getBlock())) {
            if (!level.isClientSide) {
                // 通知相邻方块更新连接（它们会发现此位置已不是铜管/接口）
                updateNeighborConnections(level, pos);
            }
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }

    // ==================== 含水逻辑（SimpleWaterloggedBlock 接口实现） ====================

    /**
     * 含水状态下的流体状态。
     * 如果方块含水且未被蜜腊涂蜡，返回水的流体状态。
     * 涂蜡后即使含水也不会流出水（waxed=true 时返回空流体）。
     */
    @Override
    protected FluidState getFluidState(BlockState state) {
        if (state.getValue(WATERLOGGED) && !state.getValue(WAXED)) {
            return Fluids.WATER.getSource(false);
        }
        return super.getFluidState(state);
    }

    /**
     * 处理含水方块的更新（如被放置、被破坏时的水流逻辑）。
     * SimpleWaterloggedBlock 接口要求此方法处理流体传播。
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED) && !state.getValue(WAXED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ==================== 连接检测 ====================

    /**
     * 判断指定方向是否可以连接。
     * 可连接条件：该方向的相邻方块是铜管（CopperPipeBlock）或铜管接口（CopperPipeEndpointBlock）。
     *
     * 使用 instanceof 判断类型，避免硬编码依赖——未来若有更多可连接方块，
     * 可改为接口或 Tag 判断。
     *
     * @param level     所在世界
     * @param pos       当前方块位置
     * @param direction 要检测的方向
     * @return true 表示该方向可连接
     */
    protected boolean canConnectTo(LevelAccessor level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();
        return neighborBlock instanceof AbstractCopperPipeBlock;
    }

    // ==================== 抽象方法（子类必须实现） ====================

    /** 提供管道中心核的碰撞箱形状（子类自定义尺寸） */
    protected abstract VoxelShape getCoreShape();

    /** 提供指定方向的管道臂碰撞箱形状（子类自定义尺寸） */
    protected abstract VoxelShape getArmShape(Direction direction);

    /**
     * 创建方块实体实例（EntityBlock 接口要求）。
     * 子类根据自身类型创建对应的 BlockEntity。
     */
    @Override
    public abstract net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state);

    // ==================== 钩子方法（子类可选重写） ====================

    /**
     * 子类注册额外的方块状态属性。
     * 例如 Endpoint 需要添加 FACING 和 MODE。
     * 默认实现为空（CopperPipeBlock 无需额外属性）。
     */
    protected void defineAdditionalProperties(StateDefinition.Builder<Block, BlockState> builder) {
        // 默认无额外属性，子类按需重写
    }

    /**
     * 控制指定方向是否应添加管道臂。
     * Endpoint 会重写此方法，对 FACING 方向返回 false（朝向容器的一面不加臂）。
     * 默认返回 true（所有方向都添加臂）。
     */
    protected boolean shouldAddArm(Direction direction, BlockState state) {
        return true;
    }

    /**
     * 控制指定方向是否参与连接检测。
     * Endpoint 会重写此方法，对 FACING 方向返回 false（朝向容器的方向不连接铜管）。
     * 默认返回 true（所有方向都参与连接）。
     */
    protected boolean shouldConnectTo(Direction direction, BlockState state) {
        return true;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 通知 6 个方向的相邻方块更新连接状态。
     * 通过调用 blockUpdated 触发邻居的 neighborChanged。
     */
    protected void updateNeighborConnections(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            level.blockUpdated(neighborPos, this);
        }
    }

    /**
     * 根据两个相邻方块的位置计算方向。
     * 返回从 fromPos 指向 toPos 的方向。
     *
     * @param fromPos 起始位置（当前方块）
     * @param toPos   目标位置（邻居方块）
     * @return 方向枚举，若位置不相邻则返回 null
     */
    protected Direction getDirectionFromPos(BlockPos fromPos, BlockPos toPos) {
        for (Direction direction : Direction.values()) {
            if (fromPos.relative(direction).equals(toPos)) {
                return direction;
            }
        }
        return null;
    }
}
