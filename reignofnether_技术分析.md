# Reign of Nether 四项核心技术实现分析

> 项目: https://github.com/SoLegendary/reignofnether
> 分支: `1.20.1-dev`, Forge 1.20.1, GPL-3.0
> 核心包: `com.solegendary.reignofnether`

---

## 一、战争迷雾与数据遮丑 (Fog of War)

### 1.1 整体架构

战争迷雾通过 **三层可见性状态** 实现：

| 层 | 状态 | 亮度 | 说明 |
|---|------|------|------|
| `brightChunks` | 可见 | 1.0 | 己方单位/建筑视野范围内 |
| 普通暗 | 未探索 | 0.35 | 视野外，但无对手建筑 |
| `frozenChunks` | 冻结/伪装 | 数据替换 | 视野外且存在对手建筑，**完全替换客户端方块数据** |
| 世界边界外 | 边界外 | 0.10 | 地图边界外 |

### 1.2 核心文件

```
fogofwar/
├── FogOfWarClientEvents.java   ← 客户端主控逻辑
├── FogOfWarServerEvents.java   ← 服务端同步
├── FrozenChunk.java            ← 冻结区块（数据遮丑核心）
├── FogOfWarClientboundPacket.java
├── FogOfWarServerboundPacket.java
├── FrozenChunkAction.java
├── FrozenChunkClientboundPacket.java
└── FrozenChunkServerboundPacket.java

mixin/fogofwar/
├── LevelRendererMixin.java         ← 拦截 applyFrustum / compileChunks
├── CompiledChunkMixin.java         ← 过滤非可见区块的 BlockEntity
├── EntityRenderDispatcherMixin.java ← 取消非可见实体的火焰渲染
├── EntityShadowMixin.java          ← 取消非可见实体的阴影
├── ModelBlockRendererMixin.java    ← 对水面/草等平面方块施加亮度着色
├── ModelBlockRendererCacheMixin.java
├── ItemEntityRendererMixin.java
├── LiquidBlockRendererMixin.java
└── SingleQuadParticleMixin.java
```

### 1.3 亮度控制机制

**ModelBlockRendererMixin** 是关键着色入口。对于所有顶点亮度均为 1.0 的方块面片（水面、高草等平面方块），它劫持 `putQuadData` 方法：

```java
// 从 FogOfWarClientEvents.getPosBrightness(pPos) 获取亮度倍率
float br = FogOfWarClientEvents.getPosBrightness(pPos);
// 把所有四个顶点的亮度乘以 br
pConsumer.putBulkData(pPose, pQuad,
    new float[]{pBrightness0 * br, pBrightness1 * br,
                pBrightness2 * br, pBrightness3 * br}, ...);
```

**亮度判定逻辑** (`getPosBrightness`):
1. 检查是否在世界边界内 → 否则 0.10
2. 遍历 `brightChunks` 集合 → 命中返回 1.0
3. 默认返回 0.35（黑暗区域）

### 1.4 视野计算算法 (`updateFogChunks`)

每 10 ticks 执行一次：

```
1. 收集 viewerChunks（普通视野，range=1 chunk）和 farViewerChunks（远视野，range=2 chunk）
   - 己方单位所在区块 → viewerChunks
   - 己方建筑所在区块 → viewerChunks
   - 主城 / 驻守建筑 → farViewerChunks
   - Ghast 单位 → farViewerChunks

2. 对每个 viewerChunk，以 CHUNK_VIEW_DIST=1 为半径扩散到 brightChunks
3. 对每个 farViewerChunk，以 CHUNK_FAR_VIEW_DIST=2 为半径扩散到 brightChunks

4. 找出 newlyDarkChunks（上次可见但本次不可见）
   → onChunkUnexplore() → 保存 FrozenChunk 的客户端方块快照

5. 找出 newlyBrightChunks（本次可见但上次不可见）
   → onChunkExplore() → 卸载 FrozenChunk 数据，恢复真实服务端方块

6. 清理超出渲染距离的 semiFrozenChunks
```

### 1.5 FrozenChunk — 数据遮丑核心

这是战争迷雾最精巧的设计。当对手在"黑暗区块"中建造建筑时，服务端的方块变更**必须对客户端完全隐藏**。

