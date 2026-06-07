package com.spectrum_reclamation.spectrum_reclamation.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

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
     * 物品传输方法 —— 从入口容器提取物品，通过网络"瞬移"到出口容器。
     *
     * <h3>传输流程</h3>
     * <ol>
     *   <li>验证 from/to 均在网络中，且分别是入口/出口</li>
     *   <li>BFS 查找最短路径</li>
     *   <li>从入口容器（Container 接口）提取一个物品</li>
     *   <li>尝试将物品放入出口容器</li>
     *   <li>如果出口容器满，将物品放回入口容器</li>
     * </ol>
     *
     * <h3>关于路径</h3>
     * <p>当前版本仅用于验证网络连通性和记录路径。
     *    未来版本可在路径上执行动画或中间处理。</p>
     *
     * @param level     世界实例（用于访问方块实体）
     * @param itemStack 要传输的物品（由调用方决定从哪个槽位提取）
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

        // 步骤 2：获取出口处的容器
        BlockEntity exitBE = level.getBlockEntity(to);
        if (!(exitBE instanceof Container exitContainer)) {
            return false;
        }

        // 步骤 3：手动尝试将物品插入出口容器的各个槽位
        // Container 接口不提供 addItem 方法，需要逐槽位手动尝试插入
        // 这是 Minecraft 容器操作的标准模式
        ItemStack remaining = itemStack.copy();
        for (int i = 0; i < exitContainer.getContainerSize(); i++) {
            ItemStack slotStack = exitContainer.getItem(i);

            // 如果槽位为空，直接放入
            if (slotStack.isEmpty()) {
                exitContainer.setItem(i, remaining);
                remaining = ItemStack.EMPTY;
                break;
            }

            // 如果槽位物品相同且未满，合并
            if (ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                int canAdd = Math.min(slotStack.getMaxStackSize() - slotStack.getCount(), remaining.getCount());
                if (canAdd > 0) {
                    slotStack.grow(canAdd);
                    remaining.shrink(canAdd);
                    exitContainer.setItem(i, slotStack); // 触发标记更新
                    if (remaining.isEmpty()) {
                        break;
                    }
                }
            }
        }

        // 步骤 4：如果仍有剩余物品（容器满），掉落为物品实体
        if (!remaining.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(level,
                    to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5, remaining);
            level.addFreshEntity(itemEntity);
        }

        // 无论是否有剩余，只要执行了传输就返回 true
        return true;
    }
}
