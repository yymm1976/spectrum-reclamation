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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.spectrum_reclamation.spectrum_reclamation.block_entity.CopperPipeBlockEntity;
import javax.annotation.Nullable;

/**
 * 铜管方块 —— 用于构建管道网络，可自动连接相邻的铜管或铜管接口。
 *
 * 核心机制：
 * - 6 个面各有一个 BooleanProperty 表示是否与相邻方块连接
 * - 放置时自动检测相邻方块类型并建立连接
 * - 相邻方块变化时自动更新连接状态
 * - 被破坏时通知相邻铜管更新连接 + 从网络中移除自身
 * - 支持含水（WATERLOGGED）和蜜脾涂蜡（WAXED）
 * - 实现 EntityBlock 接口，每个铜管方块关联一个 CopperPipeBlockEntity（持有网络 UUID）
 *
 * BlockState 序列化说明：
 * 6 个 BooleanProperty + WATERLOGGED + WAXED = 8 个布尔属性，
 * 产生 2^8 = 256 种状态变体（远低于 Minecraft 的 2^16 = 65536 上限）。
 * 持久化时以字符串形式存储在区块 NBT 中，
 * 例如："spectrum_reclamation:copper_pipe[north=true,east=false,south=false,west=true,up=false,down=true,waterlogged=false,waxed=false]"
 * 每个属性独立序列化为 "属性名=值"，逗号分隔，方括号包裹。
 * 反序列化时由 Minecraft 的 BlockStateParser 解析字符串还原状态。
 */
public class CopperPipeBlock extends Block implements SimpleWaterloggedBlock, EntityBlock {

    // ==================== 方块状态属性定义 ====================

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

    /** 所有 6 个方向的连接属性映射，方便遍历 */
    private static final java.util.Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION =
            java.util.Map.of(
                    Direction.NORTH, NORTH,
                    Direction.EAST, EAST,
                    Direction.SOUTH, SOUTH,
                    Direction.WEST, WEST,
                    Direction.UP, UP,
                    Direction.DOWN, DOWN
            );

    // ==================== 碰撞箱形状（管道形状，中间十字 + 6 个方向臂） ====================