**`saveFakeBlocks()` — 伪装保存**:
```
1. 建筑自身的方块 → 替换为 AIR（桥梁则为 WATER）
2. 脚手架(Scaffolding) → AIR
3. 岩浆块/圆石 → 煤矿石(COAL_ORE)
4. 泥土/地狱岩 → 草方块(GRASS_BLOCK)
5. 黑曜石 → 水(WATER)
6. 地狱传送门 → AIR
7. 下界方块 → 对应的主世界等效方块
8. 下界植物 → 对应主世界植物
```

**`saveBlocks()` — 真实保存**: 在客户端进入黑暗前，快照整个 16x16x16 空间的所有客户端方块。

**加载/卸载生命周期**:
- Chunk 从亮变暗 → `onChunkUnexplore()` → `frozenChunk.saveBlocks()`
- RenderChunk 进入视锥体 → `LevelRendererMixin.compileChunks()` → `frozenChunk.loadBlocks()`（先加载真实快照，再加载伪装快照）
- Chunk 从暗变亮 → `onChunkExplore()` → `frozenChunk.unloadBlocks()` → 向服务端请求同步真实方块

**`semiFrozenChunks` 机制**: 当区块不在 brightChunks 内、且已被渲染引擎加载到视锥体时，标记为 semiFrozenChunks。在 `compileChunks` 中跳过这些区块，不再重新编译——因为渲染引擎已经在使用 FrozenChunk 提供的伪装方块数据进行了正确渲染。

### 1.6 实体隐藏

- **实体渲染**: `onRenderLivingEntity` 拦截 `RenderLivingEvent.Pre`，对非 brightChunk 内实体调用 `evt.setCanceled(true)`
- **火焰/阴影**: `EntityRenderDispatcherMixin` 和 `EntityShadowMixin` 取消非可见实体的火焰和阴影渲染
- **BlockEntity**: `CompiledChunkMixin.getRenderableBlockEntities()` 过滤掉非可见区块的方块实体
- **远程攻击穿透**: `RangedAttackerUnit` 开火后临时设置 `fogRevealDuration`，使被攻击者短暂看到攻击方

### 1.7 渲染区块刷新

当区块在视锥体外从暗变亮时（`onChunkExplore` 中检测），加入 `chunksToRefresh` 集合。在 `applyFrustum` 中对这些区块的 RenderChunk 调用 `setDirty(true)` 触发重新编译——确保光照和方块状态正确更新。

---

## 二、单位行为 AI 与平面寻路优化

### 2.1 整体架构

寻路系统采用 **自定义 3D A\* + 可步行性网格缓存 + 多线程工作池** 的三层架构：

```
RtsPathfinder (高层API)
    ↓
PathfinderWorkerPool (多线程队列，WORKER_THREADS = CPU核心数/2)
    ↓
GridAStar (3D A* 搜索核心)
    ↓
WalkabilityGrid (分 Level 缓存的可步行性网格)
    ↓
WalkabilityGridChunk (单个区块的 3D 字节数组 + crowd 预计算)
    ↓
WalkabilityBuilder (单格分类: LAND/WATER/FIRE/LAVA/BLOCKED/SLIME)
```

### 2.2 核心文件

```
unit/pathfinding/
├── RtsPathfinder.java           ← 高层 API，暴露寻路接口
├── PathfinderWorkerPool.java    ← 多线程工作池，队列背压
├── GridAStar.java               ← 3D A* 搜索
├── GridNeighbors.java           ← 邻接模型（20方向 + 跨越对角线检测）
├── WalkabilityGrid.java         ← 全局可步行性网格缓存（WeakHashMap<Level,Grid>）
├── WalkabilityGridChunk.java    ← 区块 3D 网格（cellKind + solid + crowd 字节数组）
├── WalkabilityBuilder.java      ← 单元格分类器
├── WalkabilityView.java         ← A* 读取网格的只读接口
├── MobilityClass.java           ← 移动类型（地面/飞行/水陆/两栖）
├── PathConverter.java           ← 将 A* 路径转为 Minecraft Path
├── ChunkSnapshot.java           ← 寻路时的区块不可变快照
└── PathfinderConfig.java        ← 集中配置常量
```

### 2.3 PathfinderConfig — 关键配置

