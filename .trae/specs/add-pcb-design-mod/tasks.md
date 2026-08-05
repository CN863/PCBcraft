# Tasks — add-pcb-design-mod

> 按阶段（Phase）划分，每阶段产出可验证的用户可见进展。阶段内无依赖项可并行。
> 标记 `[P]` 表示可并行调度，`[S]` 表示强依赖前置。

## Phase 0 — 模组工程脚手架

- [x] Task 0.1: 初始化 Forge 1.20.1 / Java 17 模组工程
  - [x] SubTask 0.1.1: 创建 `build.gradle`、`gradle.properties`、`settings.gradle`，依赖 Forge 47.x 与 Parchment mappings
  - [x] SubTask 0.1.2: 创建 `src/main/resources/META-INF/mods.toml` 与 `pack.mcmeta`，模组 ID `pcbcraft`
  - [x] SubTask 0.1.3: 创建主类 `PCBCraft.java`（`@Mod("pcbcraft")`），事件总线订阅骨架
  - [x] SubTask 0.1.4: 配置 `pcbcraft-client.toml` / `pcbcraft-common.toml`（DRC 默认值、仿真预算、沙盒限额等可配置项）
- [x] Task 0.2: 建立包结构与日志/配置门面
  - [x] SubTask 0.2.1: 建立 `editor/data/library/block/sim/chip/tool/render/util` 包
  - [x] SubTask 0.2.2: 创建 `PCBConfig` 读取 Forge config，集中暴露默认参数

## Phase 1 — 设计数据模型与元件库

- [x] Task 1.1: 定义设计数据核心模型 `[P]`
  - [x] SubTask 1.1.1: `PcbDesign`（板尺寸、层数、图层列表、元件实例列表、走线列表、过孔列表、网络表）
  - [x] SubTask 1.1.2: `Layer` / `LayerType`（COPPER/MASK/SILK/DRILL）
  - [x] SubTask 1.1.3: `ComponentInstance`（引用元件 id、位置、旋转、位号）
  - [x] SubTask 1.1.4: `Trace`（层、路径点列表、宽度、所属网络）、`Via`（坐标、孔径、连接层集）
  - [x] SubTask 1.1.5: `Net`（网络名、节点集合、电气类型）
- [x] Task 1.2: 元件库加载器 `[P]`
  - [x] SubTask 1.2.1: 定义 `ComponentDef` JSON schema（id/name/category/symbol/footprint/pins/model）
  - [x] SubTask 1.2.2: `ComponentLibrary` 在 `AddReloadListenerEvent` 中加载 datapack 内 `data/pcbcraft/components/*.json`
  - [x] SubTask 1.2.3: 校验与错误处理（缺字段/格式错误记日志并跳过）
- [x] Task 1.3: 内置基础元件数据 `[S]` 依赖 1.2
  - [x] SubTask 1.3.1: 电阻、电容、电感、二极管、LED、按键、电源、地
  - [x] SubTask 1.3.2: 逻辑门 AND/OR/NOT/NAND/NOR/XOR、D 触发器
  - [x] SubTask 1.3.3: 运放、微控制器芯片、连接器

## Phase 2 — PCB 编辑器 GUI

- [x] Task 2.1: 编辑器屏幕骨架 `[S]` 依赖 1.1
  - [x] SubTask 2.1.1: `PcbEditorScreen extends Screen`，画布区 + 工具栏 + 图层面板 + 元件库面板 + 错误列表
  - [x] SubTask 2.1.2: 视口平移/缩放、网格吸附、坐标 ↔ 世界映射
  - [x] SubTask 2.1.3: 打开入口物品"PCB 设计图"右键打开空白设计 / 已有设计
- [x] Task 2.2: 图层管理 `[P]`
  - [x] SubTask 2.2.1: 图层列表 UI，可见/可编辑切换，默认铜层 2 层
- [x] Task 2.3: 元件放置/旋转/删除 `[P]`
  - [x] SubTask 2.3.1: 元件库面板选择 → 点击放置；R 键旋转；Delete 删除；焊盘重叠检测
- [x] Task 2.4: 布线工具 `[P]`
  - [x] SubTask 2.4.1: 两点曼哈顿走线生成、当前铜层写入、宽度按网络
  - [x] SubTask 2.4.2: 冲突检测（与其他铜/阻焊），高亮报错
- [x] Task 2.5: 过孔工具 `[P]`
  - [x] SubTask 2.5.1: 走线上放置过孔，全铜层连通，阻焊层开窗
- [x] Task 2.6: DRC 设计规则检查 `[S]` 依赖 2.3-2.5
  - [x] SubTask 2.6.1: 最小线宽/间距/孔径/未连接网络/短路网络检查
  - [x] SubTask 2.6.2: 错误列表定位坐标，致命错误禁止生成

