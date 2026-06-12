package com.spectrum_reclamation.spectrum_reclamation.util;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import com.spectrum_reclamation.spectrum_reclamation.block.CopperPipeEndpointBlock;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 铜管网络管理类 —— 静态管理所有铜管网络的拓扑结构与路径查找。
 *
 * <h2>核心设计</h2>
 * <ul>
 *   <li>每个铜管网络由唯一的 UUID 标识</li>
 *   <li>网络以邻接表（Adjacency List）表示图结构</li>
 *   <li>入口（INPUT）/ 出口（OUTPUT）由 CopperPipeEndpointBlock 定义</li>
 *   <li>物品传输通过 BFS 查找最短路径后"瞬移"（不存储在管道中）</li>
 * </ul>
 *
 * <h2>维度隔离</h2>
 * <p>
 *   每个网络实例持有 {@link #dimension} 字段记录所属维度的 {@link ResourceKey}。
 *   由于铜管只能与相邻方块连接（6方向），网络天然不会跨维度。
 *   不同维度的铜管即使 UUID 相同（理论上不可能），也会被 dimension 字段区分。
 * </p>
 *
 * <h2>线程安全</h2>
 * <p>
 *   所有网络拓扑修改操作（addNode / removeNode）和查询操作（findPath / transfer）
 *   必须在主线程（Server Thread）调用。Minecraft 的 BlockEntity 生命周期回调
 *   （onLoad / setRemoved）均在主线程执行，因此无需额外同步。
 * </p>
 */
public class CopperPipeNetwork {

    // ==================== 静态网络注册表 ====================

    /** 所有铜管网络的全局注册表：网络 UUID → 网络实例 */
    private static final Map<UUID, CopperPipeNetwork> networks = new HashMap<>();

    /**
     * 获取指定 UUID 的网络，若不存在则在指定维度创建新网络。
     * 当铜管方块实体首次加载时调用，为其分配或复用网络。
     *
     * @param networkId 网络 UUID
     * @param dimension 网络所属维度（仅在创建新网络时使用）
     * @return 对应的网络实例（永不为 null）
     */
    public static CopperPipeNetwork getOrCreate(UUID networkId, ResourceKey<Level> dimension) {
        return networks.computeIfAbsent(networkId, id -> new CopperPipeNetwork(id, dimension));
    }

    /**
     * 获取指定 UUID 的网络（仅查询，不创建）。
     *
     * @param networkId 网络 UUID
     * @return 网络实例，若不存在则返回 null
     */
    public static CopperPipeNetwork get(UUID networkId) {
        return networks.get(networkId);
    }

    /**
     * 清除所有网络数据。在服务端关闭（WorldEvent.Unload）时调用，防止内存泄漏。
     */
    public static void clearAll() {
        networks.clear();
    }

    /**
     * 删除指定 UUID 的网络。
     * 用于网络合并时移除被合并的旧网络。
     *
     * @param networkId 要删除的网络 UUID
     */
    public static void remove(UUID networkId) {
        networks.remove(networkId);
    }

    /**
     * 获取所有铜管网络的不可变快照列表。
     * 用于遍历所有网络进行定时扫描（如物品传输），
     * 返回快照而非视图，避免在遍历过程中因网络增删而抛出 ConcurrentModificationException。
     *
     * @return 所有网络实例的列表副本
     */
    public static List<CopperPipeNetwork> getAll() {
        return new ArrayList<>(networks.values());
    }

    // ==================== 网络实例字段 ====================

    /** 网络唯一标识 */
    private final UUID networkId;

    /**
     * 网络所属维度。
     * 每个网络实例仅属于一个维度，铜管无法跨维度连接，
     * 因此此字段保证维度隔离。
     */
    private final ResourceKey<Level> dimension;

    /**
     * 邻接表：方块位置 → 相邻铜管位置集合。
     * 每个铜管方块作为一个图节点，边表示两个相邻铜管之间的物理连接。
     */
    private final Map<BlockPos, Set<BlockPos>> adjacency;

    /** 入口集合：标记为 INPUT 模式的铜管接口位置 */
    private final Set<BlockPos> entryPoints;

    /** 出口集合：标记为 OUTPUT 模式的铜管接口位置 */
    private final Set<BlockPos> exitPoints;

    /**
     * BFS 路径缓存：入口位置 → 最近出口位置。
     * 拓扑不变时直接命中缓存，避免每 20 ticks 重新 BFS。
     * 拓扑变化时（addNode/removeNode/addExitPoint/removeExitPoint）清空。
     */
    private Map<BlockPos, BlockPos> exitCache = new HashMap<>();

    /** BFS 单次遍历节点上限，避免异常大网络在同一 tick 内造成服务端卡顿。 */
    private static final int BFS_VISIT_LIMIT = 2048;

    // ==================== 构造器（私有，通过 getOrCreate 获取） ====================

    private CopperPipeNetwork(UUID networkId, ResourceKey<Level> dimension) {
        this.networkId = networkId;
        this.dimension = dimension;
        this.adjacency = new HashMap<>();
        this.entryPoints = new HashSet<>();
        this.exitPoints = new HashSet<>();
    }

    /**
     * 清空路径缓存。在拓扑变化时调用。
     */
    private void invalidateCache() {
        exitCache.clear();
    }

    // ==================== 节点管理方法 ====================

    /**
     * 向网络添加一个节点（铜管方块）。
     * 将该位置加入邻接表，并注册其所有已连接的邻居。
     * 如果该位置作为入口/出口注册，则同时加入对应集合。
     *
     * @param pos       铜管方块位置
     * @param neighbors 该铜管已连接的邻居位置集合
     */
    public void addNode(BlockPos pos, Set<BlockPos> neighbors) {
        adjacency.computeIfAbsent(pos, p -> new HashSet<>());
        for (BlockPos neighbor : neighbors) {
            adjacency.get(pos).add(neighbor);
            adjacency.computeIfAbsent(neighbor, p -> new HashSet<>()).add(pos);
        }
        invalidateCache();
    }

    /**
     * 从网络移除一个节点。
     * 断开与所有邻居的双向连接，并从邻接表中删除该节点。
     * 如果移除后网络为空，自动销毁网络实例以释放内存。
     *
     * @param pos 要移除的铜管方块位置
     */
    public void removeNode(BlockPos pos) {
        Set<BlockPos> neighbors = adjacency.get(pos);
        if (neighbors != null) {
            for (BlockPos neighbor : neighbors) {
                Set<BlockPos> neighborEdges = adjacency.get(neighbor);
                if (neighborEdges != null) {
                    neighborEdges.remove(pos);
                }
            }
        }
        adjacency.remove(pos);
        entryPoints.remove(pos);
        exitPoints.remove(pos);
        invalidateCache();
        if (adjacency.isEmpty()) {
            networks.remove(this.networkId);
        }
    }

    // ==================== 入口/出口管理 ====================

    /**
     * 将指定位置注册为网络的入口（从容器提取物品）。
     *
     * @param pos 入口位置
     */
    public void addEntryPoint(BlockPos pos) {
        entryPoints.add(pos);
        invalidateCache();
    }

    /**
     * 将指定位置注册为网络的出口（向容器存入物品）。
     *
     * @param pos 出口位置
     */
    public void addExitPoint(BlockPos pos) {
        exitPoints.add(pos);
        invalidateCache();
    }

    /**
     * 从网络注销一个入口。
     *
     * @param pos 入口位置
     */
    public void removeEntryPoint(BlockPos pos) {
        entryPoints.remove(pos);
        invalidateCache();
    }

    /**
     * 从网络注销一个出口。
     *
     * @param pos 出口位置
     */
    public void removeExitPoint(BlockPos pos) {
        exitPoints.remove(pos);
        invalidateCache();
    }

    /**
     * 查询指定位置是否是网络的入口。
     */
    public boolean isEntryPoint(BlockPos pos) {
        return entryPoints.contains(pos);
    }

    /**
     * 查询指定位置是否是网络的出口。
     */
    public boolean isExitPoint(BlockPos pos) {
        return exitPoints.contains(pos);
    }

    /**
     * 获取所有入口位置的不可变视图。
     */
    public Set<BlockPos> getEntryPoints() {
        return Collections.unmodifiableSet(entryPoints);
    }

    /**
     * 获取所有出口位置的不可变视图。
     */
    public Set<BlockPos> getExitPoints() {
        return Collections.unmodifiableSet(exitPoints);
    }

    // ==================== 查询方法 ====================

    /** 获取网络 UUID */
    public UUID getNetworkId() {
        return networkId;
    }

    /** 获取网络所属维度 */
    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    /** 获取网络中的节点总数 */
    public int getNodeCount() {
        return adjacency.size();
    }

    /** 获取网络中所有节点位置（用于网络合并时遍历迁移） */
    public Set<BlockPos> getNodes() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }

    /**
     * 获取网络节点快照。
     * 合并网络时会一边遍历旧网络、一边向新网络写入节点；快照能避免底层集合变化影响遍历。
     */
    public Set<BlockPos> getNodesSnapshot() {
        return new HashSet<>(adjacency.keySet());
    }

    /** 查询指定位置是否属于此网络 */
    public boolean containsNode(BlockPos pos) {
        return adjacency.containsKey(pos);
    }

    /**
     * 获取指定位置的所有邻居节点（已连接的相邻铜管位置）。
     * 返回不可变视图，防止外部代码意外修改网络拓扑。
     *
     * @param pos 铜管方块位置
     * @return 邻居位置集合的不可变视图，节点不在网络中时返回 null
     */
    public Set<BlockPos> getNeighbors(BlockPos pos) {
        Set<BlockPos> neighbors = adjacency.get(pos);
        return neighbors != null ? Collections.unmodifiableSet(neighbors) : null;
    }

    // ==================== BFS 路径查找 ====================

    /**
     * 使用 BFS（广度优先搜索）查找从起点到终点的最短路径。
     *
     * BFS 天然保证找到的路径是最短的（按跳数计算），
     * 因为它按层级遍历图：先访问所有距离为 1 的节点，再访问距离为 2 的节点，依此类推。
     *
     * <h3>算法步骤</h3>
     * <ol>
     *   <li>将起点加入队列和已访问集合</li>
     *   <li>每次从队列头部取出一个节点，探索其所有邻居</li>
     *   <li>如果邻居是终点，通过 parent 映射回溯构建路径</li>
     *   <li>如果邻居未访问，将其加入队列并记录 parent</li>
     *   <li>队列为空时说明无路径，返回 null</li>
     * </ol>
     *
     * @param from 起始位置（入口）
     * @param to   目标位置（出口）
     * @return 最短路径（包含起点和终点），无路径时返回 null
     */
    public List<BlockPos> findPath(BlockPos from, BlockPos to) {
        // 起点和终点相同，直接返回
        if (from.equals(to)) {
            return List.of(from);
        }

        // 起点或终点不在网络中
        if (!adjacency.containsKey(from) || !adjacency.containsKey(to)) {
            return null;
        }

        // BFS 标准实现
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();

        queue.add(from);
        visited.add(from);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            // 超过守卫上限时停止本次 BFS，并记录网络规模，方便排查超大铜管网络造成的压力。
            if (visited.size() > BFS_VISIT_LIMIT) {
                logBfsPressure("findPath", from, visited.size());
                return null;
            }

            // 遍历当前节点的所有邻居
            Set<BlockPos> neighbors = adjacency.get(current);
            if (neighbors == null) continue;

            for (BlockPos neighbor : neighbors) {
                if (visited.contains(neighbor)) continue;

                visited.add(neighbor);
                parent.put(neighbor, current);

                // 找到目标，通过 parent 映射回溯构建路径
                if (neighbor.equals(to)) {
                    List<BlockPos> path = new ArrayList<>();
                    BlockPos step = to;
                    while (step != null) {
                        path.add(step);
                        step = parent.get(step);
                    }
                    Collections.reverse(path); // 反转为从起点到终点的顺序
                    return path;
                }

                queue.add(neighbor);
            }
        }

        // 队列为空，无路径
        return null;
    }

    /**
     * 使用 BFS 查找从入口到最近出口的路径（带缓存）。
     *
     * 先检查 exitCache，命中则直接返回缓存结果。
     * 未命中则执行 BFS，结果写入缓存。
     *
     * @param from 入口位置
     * @return 最近的出口位置，无可达出口时返回 null
     */
    public BlockPos findNearestExit(BlockPos from) {
        // 检查缓存
        if (exitCache.containsKey(from)) {
            return exitCache.get(from); // 可能为 null（之前确认无出口）
        }

        // 缓存未命中，执行 BFS
        if (!adjacency.containsKey(from)) {
            return null;
        }

        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(from);
        visited.add(from);

        BlockPos result = null;
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            // 超过守卫上限时停止本次 BFS；返回 null 会让调用方保留源容器物品，不会吞物品。
            if (visited.size() > BFS_VISIT_LIMIT) {
                logBfsPressure("findNearestExit", from, visited.size());
                break;
            }

            if (!current.equals(from) && exitPoints.contains(current)) {
                result = current;
                break;
            }
            Set<BlockPos> neighbors = adjacency.get(current);
            if (neighbors == null) continue;
            for (BlockPos neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        // 写入缓存（包括 null 结果，避免重复 BFS 空网络）
        exitCache.put(from, result);
        return result;
    }

    /**
     * 物品传输方法 —— 验证入口到出口连通后，尝试把调用方已提取的物品放入出口容器。
     *
     * <h3>传输流程</h3>
     * <ol>
     *   <li>验证 from/to 均在网络中，且分别是入口/出口</li>
     *   <li>BFS 查找最短路径</li>
     *   <li>根据出口接口的 FACING 找到它朝向的容器</li>
     *   <li>尝试将传入物品放入出口容器</li>
     *   <li>出口放不下时返回 false，不生成掉落物，回退策略由调用方处理</li>
     * </ol>
     *
     * <h3>关于路径</h3>
     * <p>当前版本仅用于验证网络连通性和记录路径。
     *    未来版本可在路径上执行动画或中间处理。</p>
     *
     * @param level     世界实例（用于访问方块实体）
     * @param itemStack 要传输的物品（由调用方提前从入口容器提取）
     * @param from      入口位置（铜管接口）
     * @param to        出口位置（铜管接口）
     * @return 传输成功返回 true；无路径或容器操作失败返回 false
     */
    public boolean transfer(Level level, ItemStack itemStack, BlockPos from, BlockPos to) {
        // 步骤 0：验证 from/to 分别是入口和出口
        if (!isEntryPoint(from) || !isExitPoint(to)) {
            return false;
        }

        // 步骤 1：验证路径存在
        List<BlockPos> path = findPath(from, to);
        if (path == null) {
            return false;
        }

        // 步骤 2：尝试插入出口朝向的容器；只有全部放入才算 transfer 成功。
        Container targetContainer = findEndpointContainer(level, to);
        if (targetContainer == null || !canFullyInsertIntoContainer(targetContainer, itemStack)) {
            return false;
        }
        return insertIntoEndpointContainer(level, itemStack, to).isEmpty();
    }

    /** 记录 BFS 压力守卫触发信息，帮助玩家定位过大的铜管网络。 */
    private void logBfsPressure(String operation, BlockPos from, int visitedCount) {
        SpectrumReclamation.LOGGER.warn(
                "铜管网络 {} 在 {} 从 {} 遍历 {} 个节点后触发 BFS 守卫，网络节点总数 {}。",
                networkId,
                operation,
                from,
                visitedCount,
                adjacency.size()
        );
    }

    /**
     * 按出口端点朝向查找容器并插入物品。
     * CopperPipeEndpointBlock 的 FACING 指向外部容器，端点方块本身不是容器。
     */
    private ItemStack insertIntoEndpointContainer(Level level, ItemStack itemStack, BlockPos endpointPos) {
        Container container = findEndpointContainer(level, endpointPos);
        if (container == null) {
            return itemStack.copy();
        }

        return insertIntoContainer(container, itemStack);
    }

    /** 按端点朝向查找外部容器；端点的 FACING 属性指向被连接的容器。 */
    private Container findEndpointContainer(Level level, BlockPos endpointPos) {
        BlockState endpointState = level.getBlockState(endpointPos);
        if (!(endpointState.getBlock() instanceof CopperPipeEndpointBlock)) {
            return null;
        }

        Direction facing = endpointState.getValue(CopperPipeEndpointBlock.FACING);
        BlockEntity blockEntity = level.getBlockEntity(endpointPos.relative(facing));
        if (!(blockEntity instanceof Container container)) {
            return null;
        }

        return container;
    }

    /**
     * 只模拟插入容量，不修改真实容器。
     * transfer 的布尔契约要求 false 表示完全没有写入目标容器，因此正式插入前必须先确认能全量放入。
     */
    private boolean canFullyInsertIntoContainer(Container container, ItemStack itemStack) {
        ItemStack remaining = itemStack.copy();

        for (int i = 0; i < container.getContainerSize(); i++) {
            if (remaining.isEmpty()) break;

            ItemStack slotStack = container.getItem(i);
            if (slotStack.isEmpty()) continue;
            if (!container.canPlaceItem(i, remaining)) continue;

            if (ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                int canAdd = Math.min(slotStack.getMaxStackSize() - slotStack.getCount(), remaining.getCount());
                if (canAdd > 0) {
                    remaining.shrink(canAdd);
                }
            }
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            if (remaining.isEmpty()) break;
            if (!container.canPlaceItem(i, remaining)) continue;

            ItemStack slotStack = container.getItem(i);
            if (slotStack.isEmpty()) {
                int canInsert = Math.min(Math.min(container.getMaxStackSize(), remaining.getMaxStackSize()), remaining.getCount());
                remaining.shrink(canInsert);
            }
        }

        return remaining.isEmpty();
    }

    /**
     * 逐槽位插入物品，先合并同类物品，再放入空槽。
     * Container 是原版最基础的容器接口，没有统一的 addItem 方法，所以这里必须手动处理。
     */
    private ItemStack insertIntoContainer(Container container, ItemStack itemStack) {
        ItemStack remaining = itemStack.copy();

        for (int i = 0; i < container.getContainerSize(); i++) {
            if (remaining.isEmpty()) break;

            ItemStack slotStack = container.getItem(i);
            if (slotStack.isEmpty()) continue;
            if (!container.canPlaceItem(i, remaining)) continue;

            if (ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                int canAdd = Math.min(slotStack.getMaxStackSize() - slotStack.getCount(), remaining.getCount());
                if (canAdd > 0) {
                    slotStack.grow(canAdd);
                    remaining.shrink(canAdd);
                    container.setItem(i, slotStack);
                }
            }
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            if (remaining.isEmpty()) break;
            if (!container.canPlaceItem(i, remaining)) continue;

            ItemStack slotStack = container.getItem(i);
            if (slotStack.isEmpty()) {
                int canInsert = Math.min(Math.min(container.getMaxStackSize(), remaining.getMaxStackSize()), remaining.getCount());
                ItemStack toInsert = remaining.copyWithCount(canInsert);
                container.setItem(i, toInsert);
                remaining.shrink(toInsert.getCount());
            }
        }

        return remaining;
    }
}
