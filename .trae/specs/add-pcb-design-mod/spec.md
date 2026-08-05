# Minecraft PCB 设计与仿真模组 (PCBcraft) Spec

> change-id: `add-pcb-design-mod`
> 模组 ID: `pcbcraft`
> 目标平台: Minecraft Java Edition 1.20.1 + Forge 47.x / Java 17（Fabric 适配作为后续可选）
> 参考实现: KiCAD（数据结构）、ngspice（电路求解）、PCBMOD（方块化建模）、ComputerCraft（受限脚本）、Create: Power Grid（电力回路）、Digital-Logic-Sim（信号可视化）

## Why

传统红石电路体积庞大、只能表达数字逻辑、无法处理模拟信号，难以构建紧凑复杂的电子系统。玩家希望在游戏内闭环完成 PCB 设计（元件布局、多层布线、过孔、阻焊）并自动生成可仿真电气行为的方块实体，用工程化 PCB 范式替代红石，同时支持芯片编程与实时调试。所有流程不依赖导出到现实 EDA 工具。

## What Changes

本变更建立一个全新的独立 Forge 模组，包含以下能力（均为新增）：

- **游戏内 PCB 编辑器 GUI**：多图层（铜层/阻焊/丝印/钻孔）管理、元件库放置、手工与半自动布线、过孔、设计规则检查（DRC）。
- **标准化元件库系统**：元件 = 符号 + 封装 + 电气模型（SPICE 子电路简化版），数据驱动（JSON）。
- **PCB 方块实体生成**：设计完成后将设计数据编译为多层堆叠方块（每层用颜色/纹理区分），BlockEntity 持久化设计数据。
- **实时电气仿真引擎**：基于节点电压法（MNA 简化版）的 tick 级求解，计算电压/电流分布、信号延迟、短路、过载发热。
- **可编程芯片方块**：受限 Lua（Luaj）沙盒，玩家编写固件控制芯片 I/O，沙盒限制指令数/内存/时钟。
- **调试与交互工具**：信号探针、电压/电流浮窗、信号流动粒子/颜色可视化、元件热插拔更换。
- **电力回路约束**：参考 Create: Power Grid，要求闭合回路与电源，避免红石式"无限供电"。

- **BREAKING**：无（全新模组，不修改原版行为，仅注册新内容）。

## Impact

- Affected specs: 无（基础/首个 spec）
- Affected code: 全新项目结构
  - `src/main/java/pcbcraft/` —— 核心 Java 包
  - `src/main/resources/data/pcbcraft/components/` —— 元件库 JSON
  - `src/main/resources/assets/pcbcraft/` —— 模型/纹理/lang
  - `build.gradle` / `mods.toml` —— Forge 构建配置
- 关键子系统：
  - `editor`（GUI 屏幕）、`data`（设计数据模型）、`library`（元件库）、`block`（PCB 方块/BlockEntity）、`sim`（仿真引擎）、`chip`（Lua 沙盒）、`tool`（调试工具）、`render`（可视化）

## ADDED Requirements

### Requirement: PCB 编辑器 GUI

系统 SHALL 提供一个游戏内屏幕（Screen）作为 PCB 编辑器，支持多层设计与布线。

#### Scenario: 图层管理
- **WHEN** 玩家打开 PCB 编辑器
- **THEN** 系统展示可配置图层列表（顶层铜、内层铜×N、底层铜、阻焊层、丝印层、钻孔层），玩家可切换可见/可编辑层，默认铜层 2 层。

#### Scenario: 元件放置
- **WHEN** 玩家从元件库选择一个元件并点击设计区
- **THEN** 系统按元件封装在当前铜层放置焊盘，元件可旋转/移动/删除，并阻止与已有元件焊盘非法重叠。

#### Scenario: 布线
- **WHEN** 玩家选择"布线"工具并依次点击两个焊盘
- **THEN** 系统在当前铜层生成曼哈顿路径铜走线，遇阻焊/其他铜冲突时高亮报错；走线宽度按网络约束设置。

#### Scenario: 过孔
- **WHEN** 玩家在走线上放置过孔
- **THEN** 系统在所有铜层对应坐标建立电气连接，并在阻焊层开窗。

#### Scenario: 设计规则检查（DRC）
- **WHEN** 玩家点击"编译生成"或手动触发 DRC
- **THEN** 系统检查最小线宽/间距/过孔孔径/未连接网络/短路网络，并在错误列表中定位坐标，存在致命错误时禁止生成方块。

### Requirement: 标准化元件库系统

系统 SHALL 提供数据驱动（JSON）的元件库，每个元件定义符号、封装、引脚与电气模型。

#### Scenario: 元件定义结构
- **WHEN** 模组加载 `data/pcbcraft/components/<id>.json`
- **THEN** 文件包含字段：`id`、`name`、`category`、`symbol`（原理图形状）、`footprint`（焊盘坐标/尺寸/层）、`pins`（引脚编号与网络名映射）、`model`（电气模型：电阻/电容/IC/电源/接地/二极管/三极管/逻辑门 + 参数）。