## Phase 3 — PCB 方块实体生成与渲染

- [x] Task 3.1: PCB 方块与 BlockEntity 注册 `[S]` 依赖 1.1
  - [x] SubTask 3.1.1: `PcbBlock`（多层堆叠：每个游戏方块=一层切片）、`PcbBlockEntity` 持有 `PcbDesign`
  - [x] SubTask 3.1.2: `BlockEntity` 序列化设计数据（网络表/元件/走线/过孔/状态）到 NBT，重载恢复
  - [x] SubTask 3.1.3: 注册方块/BlockEntity/物品（PCB 设计图、PCB 方块物品）
- [x] Task 3.2: 编译生成流程 `[S]` 依赖 2.6、3.1
  - [x] SubTask 3.2.1: `PcbCompiler` 将 `PcbDesign` → 方块堆叠布局（层数=铜层+阻焊+丝印）
  - [x] SubTask 3.2.2: 在世界放置多层方块，主 BlockEntity 关联整板
- [x] Task 3.3: 分层渲染 `[P]`
  - [x] SubTask 3.3.1: 每层方块按层类型着色/纹理（铜色/绿色阻焊/白色丝印）
  - [x] SubTask 3.3.2: 潜行+右键切换可见层

## Phase 4 — 实时电气仿真引擎

- [x] Task 4.1: 网络表构建 `[S]` 依赖 1.1
  - [x] SubTask 4.1.1: 由设计数据生成节点与支路（焊盘/走线/过孔合并为节点，元件为支路）
- [x] Task 4.2: MNA 简化求解器 `[P]`
  - [x] SubTask 4.2.1: 修正节点分析矩阵组装（电阻/电源/地/二极管线性化）
  - [x] SubTask 4.2.2: 求解各节点电压与支路电流；单 tick 预算守护（超限分片/降帧）
- [x] Task 4.3: 信号传播延迟 `[P]`
  - [x] SubTask 4.3.1: 按走线长度计算延迟（每格 N tick，可配置）
- [x] Task 4.4: 故障模型 `[P]`
  - [x] SubTask 4.4.1: 短路检测与保护（断开/限流策略可配，冒烟粒子告警）
  - [x] SubTask 4.4.2: 过载发热累积与烧毁状态（变黑/失效），热扩散到相邻元件
  - [x] SubTask 4.4.3: 闭合回路约束（无电源/开路 → 电压为 0，提示开路）
- [x] Task 4.5: Tick 调度 `[S]` 依赖 4.1-4.4
  - [x] SubTask 4.5.1: 服务端 `TickEvent.Server` 遍历已加载 PCB BlockEntity 推进仿真，向客户端同步关键状态

## Phase 5 — 可编程芯片方块与 Lua 沙盒

- [x] Task 5.1: 芯片方块与终端 `[P]`
  - [x] SubTask 5.1.1: `ChipBlock` / `ChipBlockEntity`（持有脚本文本与运行句柄）
  - [x] SubTask 5.1.2: 右键打开 `ChipTerminalScreen` 代码编辑器
- [x] Task 5.2: Luaj 沙盒运行时 `[S]` 依赖 5.1
  - [x] SubTask 5.2.1: 集成 Luaj，构造受限 `Globals`（去除 io/os/loadfile 等）
  - [x] SubTask 5.2.2: 每 tick 指令计数/内存上限，超限挂起到下 tick 或安全中止报错
  - [x] SubTask 5.2.3: 暴露 API：`pin.read/pin.write/sleep/print/analog.read/pwm`
- [x] Task 5.3: I/O 联动仿真 `[S]` 依赖 4.1、5.2
  - [x] SubTask 5.3.1: 引脚电平在网络表中作为驱动源/读入端，双向联动

## Phase 6 — 调试与玩家交互工具

- [x] Task 6.1: 信号探针物品 `[P]`
  - [x] SubTask 6.1.1: `ProbeItem` 右键走线/焊盘 → 浮窗显示电压/电流/网络，可选波形采样
- [x] Task 6.2: 信号流动可视化 `[P]`
  - [x] SubTask 6.2.1: 走线按电平染色（高红/低蓝），电流方向粒子，模拟信号渐变色
- [x] Task 6.3: 元件热插拔 `[P]`
  - [x] SubTask 6.3.1: `SolderingIronItem` 右键拆焊/换件，增量更新网络表无需重编译
- [x] Task 6.4: 电压全局视图 `[P]`
  - [x] SubTask 6.4.1: 切换热力色染色整板节点电压

