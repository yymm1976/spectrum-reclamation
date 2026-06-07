package com.spectrum_reclamation.spectrum_reclamation.event;

import com.spectrum_reclamation.spectrum_reclamation.block.CopperPipeEndpointBlock;
import com.spectrum_reclamation.spectrum_reclamation.util.CopperPipeNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.*;

/**
 * 铜管传输事件处理器 —— 在服务端每 tick 监听 {@link ServerTickEvent}，
 * 每 20 ticks（1 秒）扫描所有铜管网络，执行入口→出口的物品自动传输。
 *
 * <h2>传输流程</h2>
 * <ol>
 *   <li>每 20 ticks 遍历所有铜管网络</li>
 *   <li>对每个网络，遍历所有入口（INPUT 模式的铜管接口）</li>
 *   <li>检查入口朝向的容器是否有物品</li>
 *   <li>如有物品，提取一组（最多 64 个），BFS 查找最近出口</li>
 *   <li>将物品存入出口容器，溢出部分以掉落物形式丢弃在出口位置</li>
 * </ol>
 *
 * <h2>BFS 出口选择策略</h2>
 * <p>
 *   当网络中存在多个出口时，使用 BFS（广度优先搜索）从入口出发遍历网络图，
 *   选择第一个到达的出口作为目标。BFS 天然保证找到的是跳数最少的最近出口。
 *   若多个出口距离相同，选择结果取决于 HashMap 的迭代顺序（不确定但可接受）。
 * </p>
 *
 * <h2>出口不可用时的处理</h2>
 * <p>
 *   以下情况不执行传输，物品保留在源容器中：
 * </p>
 * <ul>
 *   <li>网络中没有任何出口</li>
 *   <li>BFS 找不到从入口到任何出口的连通路径（网络断开）</li>
 *   <li>所有出口朝向的容器已满或不是有效容器</li>
 * </ul>
 * <p>
 *   若出口容器有部分空间：尽可能多地存入，溢出部分以掉落物形式丢弃在出口位置。
 * </p>
 *
 * <h2>注册方式</h2>
 * <p>
 *   使用实例注册：{@code NeoForge.EVENT_BUS.register(new CopperPipeTickHandler())}，
 *   因为实例方法 {@link #onServerTick} 需要访问实例级别的 tick 计数器。
 * </p>
 */
public class CopperPipeTickHandler {

    /**
     * tick 计数器，用于控制扫描频率。
     * 每 20 ticks（1 秒）执行一次扫描，避免每 tick 都遍历网络造成性能压力。
     */
    private int tickCounter = 0;

    /**
     * 监听服务端 tick 事件 —— 每游戏 tick 调用一次。
     *
     * ServerTickEvent.Post 在服务端每 tick 结束时触发（Post 阶段），
     * 用于在游戏状态更新后执行后台任务。
     * 此处用于控制铜管网络的定时扫描。
     *
     * @param event 服务端 tick 事件
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        // 每 20 ticks（1 秒）执行一次扫描
        if (tickCounter < 20) {
            return;
        }
        tickCounter = 0;

        // 获取所有铜管网络的快照，避免在遍历过程中修改网络
        // CopperPipeNetwork.getOrCreate 等方法可能在遍历期间修改 networks 映射
        List<CopperPipeNetwork> networksSnapshot = CopperPipeNetwork.getAll();
        for (CopperPipeNetwork network : networksSnapshot) {
            processNetwork(network);
        }
    }

    /**
     * 监听服务端停止事件 —— 清理所有铜管网络数据，防止内存泄漏。
     *
     * 当服务端关闭（包括单人世界退出）时触发。
     * 如果不清理静态的 networks 映射，下次加载世界时可能残留旧网络数据，
     * 导致引用已卸载世界的 BlockPos 和 BlockEntity，引发内存泄漏或异常。
     *
     * @param event 服务端停止事件
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        CopperPipeNetwork.clearAll();
    }

    /**
     * 处理单个铜管网络的所有入口传输。
     * 遍历网络中所有入口，对每个入口执行：检查容器 → 提取物品 → 查找出口 → 存入出口。
     *
     * @param network 要处理的铜管网络
     */
    private void processNetwork(CopperPipeNetwork network) {
        // 获取网络所属维度的 ServerLevel 实例
        // 网络在创建时记录了维度信息，通过 dimension 字段可以获取对应的世界
        // 由于此方法仅在 ServerTickEvent 中调用，所有加载的世界都是 ServerLevel
        ServerLevel level = getServerLevel(network.getDimension());
        if (level == null) {
            // 维度对应的 ServerLevel 不存在（理论上不应发生），跳过此网络
            return;
        }

        // 遍历网络中的所有入口（INPUT 模式的铜管接口位置）
        for (BlockPos entryPos : network.getEntryPoints()) {
            processEntry(network, level, entryPos);
        }
    }