```java
WORKER_THREADS       = CPU核心数 / 2          // 工作线程数
MAX_NODES            = 250000                 // 单次搜索最大节点数
MAX_NODES_UNREACHABLE_GOAL = 8000            // 不可达目标早期终止
MAX_RADIUS           = 96                     // 搜索最大平面半径
MIN_DILATION         = 48                     // 最小扩张半径
MAX_CHAIN_SEGMENTS   = 10                     // 最大链式路径段数
QUEUE_BACKPRESSURE_CAP = 500                 // 队列背压上限

VERTICAL_RADIUS      = 24                     // Y轴搜索窗口（只分类地表附近）
MAX_FALL_DROP        = 3                      // 允许的最大坠落高度
FALL_COST_PER_BLOCK  = 0.3                    // 每格坠落额外代价

MALUS_CLEARANCE      = 2                      // 拥挤惩罚预计算间隙
FIRE_AVOID_COST      = 50.0                   // 火焰回避代价
SLIME_AVOID_COST     = 10.0                   // 黏液块回避代价

MAX_CACHED_CHUNKS    = 5184 (=72*72)         // 可缓存整个RTS地图
CHUNK_BUILDS_PER_TICK_DEFAULT = 4            // 每tick最大冷区块构建数
MAX_CHUNK_RECLASSIFY_PER_TICK = 8            // 每tick最大区块重分类数
WALKABILITY_SETTLE_TICKS = 4                  // 脏区块沉淀时间
WALKABILITY_MAX_DEFER_TICKS = 40             // 脏区块硬上限（~2s）
```

### 2.4 WalkabilityGrid — 懒加载与增量更新

**全局缓存**: `WeakHashMap<LevelAccessor, WalkabilityGrid>`，每个 Level 一个实例。

**区块缓存**: `Long2ObjectLinkedOpenHashMap<WalkabilityGridChunk>`，LRU 访问排序，最多 5184 个区块（覆盖整个 RTS 地图+边距，永不驱逐）。

**垂直窗口**: WalkabilityGridChunk 只在请求的 Y 范围 `[wantMinY, wantMaxY)` 内分类单元格，默认为地表上下 VERTICAL_RADIUS=24 的范围——不需要分类整个 Y 列。

**增量更新 (Dirty Chunk 机制)**:
1. 方块变更 → `LevelChunkMixin.setBlockState` → `markChunkDirtyIfPresent()`
2. 记录变更 bbox（局部坐标 0..15 + 世界 Y），以及 firstTick/lastTick 时间戳
3. 每 tick 调用 `drainDirtyChunks(budget)`：
   - 已沉淀 (lastTick ≥ settle_ticks 无变更) 或 超时 (firstTick ≥ max_defer_ticks) → 重建
   - 重建方式：`reclassifyRegion()` 对脏区域局部重分类（clone + patch），而非整区块重建
   - Copy-on-write：新建区块后原子交换，A* 线程始终读到完整版本
4. 沉淀机制：建筑逐块放置时不会每 tick 重建，而是等放置完成后一次性重建

### 2.5 GridAStar — 3D A* 搜索

**20 方向邻接模型**: 6 轴向 (NSEW+上下) + 8 水平对角 + 6 垂直对角 = 20 方向。

**多块坠落**: 普通邻接只有 ±1 Y 步，一个 2-3 格陡坡无法表达。`GridAStar` 增加坠落通道（fall pass）：
- 对 8 个水平方向，若 `(cur.y - 1)` 不可站立 → 向下扫描直到找到可站立格或达到 MAX_FALL_DROP
- 每格坠落额外代价 = `FALL_COST_PER_BLOCK * drop`
- 单位优先走缓坡，但必要时会坠崖

**宽体单位** (`footprintRadius > 0`): 每一步检查 `wideFits()`——整个 footprint box 必须都可以站立。

**攀爬单位** (蜘蛛): 单独的 `CLIMB_*` 移动集（6 方向：垂直上下 + 4 水平）:
- 检查 `adjacentToClimbWall()` ——头顶脚下有可攀爬方块
- 检查 `climbColumnClear()` ——所在列无实体阻挡
- 爬墙代价固定 `CLIMB_COST`

