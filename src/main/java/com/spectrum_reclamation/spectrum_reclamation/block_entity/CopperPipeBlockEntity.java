package com.spectrum_reclamation.spectrum_reclamation.block_entity;

import com.spectrum_reclamation.spectrum_reclamation.block.CopperPipeBlock;
import com.spectrum_reclamation.spectrum_reclamation.block.CopperPipeEndpointBlock;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRBlockEntities;
import com.spectrum_reclamation.spectrum_reclamation.util.CopperPipeNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 铜管方块实体 —— 每个铜管方块持有一个网络 UUID，用于标识其所属的铜管网络。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>持有并持久化 {@code networkId}（写入 NBT，跨世界保存/加载保持不变）</li>
 *   <li>在方块实体首次加载时，将自身注册到 {@link CopperPipeNetwork} 静态管理器</li>
 *   <li>在方块实体被永久移除时（方块被破坏），从网络中注销自身</li>
 *   <li>不存储物品 —— 物品在网络入口/出口之间"瞬移"</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <ol>
 *   <li>构造：创建方块实体实例，UUID 此时为 null</li>
 *   <li>首次 onLoad：如果 UUID 为 null，生成新 UUID；注册到静态网络</li>
 *   <li>后续 onLoad（区块重新加载）：使用已保存的 UUID 恢复网络注册</li>
 *   <li>setRemoved（方块被破坏）：从网络中注销，断开所有邻居连接</li>
 * </ol>
 *
 * <h2>NBT 持久化</h2>
 * <p>
 *   networkId 以两个 long 值（mostSigBits / leastSigBits）的形式存储在方块实体的 NBT 中，
 *   确保跨世界保存/加载后 UUID 保持不变。
 * </p>
 */
public class CopperPipeBlockEntity extends BlockEntity {

    /**
     * 此铜管所属网络的 UUID。
     * 首次放置时随机生成，之后通过 NBT 持久化，跨世界加载保持不变。
     * null 表示尚未初始化（首次放置后在 onLoad 中生成）。
     */
    private UUID networkId;

    public CopperPipeBlockEntity(BlockPos pos, BlockState blockState) {
        super(SRBlockEntities.COPPER_PIPE.get(), pos, blockState);
    }

    // ==================== NBT 持久化 ====================

    /**
     * 保存方块实体数据到 NBT。
     * 当区块被保存时调用，将 networkId 持久化到磁盘。
     *
     * UUID 在 NBT 中的存储格式：
     * - "NetworkIdMost": long 值（UUID 的高 64 位）
     * - "NetworkIdLeast": long 值（UUID 的低 64 位）
     *
     * HolderLookup.Provider 用于 1.21.x 的注册表查找（如物品、方块的 Holder），
     * 此处不涉及注册表引用，仅需传递给 super。
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (networkId != null) {
            tag.putUUID("NetworkId", networkId);
        }
    }

    /**
     * 从 NBT 加载方块实体数据。
     * 当区块从磁盘加载时调用，恢复 networkId。
     *
     * 如果 NBT 中没有 NetworkId 标签（旧存档或新放置），
     * networkId 保持 null，将在 onLoad 中生成新 UUID。
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("NetworkId")) {
            networkId = tag.getUUID("NetworkId");
        }
    }

    // ==================== 生命周期回调 ====================

    /**
     * 方块实体首次加载时调用 —— 注册到铜管网络。
     *
     * NeoForge 的 onLoad 机制：当方块实体首次被加载到世界中时调用，
     * 包括首次放置和区块重新加载两种情况。
     *
     * 首次放置时 networkId 为 null，生成新 UUID 并创建新网络；
     * 区块重新加载时 networkId 已从 NBT 恢复，直接加入已有网络。
     *
     * 注册后，扫描 6 个方向的邻居，将已连接的铜管/接口加入邻接表。
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) return;

        // 首次放置时生成新 UUID
        if (networkId == null) {
            networkId = UUID.randomUUID();
        }

        // 注册到静态网络管理器（若网络不存在则创建）
        CopperPipeNetwork network = CopperPipeNetwork.getOrCreate(networkId, level.dimension());
        network.addNode(worldPosition, findConnectedNeighbors());

        // 扫描相邻端点方块，将它们注册到网络的入口/出口
        registerAdjacentEndpoints(network);
    }

    /**
     * 扫描 6 个方向，找到相邻的 CopperPipeEndpointBlock 方块实体，
     * 确保它们的入口/出口已在网络中注册。
     */
    private void registerAdjacentEndpoints(CopperPipeNetwork network) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(direction);
            if (level.getBlockEntity(neighborPos) instanceof CopperPipeEndpointBlockEntity endpointBE) {
                // 触发端点的网络注册（端点会自行检查模式并注册为入口或出口）
                endpointBE.onModeChanged();
            }
        }
    }

    /**
     * 方块实体被移除时调用 —— 从铜管网络中注销。
     *
     * NeoForge 的 setRemoved 机制：当方块实体被永久移除时调用。
     * 在铜管场景中，触发条件是铜管方块被玩家破坏或被爆炸摧毁。
     *
     * 从网络中移除自身节点后，网络会自动断开与该节点的所有边。
     * 如果网络中所有节点都被移除，网络实例会自动销毁。
     *
     * 注意：区块卸载（UNLOAD_CHUNK）也会调用 setRemoved，
     * 但此时 networkId 已持久化到 NBT，下次加载时会自动恢复。
     */
    @Override
    public void setRemoved() {
        // 防止区块卸载时 level 为 null 导致 NPE
        if (level == null) {
            super.setRemoved();
            return;
        }
        // 仅在服务端且 networkId 已初始化时执行
        if (!level.isClientSide && networkId != null) {
            CopperPipeNetwork network = CopperPipeNetwork.get(networkId);
            if (network != null) {
                network.removeNode(worldPosition);
            }
        }
        super.setRemoved();
    }

    // ==================== 工具方法 ====================

    /**
     * 获取此铜管所属网络的 UUID。
     * 可能为 null（方块实体刚创建但尚未 onLoad 时）。
     */
    public UUID getNetworkId() {
        return networkId;
    }

    /**
     * 扫描 6 个方向的邻居，返回所有已连接的铜管/接口位置。
     *
     * 连接判定逻辑：
     * - 邻居方块是 CopperPipeBlock 或 CopperPipeEndpointBlock → 连接
     * - 铜管（CopperPipeBlock）6 个方向均可连接
     * - 铜管接口（CopperPipeEndpointBlock）面向容器的方向不连接铜管
     *
     * @return 所有已连接邻居的位置集合
     */
    private Set<BlockPos> findConnectedNeighbors() {
        Set<BlockPos> neighbors = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            Block neighborBlock = neighborState.getBlock();

            // 铜管方块：所有 6 个方向均可连接
            if (neighborBlock instanceof CopperPipeBlock) {
                neighbors.add(neighborPos);
            }
            // 铜管接口：面向容器的方向不连接铜管（该方向连接容器）
            else if (neighborBlock instanceof CopperPipeEndpointBlock) {
                Direction endpointFacing = neighborState.getValue(CopperPipeEndpointBlock.FACING);
                // 接口的 FACING 朝向容器，其反方向才是面向铜管的方向
                // 从当前铜管看邻居接口时，只有非 FACING 方向才连接
                if (direction != endpointFacing) {
                    neighbors.add(neighborPos);
                }
            }
        }

        return neighbors;
    }
}
