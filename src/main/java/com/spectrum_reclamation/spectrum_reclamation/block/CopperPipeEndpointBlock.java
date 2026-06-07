package com.spectrum_reclamation.spectrum_reclamation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.Container;

/**
 * 铜管接口方块 —— 贴在容器上，连接铜管网络与容器。
 *
 * 核心机制：
 * - FACING 属性决定朝向容器的方向（放置时面向玩家）
 * - 朝向容器的一面检测容器方块（BlockEntity 实现 Container 接口）
 * - 其余 5 面可连接铜管（通过 BooleanProperty 动态管理）
 * - 右键切换模式：入口模式（从容器提取物品 → 传入铜管网络）/ 出口模式（从铜管网络接收物品 → 存入容器）
 * - 支持含水和蜜脾涂蜡
 *
 * 模式说明：
 * - INPUT（入口）：从朝向的容器中提取物品，通过铜管网络传输
 * - OUTPUT（出口）：从铜管网络接收物品，存入朝向的容器
 */
public class CopperPipeEndpointBlock extends Block implements SimpleWaterloggedBlock {

    // ==================== 方块状态属性定义 ====================

    /** 朝向容器的方向（6 面均可选） */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /**
     * 接口模式。
     * INPUT = 从容器提取物品（入口）
     * OUTPUT = 向容器存入物品（出口）
     * 右键切换。
     */
    public static final EnumProperty<EndpointMode> MODE = EnumProperty.create("mode", EndpointMode.class);

    /** 北面是否连接铜管 */
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    /** 东面是否连接铜管 */
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    /** 南面是否连接铜管 */
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    /** 西面是否连接铜管 */
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    /** 上面是否连接铜管 */
    public static final BooleanProperty UP = BooleanProperty.create("up");
    /** 下面是否连接铜管 */
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    /** 原版含水属性 */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** 自定义属性：是否被蜜脾涂蜡 */
    public static final BooleanProperty WAXED = BooleanProperty.create("waxed");

    /** 方向 → 连接属性映射，方便遍历 */
    private static final java.util.Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION =
            java.util.Map.of(
                    Direction.NORTH, NORTH,
                    Direction.EAST, EAST,
                    Direction.SOUTH, SOUTH,
                    Direction.WEST, WEST,
                    Direction.UP, UP,
                    Direction.DOWN, DOWN
            );

    // ==================== 碰撞箱形状 ====================

    /** 接口方块中心核（略大于铜管，12x12x12 像素，方便贴在容器上） */
    private static final VoxelShape CORE = Block.box(2.0, 2.0, 2.0, 14.0, 14.0, 14.0);