**拥挤惩罚 (Crowding Malus)**: 每个 cell 预计算 0~3.0 的拥挤代价（离墙壁/边缘/角落越近越高），烘焙到字节数组 (`crowd[]`)，A* 每步累加——软引导单位走开阔路径而非贴墙挤缝。

**不可达目标早期终止**: `MAX_NODES_UNREACHABLE_GOAL = 8000`（正常搜索 250000），大幅减少不可达路径的 CPU 浪费。

### 2.6 PathNavigationMixin — 移动执行

- **禁用原版重算**: `shouldRecomputePath` 和 `recomputePath` 对 RTS 单位返回 false——方块变更不会触发原版路径重算（由 dirty chunk reclassify + move goal repath 处理）
- **放宽垂直判定**: 宽体单位 `reach = MAX_FALL_DROP + 0.5`（允许跨越坠落节点）
- **主动坠落提交**: `commitDescent` 检测到当前在悬崖边缘且下一个节点在下方时，主动推进路径索引——让宽体单位"迈出悬崖"而非在边缘旋转

---

## 三、世界生成改进（平坦化与洞穴移除）

### 3.1 实现架构

Reign of Nether **不使用 Java 代码** 修改世界生成，完全通过 **Minecraft 数据包** 实现。利用 MC 1.18+ 的 Density Function 系统，通过 `data/` 目录下的 JSON 覆盖原版的 noise router。

### 3.2 平坦化 — `flat_dimensions` 命名空间

#### 零化地形噪声
```json
// data/flat_dimensions/worldgen/density_function/overworld/continents.json
{ "type": "minecraft:add", "argument1": "minecraft:overworld/continents", "argument2": 0 }
// → 大陆度归零

// erosion.json → 侵蚀度归零
// ridges.json → 山脊度归零
// 这三个 density function 全部加 0——彻底消除地形起伏的输入信号
```

#### 偏移函数 (`offset.json`)

核心是 `flat_dimensions:overworld/terrain/offset.json`，通过 **三次样条插值** 将所有地形的 surface height 压制在 `-0.53` 附近：

```
sea_level 常量 ≈ -0.53（而非原版 0.0）

spline 定义了一系列 (location, value, derivative=0) 的控制点：
  - 海洋深槽: location = -1.1  → value = 0
  - 浅海/陆地过渡: location = -0.51 → value = -0.2222
  - 所有 terrain points 的 value 都 ≤ -0.03，derivative = 0

效果：无论 biome noise 如何变化，offset 始终把 terrain_base 拉到接近 sea_level
```

#### 洞穴逻辑短路 (`sloped_cheese.json`)

```json
{
  "type": "minecraft:clamp",
  "input": { /* depth→surface 密度转换 */ },
  "min": -1000000,
  "max": 1.5
  // ↑ 限制洞穴最大密度为 1.5，远低于原版，实际不产生洞穴
}
```

原版洞穴在 `sloped_cheese > 0` 时雕刻。这里把 `sloped_cheese` 钳制在 ≤1.5 → 永远不会进入洞穴雕刻阶段。

### 3.3 洞穴/峡谷移除 — `overworldify` 命名空间

#### 空 Carver
```json
// data/overworldify/worldgen/configured_carver/{end,nether}/cave.json
{
  "type": "minecraft:cave",
  "config": {
    "probability": 0.01,       // 极小概率（防止 probability=0 导致的崩溃）
    "yScale": 0,               // Y轴缩放为0 → 不产生实际雕刻
    "horizontal_radius_multiplier": 0,
    "vertical_radius_multiplier": 0,
    "floor_level": 0,
    "replaceable": "minecraft:air"  // 替换空气——即使触发也不影响方块
  }
}
```

#### 生物群系 Carver 禁用

覆盖 7 个原版生物群系 JSON（`plains`, `desert`, `savanna`, `meadow`, `beach`, `river`, `lukewarm_ocean`），全部设置为：
```json
"carvers": { "air": [] }   // 空的 carver 列表 → 无任何洞穴/峡谷生成
```

### 3.4 矿道禁用

覆盖原版 `structure_set/mineshafts.json`，将 `spacing` 和 `separation` 设为极大值 → 有效禁用矿道生成。

---

## 四、修改原版视锥体剔除 — 防止近视距切穿地形

### 4.1 问题背景