#### Scenario: 内置基础元件
- **WHEN** 玩家打开元件库面板
- **THEN** 至少包含：电阻、电容、电感、二极管、LED、按键、电源(V)、地(GND)、逻辑门(AND/OR/NOT/NAND/NOR/XOR)、D 触发器、运放、微控制器芯片、连接器。

#### Scenario: 第三方扩展
- **WHEN** 玩家将符合格式的 JSON 放入 datapack
- **THEN** 模组在重载后将其作为可用元件加载，错误格式给出日志告警并跳过。

### Requirement: PCB 方块实体生成与渲染

系统 SHALL 将编译通过的设计数据生成游戏内多层 PCB 方块，并由 BlockEntity 持久化。

#### Scenario: 编译生成
- **WHEN** 设计通过 DRC 且玩家确认生成
- **THEN** 系统在世界中放置一坨多层堆叠方块（每个游戏方块代表 PCB 的一个分层切片），层数 = 设计铜层数 + 阻焊 + 丝印，方块按层用不同颜色/纹理区分。

#### Scenario: 数据持久化
- **WHEN** 区块卸载/重载
- **THEN** BlockEntity 保存完整设计数据（网络表、元件实例、走线、过孔、状态），重载后恢复且仿真状态可恢复（可选快照）。

#### Scenario: 视觉分层
- **WHEN** 玩家观察已生成的 PCB
- **THEN** 铜层走线以铜色显示、阻焊层以绿色覆盖（露出焊盘）、丝印层以白色标注元件位号，玩家可按层切换可见性（潜行+右键切层）。

### Requirement: 实时电气仿真引擎

系统 SHALL 在服务器 tick（20 TPS）上对每块已生成 PCB 求解简化电路方程，并驱动电气行为。

#### Scenario: 节点电压法求解
- **WHEN** PCB 上电（含电源元件）
- **THEN** 系统构建网络表，用简化 MNA（修正节点分析）求各节点电压与支路电流，单板每 tick 计算预算 ≤ 1ms（可配置），超预算降帧/分片。

#### Scenario: 信号传输延迟
- **WHEN** 信号沿走线传播
- **THEN** 系统按走线长度计算传播延迟（每格 N tick，可配置），延迟可视化为信号头粒子移动。

#### Scenario: 短路保护
- **WHEN** 两个不同网络（含电源对地）被走线/过孔直接短接
- **THEN** 系统判定短路，触发保护：断开或限流（可配置策略），并在 PCB 上冒烟粒子告警。

#### Scenario: 过载发热
- **WHEN** 元件实际功率超过额定功耗
- **THEN** 元件发热值累积，达到阈值时变为"烧毁"状态（变黑方块/失效），相邻元件受热扩散影响。

#### Scenario: 闭合回路约束
- **WHEN** 玩家试图由无电源/无回路的网络驱动负载
- **THEN** 系统不建立电流（电压为 0），并在调试工具中提示"开路"，杜绝红石式无限供电。

### Requirement: 可编程芯片方块与受限脚本

系统 SHALL 提供微控制器芯片方块，玩家可在游戏内编辑并运行受限 Lua 固件。

#### Scenario: 脚本编辑
- **WHEN** 玩家右键芯片方块打开编程终端
- **THEN** 系统提供代码编辑界面，暴露 API：`pin.read(n)`/`pin.write(n,v)`/`sleep(t)`/`print()`/`analog.read(n)`/`pwm(n,duty)` 等。

#### Scenario: 沙盒限制
- **WHEN** 固件运行
- **THEN** 系统在 Luaj 沙盒中执行，限制：每 tick 指令数上限（默认 10000）、无文件/网络/OS 访问、无无限循环（超限挂起当 tick）、内存上限；超限安全中止并报错到终端。

#### Scenario: I/O 联动仿真
- **WHEN** 固件调用 `pin.write(3, 1)`
- **THEN** 芯片对应引脚在网络表中驱动高电平，影响仿真结果；引脚电平变化也通过 `pin.read` 反映给固件。

### Requirement: 调试与玩家交互工具

系统 SHALL 提供调试工具降低学习成本并支持运行时交互。

#### Scenario: 信号探针
- **WHEN** 玩家用"探针"物品右键点击铜走线/焊盘
- **THEN** 显示该点当前电压/电流/所属网络的浮窗，并可选周期采样显示波形（近 N tick）。

#### Scenario: 信号流动可视化
- **WHEN** 玩家手持"示波器"或开启可视化
- **THEN** 走线根据电平高/低显示颜色（高=红/低=蓝），电流方向以粒子流动表示，模拟信号以渐变色显示。

#### Scenario: 元件热插拔
- **WHEN** 玩家用焊台物品右键已焊接元件
- **THEN** 系统进入拆卸模式，取出元件并保留焊盘；放入新同封装元件自动接入网络，无需重新编译（增量更新网络表）。

#### Scenario: 电压全局视图
- **WHEN** 玩家切换"网络电压视图"
- **THEN** 整板按节点电压高低用热力色（蓝→红）染色，便于快速定位异常节点。

## MODIFIED Requirements

无（首个 spec，无既有需求修改）。

## REMOVED Requirements

无。
