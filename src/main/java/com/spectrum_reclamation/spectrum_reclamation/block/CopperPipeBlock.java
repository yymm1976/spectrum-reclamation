package com.spectrum_reclamation.spectrum_reclamation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.spectrum_reclamation.spectrum_reclamation.block_entity.CopperPipeBlockEntity;
import javax.annotation.Nullable;

/**
 * 铜管方块 —— 用于构建管道网络，可自动连接相邻的铜管或铜管接口。
 *
 * 继承 AbstractCopperPipeBlock，提供标准铜管的形状定义和方块实体工厂。
 * 连接检测、放置逻辑、邻居更新、含水处理等公共逻辑由基类统一实现。
 *
 * 核心机制：
 * - 6 个面各有一个 BooleanProperty 表示是否与相邻方块连接（基类定义）
 * - 放置时自动检测相邻方块类型并建立连接（基类实现）
 * - 相邻方块变化时自动更新连接状态（基类实现）
 * - 被破坏时通知相邻铜管更新连接 + 从网络中移除自身（基类实现）
 * - 支持含水（WATERLOGGED）和蜜脾涂蜡（WAXED）（基类实现）
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
public class CopperPipeBlock extends AbstractCopperPipeBlock {

    // ==================== 碰撞箱形状（管道形状，中间核 + 6 个方向臂） ====================

    /** 管道中心核（4x4x4 像素的立方体，位于方块中央） */
    private static final VoxelShape CORE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);

    /** 各方向的管道臂（4 像素长，从中心核延伸到方块边界） */
    private static final VoxelShape NORTH_ARM = Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 4.0);
    private static final VoxelShape SOUTH_ARM = Block.box(4.0, 4.0, 12.0, 12.0, 12.0, 16.0);
    private static final VoxelShape WEST_ARM = Block.box(0.0, 4.0, 4.0, 4.0, 12.0, 12.0);
    private static final VoxelShape EAST_ARM = Block.box(12.0, 4.0, 4.0, 16.0, 12.0, 12.0);
    private static final VoxelShape UP_ARM = Block.box(4.0, 12.0, 4.0, 12.0, 16.0, 12.0);
    private static final VoxelShape DOWN_ARM = Block.box(4.0, 0.0, 4.0, 12.0, 4.0, 12.0);

    // ==================== 构造与默认状态 ====================

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

    // ==================== 形状定义（基类抽象方法实现） ====================

    /** 返回铜管中心核碰撞箱（4x4x4 像素） */
    @Override
    protected VoxelShape getCoreShape() {
        return CORE;
    }

    /** 返回指定方向的管道臂碰撞箱 */
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
}
