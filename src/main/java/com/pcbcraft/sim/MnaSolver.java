package com.pcbcraft.sim;

import com.pcbcraft.PCBCraft;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 修正节点分析（Modified Nodal Analysis）简化求解器。
 * <p>
 * 将 {@link Netlist} 中的线性支路（电阻/电容/电感/电压源/二极管/运放）与
 * {@link DigitalSimulator} 提供的数字驱动电压源组装为 MNA 线性方程组 Ax=z，
 * 用自实现高斯消元（部分主元法）求解，输出节点电压与支路电流，并检测短路/开路。
 * </p>
 * <p>
 * 简化策略（非动态仿真，真正瞬态留后续阶段）：
 * <ul>
 *   <li>电阻 → 电导 G=1/R；</li>
 *   <li>电容 → 准静态近似为大电阻 R=1e6（开路视）；电感 → 小电阻 R=1e-3（短路视）；</li>
 *   <li>电压源 → 增加支路电流未知量（MNA 标准）；</li>
 *   <li>二极管/LED → 线性化为正向压降电压源串联小电阻（恒定导通假设）；</li>
 *   <li>运放 → 电压控制电压源 VCVS；</li>
 *   <li>数字逻辑门/DFF/MCU → 不进线性 MNA，由 {@link DigitalSimulator} 计算后以理想
 *       0/5V 电压源形式注入对应输出节点。</li>
 * </ul>
 * </p>
 * <p>数值稳定性：电阻下限 1e-9，电导上限 1e9 防溢出。</p>
 */
public final class MnaSolver {

    /** 电阻下限，避免无穷大电导。 */
    private static final double R_MIN = 1e-9;
    /** 电导上限，对应 R_MIN。 */
    private static final double G_MAX = 1e9;
    /** 电容准静态近似电阻。 */
    private static final double CAPACITOR_R = 1e6;
    /** 电感准静态近似电阻。 */
    private static final double INDUCTOR_R = 1e-3;
    /** 闭合开关等效电阻（短接）。 */
    private static final double SWITCH_CLOSED_R = 1e-9;
    /** 二极管/LED 线性化串联小电阻。 */
    private static final double DIODE_SERIES_R = 1.0;
    /** 主元容差，小于此值视为奇异。 */
    private static final double PIVOT_TOL = 1e-12;
    /** 短路电流阈值（A）。 */
    private static final double SHORT_CURRENT_THRESHOLD = 1e6;

    private MnaSolver() {
    }

    /**
     * 求解网表。
     *
     * @param netlist 网表
     * @param digital 数字仿真器（提供输出节点驱动电压；可为 null）
     * @return 仿真解
     */
    public static SimSolution solve(Netlist netlist, DigitalSimulator digital) {
        int n = netlist.nodeCount();
        List<Netlist.NetlistBranch> branches = netlist.getBranches();
        double[] nodeVoltages = new double[n];
        double[] branchCurrents = new double[branches.size()];
        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("shortCircuit", false);
        flags.put("openCircuit", false);
        flags.put("budgetExceeded", false);
        flags.put("singular", false);

        if (n == 0) {
            return new SimSolution(nodeVoltages, branchCurrents, flags);
        }

        int ground = netlist.getGroundNodeIndex();
        if (ground < 0 || ground >= n) {
            ground = 0;
        }

        // 节点索引 → 电压未知量方程索引（地节点无未知量，标记 -1）
        int[] nodeEq = new int[n];
        Arrays.fill(nodeEq, -1);
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (i != ground) {
                nodeEq[i] = m++;
            }
        }

        // 统计额外电流未知量：电压源 / 二极管 / LED / 运放 / 数字驱动
        int extra = 0;
        for (Netlist.NetlistBranch b : branches) {
            switch (b.type) {
                case "V":
                case "D":
                case "LED":
                case "OPAMP":
                    extra++;
                    break;
                default:
                    break;
            }
        }
        Map<Integer, Double> drives = (digital != null) ? digital.getDriveVoltages() : Collections.emptyMap();
        int[] driveNodeArr = new int[drives.size()];
        int dc = 0;
        for (Integer nodeIdx : drives.keySet()) {
            if (nodeIdx != null && nodeIdx >= 0 && nodeIdx < n && nodeIdx != ground) {
                driveNodeArr[dc++] = nodeIdx;
                extra++;
            }
        }

