package com.spectrum_reclamation.spectrum_reclamation.block_entity;

import com.spectrum_reclamation.spectrum_reclamation.block.CopperPipeEndpointBlock;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRBlockEntities;
import com.spectrum_reclamation.spectrum_reclamation.util.CopperPipeNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * 铜管接口方块实体 —— 管理端口的网络注册。
 *
 * 每个铜管接口方块持有一个 UUID，指向其相邻铜管所在的网络。
 * 端点自身不决定网络归属，而是由相邻铜管同步；这样能处理端点先于铜管加载的情况。
 */
public class CopperPipeEndpointBlockEntity extends BlockEntity {

    /** 此接口关联的铜管网络 UUID（通过相邻铜管推断） */
    private UUID networkId;

    public CopperPipeEndpointBlockEntity(BlockPos pos, BlockState blockState) {
        super(SRBlockEntities.COPPER_PIPE_ENDPOINT.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) return;
        syncWithAdjacentPipeNetwork();
    }

    @Override
    public void setRemoved() {
        if (level == null) {
            super.setRemoved();
            return;
        }
        if (!level.isClientSide) {
            unregisterFromCurrentNetwork();
        }
        super.setRemoved();
    }

    /**
     * 从当前记录的网络中注销此端点。
     * 切换网络前必须先注销旧入口/出口，否则旧网络会保留一个已经迁移的端点位置。
     */
    private void unregisterFromCurrentNetwork() {
        if (networkId == null) return;

        CopperPipeNetwork network = CopperPipeNetwork.get(networkId);
        if (network != null) {
            network.removeEntryPoint(worldPosition);
            network.removeExitPoint(worldPosition);
        }
        networkId = null;
    }

    /**
     * 扫描 6 个方向，找到相邻铜管方块实体，获取其网络 UUID，
     * 然后根据当前模式注册为入口或出口。
     */
    private boolean tryRegisterWithNetwork() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof CopperPipeEndpointBlock)) return false;

        Direction facing = state.getValue(CopperPipeEndpointBlock.FACING);

        // 扫描非 FACING 方向（FACING 朝向容器，不朝向铜管）
        for (Direction dir : Direction.values()) {
            if (dir == facing) continue;
            BlockPos neighborPos = worldPosition.relative(dir);
            if (level.getBlockEntity(neighborPos) instanceof CopperPipeBlockEntity pipeBE) {
                UUID pipeNetId = pipeBE.getNetworkId();
                if (pipeNetId != null) {
                    if (!pipeNetId.equals(this.networkId)) {
                        unregisterFromCurrentNetwork();
                    }
                    this.networkId = pipeNetId;
                    CopperPipeNetwork network = CopperPipeNetwork.getOrCreate(pipeNetId, level.dimension());
                    // 确保此节点也在网络的邻接表中
                    network.addNode(worldPosition, java.util.Set.of(neighborPos));
                    registerAsEntryOrExit(network, state);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 由端点自身加载、玩家切换模式或相邻铜管加载/合并后调用。
     *
     * 这里会重新扫描相邻铜管并同步网络 ID。NeoForge 的方块实体加载顺序不保证端点
     * 一定晚于铜管，因此需要铜管在 onLoad 后主动调用这个方法补齐注册。
     */
    public void syncWithAdjacentPipeNetwork() {
        if (level == null || level.isClientSide) return;
        if (!tryRegisterWithNetwork()) {
            // 未找到相邻铜管时清理旧注册，防止加载顺序或断开连接后留下幽灵端点。
            unregisterFromCurrentNetwork();
        }
    }

    /**
     * 根据当前 BlockState 的 MODE 属性，注册为入口或出口。
     */
    private void registerAsEntryOrExit(CopperPipeNetwork network, BlockState state) {
        // 先清除旧注册
        network.removeEntryPoint(worldPosition);
        network.removeExitPoint(worldPosition);

        CopperPipeEndpointBlock.EndpointMode mode = state.getValue(CopperPipeEndpointBlock.MODE);
        if (mode == CopperPipeEndpointBlock.EndpointMode.INPUT) {
            network.addEntryPoint(worldPosition);
        } else {
            network.addExitPoint(worldPosition);
        }
    }

    /**
     * 当玩家右键切换模式时由 CopperPipeEndpointBlock 调用。
     * 更新网络中的入口/出口注册。
     * 如果当前无网络关联（如方块加载时相邻管道尚未就绪），尝试重新扫描注册。
     */
    public void onModeChanged() {
        if (level == null || level.isClientSide) return;
        if (networkId == null) {
            // 首次或孤立状态：尝试扫描相邻管道并注册
            syncWithAdjacentPipeNetwork();
            return;
        }
        CopperPipeNetwork network = CopperPipeNetwork.get(networkId);
        if (network == null) {
            // 网络已被清除（如合并后旧网络被删除），重新扫描注册
            unregisterFromCurrentNetwork();
            syncWithAdjacentPipeNetwork();
            return;
        }
        registerAsEntryOrExit(network, getBlockState());
    }

    public UUID getNetworkId() {
        return networkId;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (networkId != null) {
            tag.putUUID("NetworkId", networkId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("NetworkId")) {
            networkId = tag.getUUID("NetworkId");
        }
    }
}
