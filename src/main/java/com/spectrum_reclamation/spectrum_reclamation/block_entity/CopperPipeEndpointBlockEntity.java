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
 * 在 onLoad 时根据自身模式（INPUT/OUTPUT）将自己注册为网络的入口或出口。
 * 在模式切换时更新注册。在被移除时注销注册。
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
        tryRegisterWithNetwork();
    }

    @Override
    public void setRemoved() {
        if (level == null) {
            super.setRemoved();
            return;
        }
        if (!level.isClientSide && networkId != null) {
            CopperPipeNetwork network = CopperPipeNetwork.get(networkId);
            if (network != null) {
                network.removeEntryPoint(worldPosition);
                network.removeExitPoint(worldPosition);
            }
        }
        super.setRemoved();
    }

    /**
     * 扫描 6 个方向，找到相邻铜管方块实体，获取其网络 UUID，
     * 然后根据当前模式注册为入口或出口。
     */
    private void tryRegisterWithNetwork() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof CopperPipeEndpointBlock)) return;

        Direction facing = state.getValue(CopperPipeEndpointBlock.FACING);

        // 扫描非 FACING 方向（FACING 朝向容器，不朝向铜管）
        for (Direction dir : Direction.values()) {
            if (dir == facing) continue;
            BlockPos neighborPos = worldPosition.relative(dir);
            if (level.getBlockEntity(neighborPos) instanceof CopperPipeBlockEntity pipeBE) {
                UUID pipeNetId = pipeBE.getNetworkId();
                if (pipeNetId != null) {
                    this.networkId = pipeNetId;
                    CopperPipeNetwork network = CopperPipeNetwork.getOrCreate(pipeNetId, level.dimension());
                    // 确保此节点也在网络的邻接表中
                    network.addNode(worldPosition, java.util.Set.of(neighborPos));
                    registerAsEntryOrExit(network, state);
                    return;
                }
            }
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
     */
    public void onModeChanged() {
        if (level == null || level.isClientSide) return;
        if (networkId == null) {
            // 首次，尝试注册
            tryRegisterWithNetwork();
            return;
        }
        CopperPipeNetwork network = CopperPipeNetwork.get(networkId);
        if (network != null) {
            registerAsEntryOrExit(network, getBlockState());
        }
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