    /**
     * 处理单个入口的物品传输。
     * 检查入口朝向的容器 → 提取物品 → 通过 BFS 查找出口 → 存入出口容器。
     *
     * @param network   所属铜管网络
     * @param level     服务端世界实例
     * @param entryPos  入口方块位置
     */
    private void processEntry(CopperPipeNetwork network, ServerLevel level, BlockPos entryPos) {
        // 获取入口方块的 BlockState，确认是 CopperPipeEndpointBlock
        BlockState entryState = level.getBlockState(entryPos);
        if (!(entryState.getBlock() instanceof CopperPipeEndpointBlock)) {
            return; // 方块已被替换或类型异常，跳过
        }

        // 获取入口朝向的方向（FACING 属性指向容器所在方向）
        Direction facing = entryState.getValue(CopperPipeEndpointBlock.FACING);

        // 入口朝向的容器位置 = 入口位置 + 朝向方向
        BlockPos containerPos = entryPos.relative(facing);

        // 检查该位置是否有有效的容器方块实体（实现了 Container 接口）
        BlockEntity containerBE = level.getBlockEntity(containerPos);
        if (!(containerBE instanceof Container sourceContainer)) {
            return; // 朝向方向没有容器，跳过此入口
        }

        // 从容器中提取一个非空槽位的物品（最多 64 个，即一组）
        for (int slot = 0; slot < sourceContainer.getContainerSize(); slot++) {
            ItemStack slotStack = sourceContainer.getItem(slot);
            if (slotStack.isEmpty()) continue; // 空槽位，检查下一个

            // 提取一组物品（取槽位数量和 64 的较小值）
            ItemStack extracted = slotStack.split(Math.min(slotStack.getCount(), 64));
            // split 会修改 slotStack：如果全部提取，slotStack 变为空
            // 需要通知容器该槽位已更新（触发标记脏数据，确保区块保存时写入）
            sourceContainer.setItem(slot, slotStack);

            // 使用 BFS 查找从入口到最近出口的路径
            // findNearestExit 会从入口出发做 BFS，返回第一个遇到的出口位置
            BlockPos exitPos = findNearestExit(network, entryPos);
            if (exitPos == null) {
                // 无可用出口：将物品还回源容器，不执行传输
                // 这保证了物品安全——不会凭空消失
                ItemStack leftover = insertIntoContainer(sourceContainer, extracted);
                if (!leftover.isEmpty()) {
                    // 还回失败（容器被其他操作修改导致满），丢弃为掉落物
                    ItemEntity entity = new ItemEntity(
                            level,
                            containerPos.getX() + 0.5,
                            containerPos.getY() + 0.5,
                            containerPos.getZ() + 0.5,
                            leftover
                    );
                    level.addFreshEntity(entity);
                }
                return; // 无出口，跳过此入口的后续处理
            }

            // 获取出口朝向的容器（出口的 FACING 指向容器方向）
            BlockState exitState = level.getBlockState(exitPos);
            if (!(exitState.getBlock() instanceof CopperPipeEndpointBlock)) {
                // 出口方块类型异常，将物品还回源容器
                returnToSource(sourceContainer, extracted, level, containerPos);
                return;
            }

            Direction exitFacing = exitState.getValue(CopperPipeEndpointBlock.FACING);
            BlockPos exitContainerPos = exitPos.relative(exitFacing);

            BlockEntity exitBE = level.getBlockEntity(exitContainerPos);
            if (!(exitBE instanceof Container exitContainer)) {
                // 出口朝向方向没有容器，将物品还回源容器
                returnToSource(sourceContainer, extracted, level, containerPos);
                return;
            }

            // 尝试将物品放入出口容器
            // 手动逐槽位尝试插入，Container 接口不提供 addItem 方法
            ItemStack remaining = insertIntoContainer(exitContainer, extracted);

            if (!remaining.isEmpty()) {
                // 出口容器已满，溢出部分以掉落物形式丢弃在出口容器位置
                // 这是合理的：物品已进入管道系统，必须有去处
                ItemEntity overflow = new ItemEntity(
                        level,
                        exitContainerPos.getX() + 0.5,
                        exitContainerPos.getY() + 0.5,
                        exitContainerPos.getZ() + 0.5,
                        remaining
                );
                level.addFreshEntity(overflow);
            }

            // 每次只传输一个槽位的一组物品，处理完后返回
            // 等待下一次扫描（20 ticks 后）再处理下一个槽位
            return;
        }
    }

