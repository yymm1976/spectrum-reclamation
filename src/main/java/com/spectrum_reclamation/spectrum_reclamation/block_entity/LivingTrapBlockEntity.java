package com.spectrum_reclamation.spectrum_reclamation.block_entity;

import com.spectrum_reclamation.spectrum_reclamation.block.LivingTrapBlock;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * 活体陷阱方块实体 —— 保存被困实体 UUID 和阶段计时，保证区块保存/服务器重启后状态不丢失。
 *
 * NeoForge 会把 BlockEntity 的 NBT 写入区块存档；这里不再使用静态 Map，避免重启后实体引用消失。
 */
public class LivingTrapBlockEntity extends BlockEntity {

    /** 被吞入实体的 UUID；为 null 表示当前没有实体处于吞入阶段。 */
    private UUID trappedEntityId;

    /** 吞入阶段剩余 tick；归零后释放实体并进入冷却。 */
    private int swallowTicksRemaining;

    /** 释放后的冷却剩余 tick；归零后方块恢复待命。 */
    private int cooldownTicksRemaining;

    /** 待命扫描间隔计时，避免每 tick 扫描实体造成不必要开销。 */
    private int scanTicksRemaining = LivingTrapBlock.SCAN_INTERVAL;

    /** 实体短暂未加载时的重试次数，避免区块加载顺序导致陷阱误判实体丢失。 */
    private int trappedEntityLookupRetries;

    /** UUID 查询失败后的最大等待 tick 数，给实体加载和索引注册留出短暂时间。 */
    private static final int MAX_TRAPPED_ENTITY_LOOKUP_RETRIES = 20;

    public LivingTrapBlockEntity(BlockPos pos, BlockState blockState) {
        super(SRBlockEntities.LIVING_TRAP.get(), pos, blockState);
    }

    /**
     * 服务端 tick 入口。
     * 方块实体 ticker 每 tick 调用一次；客户端不执行陷阱逻辑，避免两端状态重复修改。
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, LivingTrapBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (blockEntity.trappedEntityId != null) {
            blockEntity.tickSwallowing(serverLevel, pos, state);
            return;
        }

        if (blockEntity.cooldownTicksRemaining > 0 || state.getValue(LivingTrapBlock.COOLDOWN)) {
            blockEntity.tickCooldown(serverLevel, pos, state);
            return;
        }

        blockEntity.tickScanning(serverLevel, pos, state);
    }

    /**
     * 吞入阶段倒计时。
     * 若 UUID 短暂找不到实体，先重试一小段时间，避免区块加载顺序让实体索引晚于方块实体恢复。
     */
    private void tickSwallowing(ServerLevel level, BlockPos pos, BlockState state) {
        LivingEntity trapped = findTrappedEntity(level, pos);
        if (trapped == null || !trapped.isAlive()) {
            if (trappedEntityLookupRetries < MAX_TRAPPED_ENTITY_LOOKUP_RETRIES) {
                trappedEntityLookupRetries++;
                setChanged();
                return;
            }

            LivingTrapBlock.restoreStrayEntities(level, pos);
            clearTrappedState();
            startCooldown(level, pos, state);
            return;
        }

        trappedEntityLookupRetries = 0;
        swallowTicksRemaining--;
        if (swallowTicksRemaining > 0) {
            setChanged();
            return;
        }

        LivingTrapBlock.releaseTrappedEntity(trapped, level, pos);
        clearTrappedState();
        startCooldown(level, pos, state);
    }

    /**
     * 冷却阶段倒计时。
     * 冷却归零时同步把方块状态 COOLDOWN 复位，模型和触发逻辑都会恢复待命。
     */
    private void tickCooldown(ServerLevel level, BlockPos pos, BlockState state) {
        if (cooldownTicksRemaining > 0) {
            cooldownTicksRemaining--;
            setChanged();
        }

        if (cooldownTicksRemaining <= 0) {
            cooldownTicksRemaining = 0;
            if (state.getValue(LivingTrapBlock.COOLDOWN)) {
                LivingTrapBlock.restoreStrayEntities(level, pos);
                level.setBlock(pos, state.setValue(LivingTrapBlock.COOLDOWN, false), 3);
            }
            setChanged();
        }
    }