        int size = m + extra;
        if (size == 0) {
            // 全部节点为地，电压恒 0
            return new SimSolution(nodeVoltages, branchCurrents, flags);
        }

        double[][] A = new double[size][size];
        double[] z = new double[size];
        int nextCurr = m; // 电流未知量从 m 开始编号
        int[] branchEq = new int[branches.size()]; // 支路 → 电流未知量方程索引，-1 表示无
        Arrays.fill(branchEq, -1);

        // 组装矩阵
        for (int bi = 0; bi < branches.size(); bi++) {
            Netlist.NetlistBranch b = branches.get(bi);
            int[] nd = b.nodes;
            switch (b.type) {
                case "R":
                case "C":
                case "L": {
                    double r = equivResistance(b);
                    stampConductance(A, nodeEq, n, nd, 1.0 / clampR(r));
                    break;
                }
                case "SW": {
                    if (!toBool(b.params.get("closed"))) {
                        break; // 断开：开路，不 stamp
                    }
                    stampConductance(A, nodeEq, n, nd, 1.0 / SWITCH_CLOSED_R);
                    break;
                }
                case "V": {
                    int ci = nextCurr++;
                    branchEq[bi] = ci;
                    double v = toDouble(b.params.get("voltage"), 0.0);
                    int p = eqOf(nd, 0, nodeEq, n);
                    int q = eqOf(nd, 1, nodeEq, n);
                    if (p >= 0) {
                        A[p][ci] += 1;
                        A[ci][p] += 1;
                    }
                    if (q >= 0) {
                        A[q][ci] -= 1;
                        A[ci][q] -= 1;
                    }
                    z[ci] = v;
                    break;
                }
                case "D":
                case "LED": {
                    // 线性化：正向压降电压源 Vf 串联小电阻 Rs（恒定导通假设）
                    int ci = nextCurr++;
                    branchEq[bi] = ci;
                    double vf = toDouble(b.params.get("forwardVoltage"), 0.7);
                    int a = eqOf(nd, 0, nodeEq, n); // 阳极
                    int c = eqOf(nd, 1, nodeEq, n); // 阴极
                    if (a >= 0) {
                        A[a][ci] += 1;
                        A[ci][a] += 1;
                    }
                    if (c >= 0) {
                        A[c][ci] -= 1;
                        A[ci][c] -= 1;
                    }
                    A[ci][ci] -= DIODE_SERIES_R;
                    z[ci] = vf;
                    break;
                }
                case "OPAMP": {
                    // VCVS: Vout = gain * (Vin+ - Vin-)
                    int ci = nextCurr++;
                    branchEq[bi] = ci;
                    double gain = toDouble(b.params.get("gain"), 1e5);
                    int ip = eqOf(nd, 0, nodeEq, n); // IN+
                    int im = eqOf(nd, 1, nodeEq, n); // IN-
                    int out = eqOf(nd, 2, nodeEq, n); // OUT
                    if (out >= 0) {
                        A[out][ci] += 1;
                        A[ci][out] += 1;
                    }
                    if (ip >= 0) {
                        A[ci][ip] -= gain;
                    }
                    if (im >= 0) {
                        A[ci][im] += gain;
                    }
                    z[ci] = 0.0;
                    break;
                }
                case "LOGIC":
                case "MCU":
                case "GND":
                    // 数字/芯片/地：不进线性 MNA（数字由驱动电压源注入）
                    break;
                default:
                    break;
            }
        }

        // 数字驱动：理想电压源（节点对地）
        for (int i = 0; i < dc; i++) {
            int nodeIdx = driveNodeArr[i];
            int ci = nextCurr++;
            int nd = nodeEq[nodeIdx];
            double v = drives.get(nodeIdx);
            if (nd >= 0) {
                A[nd][ci] += 1;
                A[ci][nd] += 1;
            }
            z[ci] = v;
        }

        // 求解
        double[] x = solveLinear(A, z);
        if (x == null) {
            flags.put("singular", true);
            flags.put("openCircuit", true);
            PCBCraft.LOGGER.warn("MNA 矩阵奇异（开路/无解/冲突源），网表节点数={} 支路数={}", n, branches.size());
            return new SimSolution(nodeVoltages, branchCurrents, flags);
        }