    /** 管道中心核（4x4x4 像素的立方体，位于方块中央） */
    private static final VoxelShape CORE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);

    /** 各方向的管道臂（4x4x4 像素，从中心延伸到对应面） */
    private static final VoxelShape NORTH_ARM = Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 4.0);
    private static final VoxelShape SOUTH_ARM = Block.box(4.0, 4.0, 12.0, 12.0, 12.0, 16.0);
    private static final VoxelShape WEST_ARM = Block.box(0.0, 4.0, 4.0, 4.0, 12.0, 12.0);
    private static final VoxelShape EAST_ARM = Block.box(12.0, 4.0, 4.0, 16.0, 12.0, 12.0);
    private static final VoxelShape UP_ARM = Block.box(4.0, 12.0, 4.0, 12.0, 16.0, 12.0);
    private static final VoxelShape DOWN_ARM = Block.box(4.0, 0.0, 4.0, 12.0, 4.0, 12.0);

    // ==================== 构造与状态定义 ====================

    public CopperPipeBlock(Properties properties) {
        super(properties);
        // 设置默认状态：所有方向未连接，不含水，未涂蜡
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(NORTH, false)
                        .setValue(EAST, false)
                        .setValue(SOUTH, false)
                        .setValue(WEST, false)
                        .setValue(UP, false)
                        .setValue(DOWN, false)
                        .setValue(WATERLOGGED, false)
                        .setValue(WAXED, false)
        );
    }

    // ==================== EntityBlock 接口实现（方块实体工厂） ====================

    /**
     * 创建铜管方块实体实例。
     *
     * EntityBlock 接口要求实现此方法，Minecraft 引擎在加载方块时调用，
     * 用于创建与方块关联的 BlockEntity 实例。
     *
     * CopperPipeBlockEntity 在其 onLoad 回调中会自动注册到铜管网络。
     *
     * @param pos   方块位置
     * @param state 方块状态
     * @return 新的 CopperPipeBlockEntity 实例
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopperPipeBlockEntity(pos, state);
    }

    /**
     * 注册方块状态属性。
     * 6 个方向连接 + 含水 + 涂蜡 = 8 个布尔属性，共 256 种状态变体。
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED, WAXED);
    }

    // ==================== 碰撞箱与渲染形状 ====================

    /**
     * 根据连接状态动态构建碰撞箱。
     * 管道中心核 + 各连接方向的管道臂，组合成不同形状。
     * 无连接时仅有中心核，6 面全连接时为满格。
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) shape = Shapes.or(shape, NORTH_ARM);
        if (state.getValue(EAST)) shape = Shapes.or(shape, EAST_ARM);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, SOUTH_ARM);
        if (state.getValue(WEST)) shape = Shapes.or(shape, WEST_ARM);
        if (state.getValue(UP)) shape = Shapes.or(shape, UP_ARM);
        if (state.getValue(DOWN)) shape = Shapes.or(shape, DOWN_ARM);
        return shape;
    }

    // ==================== 放置逻辑 ====================

    /**
     * 方块被放置时调用 —— 确定放置时的初始状态。
     *
     * BlockPlaceContext 包含放置方向、放置位置等信息。
     * 此处计算放置时各方向的连接状态，并处理含水情况。
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
     * 当玩家放置一个铜管时，相邻的铜管/铜管接口也需要更新它们的连接状态，
     * 使它们朝向新放置的铜管建立连接。
     *
     * NeoForge 的 onPlace 机制：方块被成功放置到世界后调用，
     * movedByPiston 为 true 表示因活塞推动而放置（此处无需特殊处理）。
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) {
            // 通知相邻方块更新连接状态
            updateNeighborConnections(level, pos);
        }
    }

    // ==================== 连接更新逻辑 ====================

    /**
     * 相邻方块变化时调用 —— 重新计算此方块的连接状态。
     *
     * NeoForge 的 neighborChanged 机制：当相邻方块发生变化（放置、破坏、状态更新）时，
     * Minecraft 会通知当前方块。在此重新检测每个方向的连接状态。
     *
     * @param neighborPos 发生变化的邻居位置（用于判断方向）
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;

        // 计算邻居方向（邻居位置相对于当前位置的方向）
        Direction direction = getDirectionFromPos(pos, neighborPos);
        if (direction == null) return;

        // 更新该方向的连接状态
        BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);
        boolean canConnect = canConnectTo(level, pos, direction);
        if (state.getValue(property) != canConnect) {
            // 状态发生变化，更新方块状态（flag=3：通知客户端 + 发送方块更新）
            level.setBlock(pos, state.setValue(property, canConnect), 3);
        }
    }

    /**
     * 方块被移除时调用 —— 通知相邻铜管更新连接 + 从网络中移除自身。
     *
     * 当铜管被破坏时，相邻的铜管需要断开朝向此方块的连接。
     * 同时从管道网络中移除自身（目前为占位逻辑，待网络系统实现后补充）。
     */
    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        // 仅在方块真正被替换为不同类型时执行清理
        if (!oldState.is(newState.getBlock())) {
            if (!level.isClientSide) {
                // 通知相邻方块更新连接（它们会发现此位置已不是铜管）
                updateNeighborConnections(level, pos);

                // 从管道网络中移除自身：由 CopperPipeBlockEntity.setRemoved() 处理，
                // 该回调在方块实体被移除时自动调用 network.removeNode(worldPosition)
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
        // 如果含水且未涂蜡，安排流体 tick 以处理水流
        if (state.getValue(WATERLOGGED) && !state.getValue(WAXED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ==================== 工具方法 ====================

    /**
     * 判断指定方向是否可以连接。
     * 可连接条件：该方向的相邻方块是铜管（CopperPipeBlock）或铜管接口（CopperPipeEndpointBlock）。
     *
     * 使用 instanceof 判断类型，避免硬编码依赖——未来若有更多可连接方块，
     * 可改为接口或 Tag 判断。
     *
     * @param level     所在世界
     * @param pos       当前铜管位置
     * @param direction 要检测的方向
     * @return true 表示该方向可连接
     */
    private boolean canConnectTo(LevelAccessor level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();
        // 连接铜管或铜管接口
        return neighborBlock instanceof CopperPipeBlock || neighborBlock instanceof CopperPipeEndpointBlock;
    }

    /**
     * 通知 6 个方向的相邻方块更新连接状态。
     * 通过调用 neighborChanged 使相邻方块重新检测连接。
     */
    private void updateNeighborConnections(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            // 使用 blockUpdated 触发邻居的 neighborChanged
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
    private Direction getDirectionFromPos(BlockPos fromPos, BlockPos toPos) {
        for (Direction direction : Direction.values()) {
            if (fromPos.relative(direction).equals(toPos)) {
                return direction;
            }
        }
        return null;
    }
}
