以上是主干流程，下面补充几个图中没展开的关键细节：

### Bodymovin 支持清单（牢记，避免踩坑）

| 支持                         | 不支持                  |
| :--------------------------- | :---------------------- |
| 形状图层（矩形/椭圆/路径）   | 图层样式（投影/发光等） |
| 填充 + 描边（纯色/线性渐变） | 径向渐变                |
| 位置/缩放/旋转/透明度关键帧  | Puppet Pin 网格变形     |
| 路径逐帧变形                 | 表达式（expressions）   |
| 父子链接（Parent）           | 3D 图层                 |
| 缓动曲线（Easy Ease）        | 粒子效果                |
| 修剪路径（Trim Paths）       | 运动模糊                |
| 图层进出（in/out 关键帧）    | 时间重映射              |

### 文件结构规划

```
src/main/resources/assets/nujobraincraft/
├── lottie/
│   └── background.json          ← 主界面背景动画
│   └── background_audio.ogg     ← 背景音频（可选，MC SoundManager 播放）
├── textures/gui/title/
│   └── bg.png                   ← 保留为降级方案（Lottie 加载失败时使用）
└── videos/
    └── background_video.mp4     ← 可删除（已有 Lottie 替代）
```

### 迭代循环（最重要的一环）

你做动画最大的优势就在这：

1. 在 AE 里调好动效 → Bodymovin 重新导出 JSON
2. 把新 JSON 覆盖到 `assets/lottie/background.json`
3. 游戏内输入 `/reload`
4. 主界面背景**立刻刷新**，不需要重启游戏

这个热重载和你的剧情 JSON 重载是同一套机制（`SimpleJsonResourceReloadListener`），可以用同样的 `StoryLoader` 架构来监听 lottie 目录变化。

### 降级策略

```
Lottie JSON 是否存在？
  ├── 是 → 解析成功？→ 渲染动画
  │                  └── 解析失败 → 打印日志 + 降级到 bg.png
  └── 否 → 降级到 bg.png（当前行为保持不变）
```

这样哪怕 JSON 坏了或者文件缺失，主界面只是退回静态图而不是崩溃。

------

整个工作流的唯一瓶颈在设计端——你画素材和做动效的时间。代码侧一旦 Lottie 渲染器写好，后面再换动画只需要换 JSON 文件，零 Java 代码改动。