        // 回填节点电压
        for (int i = 0; i < n; i++) {
            if (i == ground) {
                nodeVoltages[i] = 0.0;
            } else {
                nodeVoltages[i] = x[nodeEq[i]];
            }
        }

        // 回填支路电流 + 短路检测
        boolean shortCircuit = false;
        for (int bi = 0; bi < branches.size(); bi++) {
            Netlist.NetlistBranch b = branches.get(bi);
            int[] nd = b.nodes;
            if (branchEq[bi] >= 0) {
                double i = x[branchEq[bi]];
                branchCurrents[bi] = i;
                if (Math.abs(i) > SHORT_CURRENT_THRESHOLD) {
                    shortCircuit = true;
                }
            } else {
                // 导纳型支路：由两端电压差计算电流（a→b）
                double va = voltageOf(nd, 0, nodeVoltages, n, ground);
                double vb = voltageOf(nd, 1, nodeVoltages, n, ground);
                switch (b.type) {
                    case "R":
                    case "C":
                    case "L": {
                        double r = clampR(equivResistance(b));
                        branchCurrents[bi] = (va - vb) / r;
                        break;
                    }
                    case "SW": {
                        if (toBool(b.params.get("closed"))) {
                            double i = (va - vb) / SWITCH_CLOSED_R;
                            branchCurrents[bi] = i;
                            if (Math.abs(i) > SHORT_CURRENT_THRESHOLD) {
                                shortCircuit = true;
                            }
                        } else {
                            branchCurrents[bi] = 0.0;
                        }
                        break;
                    }
                    default:
                        branchCurrents[bi] = 0.0;
                        break;
                }
            }
        }
        flags.put("shortCircuit", shortCircuit);

