package com.Hen3579.Nujomod.Client.Events;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 2D A* 寻路器。在 XZ 平面上搜索从起点到终点的最优可行走路径，
 * 支持 1 格高跳跃和 1 格高下落。
 * 搜索范围限制在 64×64 区块内，最多评估 4000 个节点。
 */
public class AStarPathfinder {

    /** 路径节点 */
    private static class Node implements Comparable<Node> {
        int x, y, z;
        double g;    // 从起点到当前节点的实际代价
        double f;    // g + 启发式估计
        Node parent;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.g = Double.MAX_VALUE;
            this.f = Double.MAX_VALUE;
        }

        BlockPos pos() { return new BlockPos(x, y, z); }

        @Override
        public int compareTo(Node o) {
            return Double.compare(this.f, o.f);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node n)) return false;
            return x == n.x && y == n.y && z == n.z;
        }

        @Override
        public int hashCode() {
            return (x * 897689 + y * 7919 + z * 6991);
        }
    }

    // 8 方向邻接偏移 (dx, dz)，对角线代价 ≈ 1.414
    private static final int[][] DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
    };

    /**
     * 在 2D 网格上搜索最优路径。
     *
     * @param level     客户端世界
     * @param start     起点方块坐标
     * @param target    终点方块坐标
     * @param maxNodes  最多评估节点数（防止无限循环）
     * @return 从起点到终点的路径点列表（含起点和终点），或 null（无路可走）
     */
    @Nullable
    public static List<Vec3> findPath(Level level, BlockPos start, BlockPos target, int maxNodes, long timeBudgetMs) {
        long startTimeNs = timeBudgetMs > 0 ? System.nanoTime() : 0;

        // 检查终点是否可达
        if (!isWalkable(level, target.getX(), target.getY(), target.getZ())) {
            // 尝试找附近可立足的点
            BlockPos adjTarget = findStandableNear(level, target);
            if (adjTarget == null) return null;
            target = adjTarget;
        }

        // 限制搜索范围到 64×64
        int minX = Math.min(start.getX(), target.getX()) - 32;
        int maxX = Math.max(start.getX(), target.getX()) + 32;
        int minZ = Math.min(start.getZ(), target.getZ()) - 32;
        int maxZ = Math.max(start.getZ(), target.getZ()) + 32;
        int startY = start.getY();

        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<Long, Node> allNodes = new HashMap<>();
        Set<Long> openSet = new HashSet<>();   // O(1) contains 检查，替代 PriorityQueue.contains() 的 O(n)
        Set<Long> closedSet = new HashSet<>(); // 已扩展节点，防止重复处理

        Node startNode = new Node(start.getX(), startY, start.getZ());
        startNode.g = 0;
        startNode.f = heuristic(startNode, target);
        open.add(startNode);
        openSet.add(key(start.getX(), start.getZ()));
        allNodes.put(key(start.getX(), start.getZ()), startNode);

        int nodesEvaluated = 0;
        Node found = null;

        while (!open.isEmpty() && nodesEvaluated < maxNodes) {
            // 超时检查：防止阻塞客户端 tick
            if (timeBudgetMs > 0 && (System.nanoTime() - startTimeNs) > timeBudgetMs * 1_000_000L) {
                break;
            }
            Node current = open.poll();
            openSet.remove(key(current.x, current.z));
            closedSet.add(key(current.x, current.z));
            nodesEvaluated++;

            // 到达目标
            if (Math.abs(current.x - target.getX()) <= 1 && Math.abs(current.z - target.getZ()) <= 1
                    && Math.abs(current.y - target.getY()) <= 1) {
                found = current;
                break;
            }

            // 检查邻居
            for (int[] dir : DIRS) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];

                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;

                // 确定目标格子的 Y
                int ny = findWalkableY(level, nx, nz, current.y);

                if (ny == Integer.MIN_VALUE) continue; // 不可行走

                // 计算移动代价
                double moveCost = (dir[0] != 0 && dir[1] != 0) ? 1.414 : 1.0;
                // 跳跃额外代价
                if (ny > current.y) moveCost += 0.5;
                if (ny < current.y) moveCost += 0.1;

                double tentativeG = current.g + moveCost;

                long k = key(nx, nz);

                // 已在 closed set 中（之前扩展过）→ 跳过
                if (closedSet.contains(k)) continue;

                Node neighbor = allNodes.get(k);
                if (neighbor == null) {
                    neighbor = new Node(nx, ny, nz);
                    allNodes.put(k, neighbor);
                } else if (tentativeG >= neighbor.g) {
                    continue; // 已有更优路径
                }

                // 检查两格之间能否直线走过去（对角穿墙检测）
                if (dir[0] != 0 && dir[1] != 0) {
                    // 对角线移动：检查拐角不会被墙挡住
                    if (!isPassable(level, current.x + dir[0], current.y, current.z)
                            || !isPassable(level, current.x, current.y, current.z + dir[1])) {
                        continue;
                    }
                }

                neighbor.parent = current;
                neighbor.g = tentativeG;
                neighbor.f = tentativeG + heuristic(neighbor, target);
                neighbor.y = ny;
                if (openSet.add(k)) {
                    open.add(neighbor);
                }
            }
        }

        if (found == null) return null;

        // 回溯路径
        List<Vec3> path = new ArrayList<>();
        Node node = found;
        while (node != null) {
            // 使用格子中心坐标作为路径点，Y 取行走面高度
            path.add(new Vec3(node.x + 0.5, node.y, node.z + 0.5));
            node = node.parent;
        }
        Collections.reverse(path);
        return path;
    }

    /** 启发式函数：欧几里得距离 */
    private static double heuristic(Node a, BlockPos target) {
        double dx = a.x - target.getX();
        double dz = a.z - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 在指定 XZ 位置，从参考 Y 开始找可站立的高度（上下最多 3 格，支持楼梯/半砖台阶） */
    private static int findWalkableY(Level level, int x, int z, int refY) {
        // 向上搜索（爬坡）：最多 2 格
        for (int dy = 0; dy <= 2; dy++) {
            int y = refY + dy;
            if (isWalkable(level, x, y, z)) return y;
            // 楼梯特殊处理：如果当前层是楼梯，玩家可以"站在上面一格"（踩在楼梯上）
            if (dy < 2 && isStairStep(level, x, y, z)) {
                int stairTop = y + 1;
                if (isWalkable(level, x, stairTop, z)) return stairTop;
            }
        }
        // 向下搜索（下坡/下落）：最多 3 格
        for (int dy = 1; dy <= 3; dy++) {
            int y = refY - dy;
            if (isWalkable(level, x, y, z)) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** 判断 (x, y, z) 是否可站立（脚下有方块、身上有空间） */
    private static boolean isWalkable(Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockPos below = pos.below();
        BlockPos above = pos.above();

        if (y < level.getMinBuildHeight() || y > level.getMaxBuildHeight()) return false;

        BlockState state = level.getBlockState(pos);
        BlockState stateBelow = level.getBlockState(below);
        BlockState stateAbove = level.getBlockState(above);

        // 占据格必须是空气或可替换（草、水等），或底层半砖/楼梯（视为 0.5 格高，头顶仍有 1.5 格空间）
        boolean posOK = state.isAir() || state.canBeReplaced();
        if (!posOK && isLowBlock(state) && isPassable(level, x, y + 1, z)) {
            posOK = true;
        }

        // 头顶必须有空间（至少 1 格高）
        boolean aboveOK = stateAbove.isAir() || stateAbove.canBeReplaced();
        if (!aboveOK && isLowBlock(stateAbove) && isPassable(level, x, y + 2, z)) {
            aboveOK = true;
        }

        // 脚下必须有实体碰撞支撑（空气/花草/水等无碰撞的方块不能立足）
        if (stateBelow.getCollisionShape(level, below).isEmpty()) return false;

        return posOK && aboveOK;
    }

    /**
     * 判断方块是否为"低矮方块"（玩家踩上去不占完整一格高度的方块）。
     * 包括：底层半砖、下半楼梯、地毯、雪层等。
     */
    private static boolean isLowBlock(BlockState state) {
        if (state.getBlock() instanceof SlabBlock) {
            return state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        }
        if (state.getBlock() instanceof StairBlock) {
            return state.getValue(StairBlock.HALF) == Half.BOTTOM;
        }
        return false;
    }

    /**
     * 判断 (x,y,z) 的方块是否形成可踩踏的台阶（站在上面能自然走高 1 格）。
     * 典型场景：下半楼梯——玩家站在楼梯块上时脚底 Y 实际上是 y，
     * 头顶是 y+1（仍需空间），但视觉上只需要 1 格站立空间。
     */
    private static boolean isStairStep(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
            // 楼梯上方必须有至少 1 格空间供玩家站立
            return isPassable(level, x, y + 1, z);
        }
        return false;
    }

    /** 判断 (x, y, z) 是否可以自由穿过（不碰撞） */
    private static boolean isPassable(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return state.isAir() || state.canBeReplaced();
    }

    /** 在目标附近找一个可站立的位置 */
    @Nullable
    private static BlockPos findStandableNear(Level level, BlockPos target) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos pos = target.offset(dx, dy, dz);
                    if (isWalkable(level, pos.getX(), pos.getY(), pos.getZ())) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** 哈希键 */
    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
