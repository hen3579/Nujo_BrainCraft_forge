# Nujo's Braincraft 项目长期记忆

## 项目基本信息
- MC 版本: 1.20.1, Forge 47.3.11, Parchment mappings 2023.09.03
- 包名: com.Hen3579.Nujomod, mod_id: nujobraincraft
- 依赖: GeckoLib 4.4.9, Patchouli, Mixin, VLCJ (视频背景)
- Java: 17

## 剧情系统架构 (2026-07-09 建立)
- 剧情引擎位于 `com.Hen3579.Nujomod.Story` 包
- JSON 数据包位于 `data/nujobraincraft/story/` (stages/, dialogues/, quests/, npcs/)
- 引擎与内容分离：Java 只写通用逻辑，所有剧情内容通过 JSON 定义
- 热重载：/reload 自动重新加载所有剧情 JSON (StoryLoader extends SimpleJsonResourceReloadListener)
- 玩家进度：StoryCapability (Capability + NBT)，死亡不丢失
- 网络通道：StoryNetwork (SimpleChannel, modid:story)
- 命令：/story info|setstage|reset|reload|trigger|mark|character

## 剧情内容
- 基于《阿凡达跨维实验》主线通史 + 《剧情全要素整理文档》
- 10 个剧情阶段（含拆分）：prologue → world_abnormal → enter_brain_world → meet_brian_helen → know_truth_injustice → defeat_darcy → final_farewell_talk → unlock_empty_dim → finish_main_story → finale
- 关键 NPC：画师、布瑞恩、海伦、达西、异魔领袖、怒九空白
- 群体 NPC：世俗村民、异魔长老、异魔战士、异魔孩童、异魔平民
- 标记系统：war_hero, blank_recognized, cross_dimension_traveler
- 剧情道具 ID（需 Java 注册）：domain_rule_fragment, cross_dim_blueprint, memory_fragment, forgotten_fragment
- 注意：对话节点 on_enter 动作不被引擎处理，只有 option.action 会执行

## 维度系统 (2026-07-09)
- 维度通过数据包 JSON 注册（无需 Java 代码）
- **brain_world**（脑洞唯心世界）：`data/nujobraincraft/dimension/brain_world.json` + `dimension_type/brain_world.json`
  - 类似主世界规则（有昼夜、可睡觉、自然生成），当前用 overworld 预设占位，后期替换自定义生物群系
- **source_void**（本源空域）：`data/nujobraincraft/dimension/source_void.json` + `dimension_type/source_void.json`
  - 纯白虚空维度，flat 生成器 + the_void 生物群系，无自然生物刷新，ambient_light=1.0 常亮
  - 不可睡觉（bed_works: false）
- 传送：ActionExecutor.executeTeleport() 通过 Registries.DIMENSION + ResourceLocation 查找
- **世界平坦化** (2026-07-10, 2026-07-10 重构): RoN 风格——保留原版完整管线，只覆盖输入信号
  - `brain_world_flat.json` 和 `overworld.json` 均为原版 overworld.json 完整副本（含 32K surface_rule + 2.5K final_density 管线）
  - 覆盖 `minecraft:overworld/offset` → 常量 -0.5（原版是 60K spline，控制地形高度偏移）
  - 覆盖 `minecraft:overworld/factor` → 常量 3.0（原版是 34K spline，控制地形高度变化倍率）
  - 保留原版洞穴系统: final_density 管线含 spaghetti_2d/noodle/pillars/entrances + blend_density + interpolated + squeeze
  - carver 已恢复原版 (cave probability=0.15, canyon 原版配置)
  - aquifers/ore_veins 已启用（原版默认）
  - 地表矿脉 (surface_ore) 和树木密度调整仍保留
  - 旧方案问题: surface_rule 仅 230 字符(无草地/泥土)、final_density 跳过管线(无洞穴)、线性梯度代替 spline

## 战争迷雾系统 (2026-07-10 建立)
- `fogofwar/FogOfWarData` — 三层亮度(BRIGHT_VISIBLE=1.0/BRIGHT_DARK=0.35/BRIGHT_UNEXPLORED=0.10), ChunkPos 集合, NBT 持久化
- `fogofwar/FogOfWarEvents` — @Mod.EventBusSubscriber(client): onClientTick(更新+auto-save), onRenderLivingPre(取消非揭示实体), onWorldLoad/Unload(持久化)
- `Mixins/ModelBlockRendererMixin` — putQuadData @Inject HEAD 捕获 pPos → ThreadLocal fogBrightness, @ModifyArgs 拦截 putBulkData args[2] brightnesses[] *= fog
- 持久化: 单人→saves/<world>/nujobraincraft_fog.nbt, 多人→游戏根目录
- 剧情集成: storyReveal(chunkX, chunkZ, radius) 可强制揭示区域

## 编译注意事项
- Forge 1.20.1 中 EntityType.spawn() 使用 3 参数版本: spawn(ServerLevel, BlockPos, MobSpawnType)
- 维度 ResourceKey 使用 Registries.DIMENSION (不是 Registry.DIMENSION_REGISTRY)
- NbtAccounter.unlimitedHeap() 不存在，直接用 buf.readNbt()
- registerMessage 使用 5 参数版本（不带 NetworkDirection）
- getRegistryName() 已移除，用 BuiltInRegistries.getKey() 替代

## 世界平坦化注意事项 (2026-07-10)
- **核心思路**: 不要重写 noise_settings，而是复制原版 overworld.json 然后只覆盖 offset/factor 两个密度函数
- 原版 offset (60K spline) 和 factor (34K spline) 是地形高度变化的来源——覆盖为常量即可平坦化
- offset=-0.5 将地表放在海平面 (y≈63), factor=3.0 提供陡峭的固体→空气过渡
- 原版 final_density 管线包含完整洞穴雕刻: range_choice(sloped_cheese) → cave_entrances/cave_cheese/spaghetti_2d/pillars/noodle
- sloped_cheese = 4 * quarter_negative(depth * factor) + base_3d_noise，依赖 depth(=y_gradient+offset) 和 factor
- surface_rule 是 32K 字符的 sequence 决策树，处理草地/泥土/沙子/石头/水等所有地表方块
- blend_alpha/blend_offset 用于区块边界平滑过渡，覆盖时需保留
- Forge biome_modifier: data/<modid>/forge/biome_modifier/*.json, type=forge:add_features 可批量添加 placed_feature 到 biome tag