    /** 各方向的连接臂（与铜管一致的 4x4 像素臂） */
    private static final VoxelShape NORTH_ARM = Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 2.0);
    private static final VoxelShape SOUTH_ARM = Block.box(4.0, 4.0, 14.0, 12.0, 12.0, 16.0);
    private static final VoxelShape WEST_ARM = Block.box(0.0, 4.0, 4.0, 2.0, 12.0, 12.0);
    private static final VoxelShape EAST_ARM = Block.box(14.0, 4.0, 4.0, 16.0, 12.0, 12.0);
    private static final VoxelShape UP_ARM = Block.box(4.0, 14.0, 4.0, 12.0, 16.0, 12.0);
    private static final VoxelShape DOWN_ARM = Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0);

    // ==================== 构造与状态定义 ====================

    public CopperPipeEndpointBlock(Properties properties) {
        super(properties);
        // 默认状态：朝南、入口模式、所有方向未连接、不含水、未涂蜡
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.SOUTH)
                        .setValue(MODE, EndpointMode.INPUT)
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

    /**
     * 注册方块状态属性。
     * FACING(6) × MODE(2) × 6方向布尔(2^6) × WATERLOGGED(2) × WAXED(2) = 6144 种变体。
     * 低于 Minecraft 的 2^16 = 65536 上限。
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED, WAXED);
    }

    // ==================== 碰撞箱 ====================

    /**
     * 根据连接状态动态构建碰撞箱。
     * 中心核 + 非朝向方向的连接臂。
     * 朝向容器的方向不添加臂（那面贴在容器上）。
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        Direction facing = state.getValue(FACING);
        // 遍历 6 个方向，朝向容器的方向跳过（不添加管道臂）
        for (Direction direction : Direction.values()) {
            if (direction == facing) continue;
            if (state.getValue(PROPERTY_BY_DIRECTION.get(direction))) {
                shape = Shapes.or(shape, getArmShape(direction));
            }
        }
        return shape;
    }

    // ==================== 放置逻辑 ====================

    /**
     * 方块被放置时调用 —— 确定朝向和初始连接状态。
     *
     * 朝向规则：放置时 FACING 朝向玩家看的方向（与玩家面对的方向相反），
     * 即接口背面朝向容器，正面朝向玩家。
     * 这样玩家放置后，接口自然贴在身后的容器上。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        FluidState fluidState = level.getFluidState(pos);

        // 朝向玩家看的方向的反方向（接口背面朝向容器）
        Direction facing = context.getNearestLookingDirection().getOpposite();
        BlockState state = this.defaultBlockState().setValue(FACING, facing);

        // 根据相邻铜管计算各方向连接（排除朝向容器的方向）
        for (Direction direction : Direction.values()) {
            if (direction == facing) continue; // 朝向容器的方向不连接铜管
            BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);
            state = state.setValue(property, canConnectToPipe(level, pos, direction));
        }

        // 含水处理
        state = state.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);

        return state;
    }

    /**
     * 方块被放置到世界后调用 —— 通知相邻铜管更新连接。
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) {
            // 通知相邻方块（铜管）更新连接状态
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);
                level.blockUpdated(neighborPos, this);
            }
        }
    }

    // ==================== 连接更新逻辑 ====================

    /**
     * 相邻方块变化时调用 —— 更新铜管连接状态。
     * 排除朝向容器的方向（该方向连接容器，不连接铜管）。
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;

        Direction facing = state.getValue(FACING);

        // 计算邻居方向
        Direction direction = getDirectionFromPos(pos, neighborPos);
        if (direction == null) return;

        // 朝向容器的方向不做铜管连接判断
        if (direction == facing) return;

        // 更新该方向的铜管连接状态
        BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);
        boolean canConnect = canConnectToPipe(level, pos, direction);
        if (state.getValue(property) != canConnect) {
            level.setBlock(pos, state.setValue(property, canConnect), 3);
        }
    }

    /**
     * 方块被移除时调用 —— 通知相邻铜管断开连接。
     */
    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!oldState.is(newState.getBlock())) {
            if (!level.isClientSide) {
                // 通知相邻方块更新连接
                for (Direction direction : Direction.values()) {
                    BlockPos neighborPos = pos.relative(direction);
                    level.blockUpdated(neighborPos, this);
                }
            }
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }

    // ==================== 交互逻辑（右键切换模式） ====================

    /**
     * 玩家右键方块时调用 —— 切换入口/出口模式。
     *
     * 右键点击切换模式：
     * - INPUT（入口模式）→ OUTPUT（出口模式）
     * - OUTPUT（出口模式）→ INPUT（入口模式）
     *
     * 播放点击音效作为反馈。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            // 切换模式：INPUT ↔ OUTPUT
            EndpointMode currentMode = state.getValue(MODE);
            EndpointMode newMode = (currentMode == EndpointMode.INPUT)
                    ? EndpointMode.OUTPUT
                    : EndpointMode.INPUT;
            level.setBlock(pos, state.setValue(MODE, newMode), 3);

            // 通知玩家当前模式（通过动作栏消息，屏幕底部经验条上方）
            String message = newMode == EndpointMode.INPUT ? "铜管接口：入口模式" : "铜管接口：出口模式";
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
        }
        return InteractionResult.SUCCESS;
    }

    // ==================== 含水逻辑 ====================

    @Override
    protected FluidState getFluidState(BlockState state) {
        if (state.getValue(WATERLOGGED) && !state.getValue(WAXED)) {
            return Fluids.WATER.getSource(false);
        }
        return super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED) && !state.getValue(WAXED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ==================== 工具方法 ====================

    /**
     * 判断指定方向是否可以连接铜管。
     * 可连接条件：该方向的相邻方块是铜管（CopperPipeBlock）或铜管接口（CopperPipeEndpointBlock）。
     */
    private boolean canConnectToPipe(LevelAccessor level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();
        return neighborBlock instanceof CopperPipeBlock || neighborBlock instanceof CopperPipeEndpointBlock;
    }

    /**
     * 检测朝向方向的方块是否是容器。
     * 容器判定：BlockEntity 实现 Container 接口（如箱子、漏斗、熔炉等）。
     *
     * Container 是 Minecraft 的标准容器接口，
     * 凡是能存储物品的方块实体（箱子、桶、漏斗、发射器等）都实现了此接口。
     * NeoForge 还提供了 IItemHandler capability，但此处使用更通用的 Container 接口。
     *
     * @param level 所在世界
     * @param pos   接口方块位置
     * @param facing 朝向容器的方向
     * @return true 表示朝向方向有容器
     */
    public boolean hasContainer(Level level, BlockPos pos, Direction facing) {
        BlockPos containerPos = pos.relative(facing);
        BlockEntity blockEntity = level.getBlockEntity(containerPos);
        // 检测是否实现了 Container 接口（标准 Minecraft 容器接口）
        return blockEntity instanceof Container;
    }

    /**
     * 根据两个相邻位置计算方向。
     */
    private Direction getDirectionFromPos(BlockPos fromPos, BlockPos toPos) {
        for (Direction direction : Direction.values()) {
            if (fromPos.relative(direction).equals(toPos)) {
                return direction;
            }
        }
        return null;
    }

    /**
     * 根据方向返回对应的连接臂碰撞箱。
     */
    private VoxelShape getArmShape(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH_ARM;
            case SOUTH -> SOUTH_ARM;
            case WEST -> WEST_ARM;
            case EAST -> EAST_ARM;
            case UP -> UP_ARM;
            case DOWN -> DOWN_ARM;
        };
    }

    // ==================== 接口模式枚举 ====================

    /**
     * 铜管接口的工作模式。
     * - INPUT：入口模式，从朝向的容器提取物品，传入铜管网络
     * - OUTPUT：出口模式，从铜管网络接收物品，存入朝向的容器
     *
     * 使用 EnumProperty 管理模式状态，右键切换时通过 setValue 更新。
     */
    public enum EndpointMode implements net.minecraft.util.StringRepresentable {
        /** 入口模式：从容器提取物品 */
        INPUT("input"),
        /** 出口模式：向容器存入物品 */
        OUTPUT("output");

        private final String name;

        EndpointMode(String name) {
            this.name = name;
        }

        /**
         * 返回序列化名称，用于 BlockState 字符串表示和 NBT 持久化。
         * 例如：mode=input 或 mode=output
         */
        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