        return new SimSolution(nodeVoltages, branchCurrents, flags);
    }

    /**
     * 带预算限时求解：用 {@link System#nanoTime()} 计时，超预算时在 flags 中标记
     * {@code budgetExceeded=true} 并返回当前 best-effort 解。
     * <p>本阶段实现为对 {@link #solve} 计时（求解同步完成，超预算仅置标志）。</p>
     *
     * @param netlist      网表
     * @param digital      数字仿真器
     * @param budgetMicros 预算（微秒）
     * @return 仿真解
     */
    public static SimSolution solveBounded(Netlist netlist, DigitalSimulator digital, int budgetMicros) {
        long start = System.nanoTime();
        SimSolution sol = solve(netlist, digital);
        long elapsedNanos = System.nanoTime() - start;
        if (elapsedNanos > (long) budgetMicros * 1000L) {
            sol.flags.put("budgetExceeded", true);
        }
        return sol;
    }

    /**
     * 自实现高斯消元（部分主元法）求解 Ax=b。
     *
     * @param A 方阵
     * @param b 右端向量
     * @return 解向量；矩阵奇异（主元近 0）返回 null
     */
    public static double[] solveLinear(double[][] A, double[] b) {
        int n = b.length;
        if (n == 0) {
            return new double[0];
        }
        // 增广矩阵
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
        // 前向消元（部分主元）
        for (int col = 0; col < n; col++) {
            int piv = col;
            double maxAbs = Math.abs(m[col][col]);
            for (int r = col + 1; r < n; r++) {
                double v = Math.abs(m[r][col]);
                if (v > maxAbs) {
                    maxAbs = v;
                    piv = r;
                }
            }
            if (maxAbs < PIVOT_TOL) {
                return null; // 奇异
            }
            if (piv != col) {
                double[] tmp = m[piv];
                m[piv] = m[col];
                m[col] = tmp;
            }
            double pivotVal = m[col][col];
            for (int r = col + 1; r < n; r++) {
                double factor = m[r][col] / pivotVal;
                if (factor == 0.0) {
                    continue;
                }
                for (int c = col; c <= n; c++) {
                    m[r][c] -= factor * m[col][c];
                }
            }
        }
        // 回代
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = m[i][n];
            for (int j = i + 1; j < n; j++) {
                s -= m[i][j] * x[j];
            }
            if (Math.abs(m[i][i]) < PIVOT_TOL) {
                return null;
            }
            x[i] = s / m[i][i];
        }
        return x;
    }

    // ===== 组装/计算辅助 =====

    /**
     * 等效电阻：R/C/L/SW 的简化电阻模型。
     */
    private static double equivResistance(Netlist.NetlistBranch b) {
        switch (b.type) {
            case "R":
                return toDouble(b.params.get("resistance"), 1000.0);
            case "C":
                // 电容准静态近似：开路视为大电阻
                return CAPACITOR_R;
            case "L":
                // 电感准静态近似：短路视为小电阻
                return INDUCTOR_R;
            case "SW":
                return toBool(b.params.get("closed")) ? SWITCH_CLOSED_R : Double.POSITIVE_INFINITY;
            default:
                return 1e9;
        }
    }

    private static double clampR(double r) {
        if (r < R_MIN) {
            return R_MIN;
        }
        if (r > 1e9) {
            return 1e9;
        }
        return r;
    }

    /**
     * 电阻支路电导 stamp：在 a-b 间加电导 G。
     */
    private static void stampConductance(double[][] A, int[] nodeEq, int n, int[] nd, double gIn) {
        double G = Math.min(gIn, G_MAX);
        int a = eqOf(nd, 0, nodeEq, n);
        int bb = eqOf(nd, 1, nodeEq, n);
        if (a >= 0) {
            A[a][a] += G;
        }
        if (bb >= 0) {
            A[bb][bb] += G;
        }
        if (a >= 0 && bb >= 0) {
            A[a][bb] -= G;
            A[bb][a] -= G;
        }
    }

    /**
     * 取支路第 idx 端节点的电压方程索引；地节点或越界返回 -1。
     */
    private static int eqOf(int[] nd, int idx, int[] nodeEq, int n) {
        if (idx >= nd.length) {
            return -1;
        }
        int node = nd[idx];
        if (node < 0 || node >= n) {
            return -1;
        }
        return nodeEq[node];
    }

    /**
     * 取支路第 idx 端节点电压（用于回填导纳支路电流）。
     */
    private static double voltageOf(int[] nd, int idx, double[] nodeVoltages, int n, int ground) {
        if (idx >= nd.length) {
            return 0.0;
        }
        int node = nd[idx];
        if (node < 0 || node >= n) {
            return 0.0;
        }
        if (node == ground) {
            return 0.0;
        }
        return nodeVoltages[node];
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        return def;
    }

    private static boolean toBool(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue() != 0.0;
        }
        return false;
    }

    // ===== 仿真解 =====

    /**
     * MNA 仿真解：节点电压、支路电流与状态标志。
     */
    public static final class SimSolution {
        /** 节点电压（按节点索引）。 */
        public final double[] nodeVoltages;
        /** 支路电流（按支路索引）。 */
        public final double[] branchCurrents;
        /** 状态标志：shortCircuit / openCircuit / budgetExceeded / singular。 */
        public final Map<String, Object> flags;

        public SimSolution(double[] nodeVoltages, double[] branchCurrents, Map<String, Object> flags) {
            this.nodeVoltages = nodeVoltages;
            this.branchCurrents = branchCurrents;
            this.flags = flags;
        }

        /**
         * 返回指定节点电压。
         *
         * @param nodeIndex 节点索引
         * @return 电压（越界返回 0）
         */
        public double voltageAt(int nodeIndex) {
            if (nodeIndex < 0 || nodeIndex >= nodeVoltages.length) {
                return 0.0;
            }
            return nodeVoltages[nodeIndex];
        }

        /**
         * 返回指定支路电流。
         *
         * @param branchIndex 支路索引
         * @return 电流（越界返回 0）
         */
        public double currentOf(int branchIndex) {
            if (branchIndex < 0 || branchIndex >= branchCurrents.length) {
                return 0.0;
            }
            return branchCurrents[branchIndex];
        }

        public boolean isShortCircuited() {
            return toBool(flags.get("shortCircuit"));
        }

        public boolean isOpenCircuit() {
            return toBool(flags.get("openCircuit"));
        }

        public boolean isBudgetExceeded() {
            return toBool(flags.get("budgetExceeded"));
        }

        public boolean isSingular() {
            return toBool(flags.get("singular"));
        }
    }
}