    /**
     * 待命阶段按间隔扫描小型生物。
     * 使用方块实体 tick 替代 scheduleTick，区块重新加载后会自动继续运行。
     */
    private void tickScanning(ServerLevel level, BlockPos pos, BlockState state) {
        scanTicksRemaining--;
        if (scanTicksRemaining > 0) return;

        scanTicksRemaining = LivingTrapBlock.SCAN_INTERVAL;
        LivingTrapBlock.scanAndTrapEntity(level, pos, state);
    }

    /**
     * 记录新吞入的实体，并立即写脏数据，确保下一次区块保存会包含 UUID 与剩余时间。
     */
    public void beginSwallowing(LivingEntity entity) {
        trappedEntityId = entity.getUUID();
        swallowTicksRemaining = LivingTrapBlock.SWALLOW_DURATION;
        cooldownTicksRemaining = 0;
        scanTicksRemaining = LivingTrapBlock.SCAN_INTERVAL;
        trappedEntityLookupRetries = 0;
        setChanged();
    }

    /**
     * 方块被破坏或替换时调用，释放当前被困实体，避免实体永久不可见/无敌/无重力。
     */
    public void releaseOnRemove(ServerLevel level, BlockPos pos) {
        if (trappedEntityId != null) {
            LivingEntity trapped = findTrappedEntity(level, pos);
            if (trapped != null) {
                LivingTrapBlock.releaseTrappedEntity(trapped, level, pos);
            }
            clearTrappedState();
        }
        cooldownTicksRemaining = 0;
        setChanged();
    }

    /**
     * 从 UUID 找回被困实体。
     * 优先使用 ServerLevel 全局 UUID 查询；失败时再扫描附近异常实体，兼容旧存档残留状态。
     */
    private LivingEntity findTrappedEntity(ServerLevel level, BlockPos pos) {
        Entity byUuid = level.getEntity(trappedEntityId);
        if (byUuid instanceof LivingEntity living) {
            return living;
        }

        AABB searchBox = new AABB(pos).inflate(2.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (trappedEntityId.equals(entity.getUUID())) {
                return entity;
            }
        }
        return null;
    }

    /** 清空吞入阶段数据，避免旧 UUID 在后续保存中继续存在。 */
    private void clearTrappedState() {
        trappedEntityId = null;
        swallowTicksRemaining = 0;
        trappedEntityLookupRetries = 0;
        setChanged();
    }

    /** 进入释放后的冷却阶段，并保持方块状态 COOLDOWN=true。 */
    private void startCooldown(ServerLevel level, BlockPos pos, BlockState state) {
        cooldownTicksRemaining = LivingTrapBlock.COOLDOWN_DURATION;
        if (!state.getValue(LivingTrapBlock.COOLDOWN)) {
            level.setBlock(pos, state.setValue(LivingTrapBlock.COOLDOWN, true), 3);
        }
        setChanged();
    }

    /**
     * 保存陷阱运行状态到 NBT。
     * HolderLookup.Provider 是 1.21.x 方块实体持久化签名要求，此处仅传给父类即可。
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (trappedEntityId != null) {
            tag.putUUID("TrappedEntityId", trappedEntityId);
        }
        tag.putInt("SwallowTicksRemaining", swallowTicksRemaining);
        tag.putInt("CooldownTicksRemaining", cooldownTicksRemaining);
        tag.putInt("ScanTicksRemaining", scanTicksRemaining);
        tag.putInt("TrappedEntityLookupRetries", trappedEntityLookupRetries);
    }

    /** 从 NBT 恢复陷阱运行状态，保证区块重新加载后继续释放/冷却流程。 */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        trappedEntityId = tag.hasUUID("TrappedEntityId") ? tag.getUUID("TrappedEntityId") : null;
        swallowTicksRemaining = tag.getInt("SwallowTicksRemaining");
        cooldownTicksRemaining = tag.getInt("CooldownTicksRemaining");
        scanTicksRemaining = Math.max(1, tag.getInt("ScanTicksRemaining"));
        trappedEntityLookupRetries = tag.getInt("TrappedEntityLookupRetries");
    }
}