RTS 游戏使用正交投影俯视视角。原版 Minecraft 的透视投影 + 第三人称摄像机在俯视时会产生**近视距平面切穿地形**的问题——离摄像机太近的地形块被近平面裁剪掉，出现空洞。

### 4.2 三层解决方案

#### 第一层：正交投影矩阵 (`OrthoViewMixin` + `OrthoviewClientEvents`)

**`OrthoViewMixin`** 注入到 `GameRenderer.getProjectionMatrix()`：
```java
if (OrthoviewClientEvents.isEnabled()) {
    cir.setReturnValue(OrthoviewClientEvents.getOrthographicProjection());
}
```

**正交矩阵生成** (`getOrthographicProjection`):
```java
near = -3000;  // 近平面推到摄像机后方 3000 格
far  = 3000;   // 远平面在摄像机前方 3000 格

// 基于 zoom 计算视口宽高
wView = (zoomFinal / screenHeight) * screenWidth;
left = -wView / 2;  right = wView / 2;
top  = zoomFinal / 2;  bot = -zoomFinal / 2;

// 标准正交投影矩阵
Matrix4f = [
  2/(r-l), 0,       0,       -(r+l)/(r-l),
  0,       2/(t-b), 0,       -(t+b)/(t-b),
  0,       0,       -2/(f-n), -(f+n)/(f-n),
  0,       0,       0,        1
]
```

**关键**: `near = -3000` —— 近平面位置在摄像机**后方**，意味着从摄像机位置向前 3000 格范围内的所有内容都不会被近平面裁剪。这从根本上解决了近视距切穿问题。

#### 第二层：摄像机位置偏移 (`CameraMixin`)

```java
// CameraMixin.move() → 当 orthoview 启用时
pDistanceOffset -= 20;  // 摄像机额外后移 20 格
```

同时设置 `detached = true`（第三人称分离模式），使摄像机完全独立于玩家实体位置。

#### 第三层：防止 Frustum 死循环 (`FrustumMixin`)

```java
// offsetToFullyIncludeCameraCube — 阻止冻结
if (OrthoviewClientEvents.isEnabled()) {
    cir.setReturnValue((Frustum)(Object)this);
    // 直接返回自身，不进行任何偏移计算
}
```

原版 Frustum 在 `offsetToFullyIncludeCameraCube` 中有复杂的迭代逻辑，在正交投影下可能陷入死循环。直接短路它。

#### 额外优化：BlockEntity 可见性

```java
// FrustumMixin.isVisible() — 针对 INFINITE_EXTENT_AABB
if (OrthoviewClientEvents.isEnabled() && infAABB) {
    // 对 Beacon / EndPortal 等 INFINITE_EXTENT_AABB 的方块实体
    // 使用基于 zoom 的距离检查替代无限包围盒
    if (building.centrePos.distSqr(equalYBp) < (zoom * zoom))
        cir.setReturnValue(true);
}
```

### 4.4 正交视角玩家高度自适应

```java
// OrthoviewClientEvents.updateOrthoviewY()
// 每 tick 计算玩家周围 21×21 区域的 MOTION_BLOCKING 平均高度
avgHeight = Σ(MOTION_BLOCKING height) / (21 * 21)
orthoviewPlayerBaseY = avgHeight + 30;  // 基础高度 = 地表+30
orthoviewPlayerMaxY = avgHeight + 100;  // 最大高度 = 地表+100
```

摄像机随地形自适应抬高，避免摄像机嵌入山体或俯冲过低。

---

## 总结对比

| 特性 | Reign of Nether 的实现方式 | 关键技术点 |
|------|--------------------------|----------|
| 战争迷雾 | 客户端三层亮度 + FrozenChunk 方块伪装 | 拦截渲染管线 Mixin、方块状态快照/伪装/恢复 |
| 寻路 AI | 3D A* + 多线程工作池 + 懒加载可步行性网格 | 增量区块更新(Copy-on-Write)、宽体/攀爬/坠落支持 |
| 世界平坦化 | 纯数据包 Density Function 覆盖 | 零化噪声、spline 压低地形、sloped_cheese 钳制洞穴 |
| 视锥体修复 | 正交投影(near=-3000) + 摄像机后移 + Frustum 死循环防护 | OrthoViewMixin、CameraMixin、FrustumMixin 三层组合 |