## Phase 7 — 集成、配方与本地化

- [x] Task 7.1: 合成配方 `[S]`
  - [x] SubTask 7.1.1: PCB 设计图、PCB 空板、探针、示波器、焊台、芯片方块的合成/获取配方
- [x] Task 7.2: 本地化 `[P]`
  - [x] SubTask 7.2.1: `lang/en_us.json` 与 `lang/zh_cn.json` 覆盖物品/方块/UI/提示
- [x] Task 7.3: 端到端验证场景 `[S]`
  - [x] SubTask 7.3.1: LED+电阻+电源点亮回路
  - [x] SubTask 7.3.2: 逻辑门组合实现半加器
  - [x] SubTask 7.3.3: 芯片固件 blink LED
  - [x] SubTask 7.3.4: 短路保护与过载烧毁演示

## Phase 8 — 验证期视觉缺陷修复（验证代理追加）

> 由静态代码审查发现的非 TODO 占位缺陷（核心逻辑已实现，仅缺视觉表现）。这三项分别影响
> Phase 6 Task 6.2 / Phase 4 Task 4.4.1 / Phase 4 Task 4.4.2 的 checkpoint 完整达成，
> 合并为一个修复 Task 以便集中处理。

- [x] Task 8.1: 视觉表现补齐 `[P]`
  - [x] SubTask 8.1.1: 信号流动电流粒子方向（Phase 6.2）
    - 现状：`PcbVisualizationRender` 仅按电压静态染色（高红/低蓝/中间黄 + 热力图渐变），第 49 行注释明确 `电流粒子流动本阶段留 TODO，当前仅静态颜色`。
    - 修复：在 `RenderLevelStageEvent` 中基于 `SimStatePacket` 节点电压与支路电流方向，沿走线方向每帧推进粒子相位，绘制带方向的小箭头/粒子流；模拟信号（中间电平）按电压幅值调整粒子密度/速度。
    - 影响 checkpoint：调试工具 "信号流动可视化：高红/低蓝染色、电流粒子方向、模拟信号渐变色"。
  - [x] SubTask 8.1.2: 短路保护冒烟粒子告警（Phase 4.4.1）
    - 现状：`FaultModel.analyze` 在短路时仅 `LOGGER.warn` + `setShutDown(true)`，无任何 `ParticleTypes.LARGE_SMOKE`/`FLAME` 等粒子；全项目仅 `PcbBlock.use` 在切层时发 `HAPPY_VILLAGER`。短路"冒烟告警"未在视觉上体现。
    - 修复：`SimTickScheduler.onServerTick` 在 `fault.isShortCircuited()` 首次为 true 或 `tripped` 由 false 转 true 的瞬间，向 master 方块上方 `sendParticles(ParticleTypes.LARGE_SMOKE, ...)` 多次喷发；可通过 `SimStatePacket` 同步触发客户端额外冒烟粒子。
    - 影响 checkpoint：仿真引擎 "不同网络短接触发保护（断开/限流）并冒烟告警"。
  - [x] SubTask 8.1.3: 烧毁元件渲染变黑失效（Phase 4.4.2）
    - 现状：`FaultModel.burnedComponents` 集合正确记录已烧毁位号并阻止其继续发热，但 `PcbRenderEvents` 的 BlockColor 仅按 `LayerType` + `isPowered()` 着色，未读取 `fault.isBurned(designator)`；丝印层方块也未按烧毁位号变黑。烧毁仅在日志与仿真状态上有体现，世界中视觉无变化。
    - 修复：扩展 `PcbBlockEntity` 同步 `burnedComponents` 到客户端（已有 `getUpdateTag` 转发 `FaultModel.save`，客户端侧 `PcbBlockEntity` 需暴露 burned 集合）；`PcbRenderEvents` 在 silk 层方块对应该 master 时查询对应位号 origin，若已烧毁则强制返回黑色（如 `0xFF111111`）覆盖基色；可选：烧毁元件 origin 处追加 `ParticleTypes.FLAME`/`SMOKE` 持续粒子。
    - 影响 checkpoint：仿真引擎 "元件过载累积发热，达阈值烧毁变黑失效，热扩散影响相邻元件"。

# Task Dependencies

- Phase 1 全部 → Phase 2 / Phase 3.1 / Phase 4.1
- Task 2.6 (DRC) → Task 3.2 (编译生成)
- Task 3.1 (BlockEntity) → Task 3.2
- Phase 4.1 (网络表) → Phase 4.5 / Task 5.3
- Task 5.2 (沙盒) → Task 5.3
- Phase 4 + Phase 5 → Phase 6 可并行
- Phase 6 + Phase 3 完成 → Phase 7