    /**
     * 使用 BFS 查找从入口到最近出口的最短路径，返回该出口位置。
     *
     * BFS（广度优先搜索）按层级遍历图节点：
     * 先访问所有距离为 1 的节点，再访问距离为 2 的节点，依此类推。
     * 因此第一个遇到的出口天然就是跳数最少的最近出口。
     *
     * 若存在多个距离相同的出口，选择结果取决于 HashSet 的迭代顺序
     * （不确定但可接受，因为传输效果相同）。
     *
     * @param network  铜管网络
     * @param from     入口位置（BFS 起点）
     * @return 最近的出口位置，无可达出口时返回 null
     */
    private BlockPos findNearestExit(CopperPipeNetwork network, BlockPos from) {
        return network.findNearestExit(from);
    }

    /**
     * 将物品还回源容器。
     * 当出口不可用时，将已提取的物品放回原容器。
     * 如果原容器也满了（极端情况），丢弃为掉落物。
     *
     * @param source       源容器
     * @param itemStack    要还回的物品
     * @param level        世界实例
     * @param containerPos 容器位置（用于生成掉落物）
     */
    private void returnToSource(Container source, ItemStack itemStack, Level level, BlockPos containerPos) {
        // 手动逐槽位尝试合并到已有槽位
        ItemStack leftover = insertIntoContainer(source, itemStack);
        if (!leftover.isEmpty()) {
            // 源容器也满了（极端情况），丢弃为掉落物
            ItemEntity entity = new ItemEntity(
                    level,
                    containerPos.getX() + 0.5,
                    containerPos.getY() + 0.5,
                    containerPos.getZ() + 0.5,
                    leftover
            );
            level.addFreshEntity(entity);
        }
    }

    /**
     * 尝试将物品插入容器的各个槽位。
     * Container 接口不提供 addItem 方法，需要逐槽位手动尝试插入。
     * 这是 Minecraft 容器操作的标准模式，与 CopperPipeNetwork.transfer() 一致。
     *
     * 插入策略：
     * 1. 优先找到相同物品类型的非满槽位进行合并
     * 2. 其次找到空槽位直接放入
     * 3. 返回无法放入的剩余物品
     *
     * @param container 目标容器
     * @param itemStack 要插入的物品（不会被修改，内部使用副本）
     * @return 无法放入的剩余物品，全部放入时返回 ItemStack.EMPTY
     */
    private ItemStack insertIntoContainer(Container container, ItemStack itemStack) {
        ItemStack remaining = itemStack.copy();

        // 第一遍：优先合并同类物品（isSameItemSameComponents 且未满）
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (remaining.isEmpty()) break;

            ItemStack slotStack = container.getItem(i);
            if (slotStack.isEmpty()) continue; // 空槽第二遍处理

            if (!container.canPlaceItem(i, remaining)) continue;

            if (ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                int canAdd = Math.min(slotStack.getMaxStackSize() - slotStack.getCount(), remaining.getCount());
                if (canAdd > 0) {
                    slotStack.grow(canAdd);
                    remaining.shrink(canAdd);
                    container.setItem(i, slotStack);
                    if (remaining.isEmpty()) break;
                }
            }
        }

        // 第二遍：寻找空槽位放入
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (remaining.isEmpty()) break;

            if (!container.canPlaceItem(i, remaining)) continue;

            ItemStack slotStack = container.getItem(i);
            if (slotStack.isEmpty()) {
                container.setItem(i, remaining);
                remaining = ItemStack.EMPTY;
                break;
            }
        }

        return remaining;
    }

    /**
     * 根据维度 ResourceKey 获取对应的 ServerLevel 实例。
     * NeoForge 的 MinecraftServer 保存了所有已加载维度的 ServerLevel 映射。
     *
     * @param dimension 维度的 ResourceKey
     * @return 对应的 ServerLevel，不存在时返回 null
     */
    private ServerLevel getServerLevel(net.minecraft.resources.ResourceKey<Level> dimension) {
        // 通过 MinecraftServer 获取所有已加载的 ServerLevel
        // NeoForge 的 LifecycleEvents.SERVER_STARTING 之后 MinecraftServer 可用
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        return server.getLevel(dimension);
    }
}
