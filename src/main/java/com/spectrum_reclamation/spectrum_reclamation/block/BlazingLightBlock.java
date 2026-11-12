package com.spectrum_reclamation.spectrum_reclamation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 炽光灯方块 —— 炽光炸弹着弹时生成的临时光源。
 *
 * 实现方案选择理由（方块方案 vs 粒子方案）：
 * - 方块方案：光源持久稳定，不受客户端粒子设置影响，亮度精确可控
 * - 粒子方案：实现简单但光源不稳定，玩家关闭粒子后完全不可见
 * - 选择方块方案：以可靠性换取少量实现复杂度，30 秒自毁机制避免方块积累
 *
 * 特性：
 * - 不可见：渲染形状为 INVISIBLE，无模型渲染
 * - 无碰撞箱：实体可以自由穿过
 * - 无选择框：玩家无法选中此方块（空 VoxelShape）
 * - 亮度 15：最高光照等级
 * - 30 秒自毁：放置后调度 600 ticks 的延迟刻，到期自动移除
 * - 可被替换：不影响玩家在该位置放置其他方块
 */
public class BlazingLightBlock extends Block {

    /** 自毁延迟 ticks 数（30 秒 = 600 ticks） */
    private static final int SELF_DESTRUCT_DELAY = 600;

    public BlazingLightBlock(Properties properties) {
        super(properties);
    }

    /**
     * 返回空碰撞形状，使方块完全不可见（无选择框、无轮廓线）。
     * noCollission() 属性已移除碰撞箱，此处进一步移除选择框。
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * 返回 INVISIBLE 渲染形状，跳过方块模型渲染。
     * 此方块仅作为光源存在，不需要任何视觉表现。
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * 方块被放置到世界时调用。
     * 调度一个 600 ticks（30 秒）的延迟刻，到期后触发 tick() 方法执行自毁。
     *
     * NeoForge 的延迟刻机制：level.scheduleTick(pos, block, delay) 注册一个
     * 在 delay ticks 后执行的 tick 事件，由 ServerLevel 在 tick 阶段统一调度。
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // 仅服务端调度自毁延迟刻（客户端 scheduleTick 为空操作，但显式检查更清晰）
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, SELF_DESTRUCT_DELAY);
        }
    }

    /**
     * 延迟刻触发时调用 —— 执行自毁。
     * removeBlock(pos, false) 移除方块但不触发战利品掉落（因为已设置 noLootTable）。
     * 此方法仅在服务端（ServerLevel）调用，无需额外的 isClientSide 检查。
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }
}
