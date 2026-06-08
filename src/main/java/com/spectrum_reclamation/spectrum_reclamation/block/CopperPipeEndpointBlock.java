package com.spectrum_reclamation.spectrum_reclamation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.Container;
import com.spectrum_reclamation.spectrum_reclamation.block_entity.CopperPipeEndpointBlockEntity;
import javax.annotation.Nullable;

/**
 * 铜管接口方块 —— 贴在容器上，连接铜管网络与容器。
 *
 * 继承 AbstractCopperPipeBlock，在基类共享逻辑之上提供：
 * - FACING 属性：决定朝向容器的方向（放置时面向玩家）
 * - MODE 属性：入口/出口模式，右键切换
 * - 朝向容器的一面不连接铜管也不添加管道臂
 *
 * 核心机制：
 * - 朝向容器的一面检测容器方块（BlockEntity 实现 Container 接口）
 * - 其余 5 面可连接铜管（通过基类 BooleanProperty 动态管理）
 * - 右键切换模式：入口模式（从容器提取物品 → 传入铜管网络）/ 出口模式（从铜管网络接收物品 → 存入容器）
 * - 支持含水和蜜脾涂蜡（基类实现）
 *
 * 模式说明：
 * - INPUT（入口）：从朝向的容器中提取物品，通过铜管网络传输
 * - OUTPUT（出口）：从铜管网络接收物品，存入朝向的容器
 */
public class CopperPipeEndpointBlock extends AbstractCopperPipeBlock {

    // ==================== 接口专属方块状态属性 ====================

    /** 朝向容器的方向（6 面均可选） */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /**
     * 接口模式。
     * INPUT = 从容器提取物品（入口）
     * OUTPUT = 向容器存入物品（出口）
     * 右键切换。
     */
    public static final EnumProperty<EndpointMode> MODE = EnumProperty.create("mode", EndpointMode.class);

    // ==================== 碰撞箱形状 ====================

    /** 接口方块中心核（略大于铜管，12x12x12 像素，方便贴在容器上） */
    private static final VoxelShape CORE = Block.box(2.0, 2.0, 2.0, 14.0, 14.0, 14.0);

    /** 各方向的连接臂（2 像素长，从较大中心核延伸到方块边界） */
    private static final VoxelShape NORTH_ARM = Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 2.0);
    private static final VoxelShape SOUTH_ARM = Block.box(4.0, 4.0, 14.0, 12.0, 12.0, 16.0);
    private static final VoxelShape WEST_ARM = Block.box(0.0, 4.0, 4.0, 2.0, 12.0, 12.0);
    private static final VoxelShape EAST_ARM = Block.box(14.0, 4.0, 4.0, 16.0, 12.0, 12.0);
    private static final VoxelShape UP_ARM = Block.box(4.0, 14.0, 4.0, 12.0, 16.0, 12.0);
    private static final VoxelShape DOWN_ARM = Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0);

    // ==================== 构造与默认状态 ====================

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

    // ==================== 基类抽象方法实现 ====================

    /** 返回接口方块中心核碰撞箱（12x12x12 像素，比铜管大） */
    @Override
    protected VoxelShape getCoreShape() {
        return CORE;
    }

    /** 返回指定方向的连接臂碰撞箱（2 像素长） */
    @Override
    protected VoxelShape getArmShape(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH_ARM;
            case SOUTH -> SOUTH_ARM;
            case WEST -> WEST_ARM;
            case EAST -> EAST_ARM;
            case UP -> UP_ARM;
            case DOWN -> DOWN_ARM;
        };
    }

    // ==================== 基类钩子方法重写 ====================

    /**
     * 注册接口专属的方块状态属性：FACING 和 MODE。
     * 基类已添加 8 个公共属性，此处追加 FACING 和 MODE。
     * 总计：FACING(6) × MODE(2) × 6方向布尔(2^6) × WATERLOGGED(2) × WAXED(2) = 6144 种变体。
     */
    @Override
    protected void defineAdditionalProperties(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    /**
     * 朝向容器的方向不添加管道臂（那面贴在容器上，无需管道延伸）。
     */
    @Override
    protected boolean shouldAddArm(Direction direction, BlockState state) {
        return direction != state.getValue(FACING);
    }

    /**
     * 朝向容器的方向不参与铜管连接检测（该方向连接容器，不连接铜管）。
     */
    @Override
    protected boolean shouldConnectTo(Direction direction, BlockState state) {
        return direction != state.getValue(FACING);
    }

    // ==================== 放置逻辑重写 ====================

    /**
     * 方块被放置时调用 —— 确定朝向和初始连接状态。
     *
     * 朝向规则：放置时 FACING 朝向玩家看的方向（与玩家面对的方向相反），
     * 即接口背面朝向容器，正面朝向玩家。
     * 这样玩家放置后，接口自然贴在身后的容器上。
     *
     * 先设置 FACING，再调用基类方法完成公共连接检测和含水处理。
     * 基类的 getStateForPlacement 会通过 shouldConnectTo() 自动跳过 FACING 方向。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 先通过基类方法获取初始状态（含连接检测和含水处理）
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;

        // 设置朝向：接口背面朝向玩家看的方向（即容器方向）
        Direction facing = context.getNearestLookingDirection().getOpposite();
        return state.setValue(FACING, facing);
    }

    // ==================== EntityBlock 接口实现 ====================

    /**
     * 创建铜管接口方块实体实例。
     * CopperPipeEndpointBlockEntity 管理接口与容器之间的物品传输。
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopperPipeEndpointBlockEntity(pos, state);
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

            // 通知方块实体更新网络注册
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CopperPipeEndpointBlockEntity endpointBE) {
                endpointBE.onModeChanged();
            }

            // 通知玩家当前模式（使用翻译键）
            String messageKey = newMode == EndpointMode.INPUT
                    ? "message.spectrum_reclamation.pipe_endpoint.input"
                    : "message.spectrum_reclamation.pipe_endpoint.output";
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(messageKey), true);
        }
        return InteractionResult.SUCCESS;
    }

    // ==================== 容器检测 ====================

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
