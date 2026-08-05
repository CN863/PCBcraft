package com.pcbcraft.chip;

import com.pcbcraft.PCBConfig;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * 单芯片 Lua 运行时（Task 5.2）。
 * <p>
 * 每个运行中的 {@link ChipBlockEntity} 持有一个 {@code ChipRuntime}。固件以 Lua 协程形式运行，
 * 每个 tick 由 {@link ChipTickScheduler} 调用一次 {@link #resumeTick()}，执行到下一个
 * {@code sleep()} 让出或本 tick 指令预算耗尽为止。
 * </p>
 *
 * <h3>沙盒限制机制</h3>
 * <ul>
 *   <li><b>受限 Globals</b>：基于 {@link JsePlatform#standardGlobals()} 裁剪掉 {@code os/io/package}
 *       及 {@code dofile/loadfile/require}，仅保留安全的基础库、{@code math}、{@code string}、
 *       {@code table}、{@code coroutine}。</li>
 *   <li><b>协程 + 让出</b>：固件须通过 {@code sleep(n)} 让出（实现为 {@code coroutine.yield(n)}），
 *       否则单次 resume 只能跑完一段后结束；{@code sleep} 由前置 preamble 注入。</li>
 *   <li><b>每 tick 指令计数硬上限</b>：经查 luaj 3.0.1 的 {@link DebugLib} <em>确实</em>支持
 *       {@code debug.sethook(hook, "", count)} 计数钩子（{@link DebugLib#onInstruction} 中
 *       {@code s.bytecodes % s.hookcount == 0} 触发）。本运行时利用该机制，以
 *       {@link PCBConfig#luaInstructionLimitPerTick()} 为预算，超过即抛 {@link LuaError}
 *       中止本次 resume（{@code coroutine.resume} 受保护，捕获后返回 {@code false}），
 *       协程死亡则下 tick 重建（同脚本重启），从而把每 tick 执行量硬性限制在预算附近，
 *       避免固件死循环拖垮服务端 tick。</li>
 *   <li><b>二级时间保护</b>：单次 resume 超过 {@link #HARD_TIME_NS}（10ms）则强制停止运行时，
 *       兜底防止 Java 侧异常长耗时。</li>
 *   <li><b>内存</b>：luaj 无法硬限内存，仅以 {@code try/catch OutOfMemoryError} 兜底中止。</li>
 * </ul>
 *
 * <h3>暴露 API</h3>
 * <ul>
 *   <li>{@code pin.read(n)} → 引脚 n 数字读（&gt;2.5V→1，否则 0）</li>
 *   <li>{@code pin.write(n,v)} → 引脚 n 数字写（v≠0→5.0V，否则 0.0V）</li>
 *   <li>{@code analog.read(n)} → 引脚 n 模拟读（原始电压）</li>
 *   <li>{@code pwm(n,duty)} → 引脚 n PWM 写（duty∈[0,1]→0..5V）</li>
 *   <li>{@code sleep(n)} → 让出 n tick（{@code coroutine.yield(n)}）</li>
 *   <li>{@code print(...)} → 追加到控制台输出缓冲</li>
 * </ul>
 */
public final class ChipRuntime {

    /** 指令计数钩子粒度（每 N 条字节码触发一次检查）。 */
    private static final int GRANULARITY = 100;
    /** 单次 resume 硬时间上限（纳秒，10ms）。 */
    private static final long HARD_TIME_NS = 10_000_000L;
    /** 指令超限错误哨兵文本（resume 返回的错误信息中包含此串即判定为超限）。 */
    private static final String LIMIT_SENTINEL = "PCBCRAFT_INSTRUCTION_LIMIT";
    /** sleep preamble：定义 sleep 为 coroutine.yield。 */
    private static final String PREAMBLE = "sleep = function(n) coroutine.yield(n) end\n";

    private final ChipBlockEntity be;
    private Globals globals;
    private LuaValue co;
    private final LuaValue resumeFn;
    private final LuaValue statusFn;
    private final HookFunc hookFunc;

    /** 是否仍可继续运行（脚本结束/出错则置 false）。 */
    private boolean alive = true;
    /** 上一 tick 是否因指令超限中止（用于下 tick 重建协程）。 */
    private boolean limitHit;
    /** 本 tick 已执行指令计数（每 tick 重置）。 */
    private int tickInstructions;
    /** 指令预算（来自配置）。 */
    private final int budget;
    /** 控制台输出缓冲。 */
    private final StringBuilder console = new StringBuilder();

    private ChipRuntime(ChipBlockEntity be) {
        this.be = be;
        this.budget = Math.max(1, PCBConfig.luaInstructionLimitPerTick());
        this.hookFunc = new HookFunc();
        this.globals = JsePlatform.standardGlobals();
        pruneGlobals(globals);
        // 装载 DebugLib 以启用指令计数钩子（onInstruction 仅在 debuglib 存在时被解释器调用）
        globals.load(new DebugLib());
        installApi();
        this.resumeFn = globals.get("coroutine").get("resume");
        this.statusFn = globals.get("coroutine").get("status");
    }

    /**
     * 创建并初始化运行时：编译脚本、创建协程、挂指令钩子。
     *
     * @param be 所属芯片方块实体
     * @return 运行时实例；脚本语法错误时返回 alive=false 的实例
     */
    public static ChipRuntime create(ChipBlockEntity be) {
        ChipRuntime rt = new ChipRuntime(be);
        rt.initCoroutine();
        return rt;
    }

    private void initCoroutine() {
        String full = PREAMBLE + be.getScript();
        LuaValue func;
        try {
            func = globals.load(full, "chip");
        } catch (LuaError e) {
            append("[load error] ").append(e.getMessage()).append('\n');
            alive = false;
            return;
        }
        try {
            co = globals.get("coroutine").get("create").call(func);
        } catch (LuaError e) {
            append("[create error] ").append(e.getMessage()).append('\n');
            alive = false;
            return;
        }
        installHook(co);
    }

    /** 重建协程（同脚本），用于指令超限后下 tick 恢复。 */
    private void recreateCoroutine() {
        try {
            LuaValue func = globals.load(PREAMBLE + be.getScript(), "chip");
            co = globals.get("coroutine").get("create").call(func);
            installHook(co);
        } catch (LuaError e) {
            append("[restart error] ").append(e.getMessage()).append('\n');
            alive = false;
        }
    }

    /** 在协程上挂指令计数钩子（mask 为空串，仅靠 count 触发）。 */
    private void installHook(LuaValue thread) {
        globals.get("debug").get("sethook").invoke(
                LuaValue.varargsOf(new LuaValue[]{thread, hookFunc, LuaValue.valueOf(""), LuaValue.valueOf(GRANULARITY)}));
    }

    /** 每服务端 tick 调用一次：恢复协程执行，受指令预算与时间双重约束。 */
    public void resumeTick() {
        if (!alive || co == null) {
            return;
        }
        // 协程已死：按原因决定重建或停止
        if (isDead()) {
            if (limitHit) {
                limitHit = false;
                recreateCoroutine();
                if (!alive || co == null) {
                    return;
                }
            } else {
                alive = false;
                return;
            }
        }

        tickInstructions = 0;
        long start = System.nanoTime();
        Varargs r;
        try {
            r = resumeFn.invoke(co);
        } catch (LuaError e) {
            // resume 未自行捕获时兜底
            r = LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(e.getMessage()));
        } catch (OutOfMemoryError oom) {
            append("[out of memory, halted]\n");
            alive = false;
            return;
        }
        long elapsed = System.nanoTime() - start;

        boolean ok = r.arg1().toboolean();
        if (ok) {
            if (isDead()) {
                // 脚本正常执行完毕（无循环或 break 退出）
                alive = false;
                append("[finished]\n");
            }
            // 否则已 yield（suspended），等待下 tick
        } else {
            String msg = r.arg(2).tojstring();
            if (msg != null && msg.contains(LIMIT_SENTINEL)) {
                limitHit = true;
                append("[instruction limit exceeded, will restart next tick]\n");
                // alive 保持 true，下 tick 重建协程
            } else {
                append("[runtime error] ").append(msg).append('\n');
                alive = false;
            }
        }

        // 二级时间兜底
        if (elapsed > HARD_TIME_NS) {
            append("[tick time exceeded hard limit, halting]\n");
            alive = false;
        }
    }

    private boolean isDead() {
        return "dead".equals(statusFn.call(co).tojstring());
    }

    public boolean isAlive() {
        return alive;
    }

    public void stop() {
        alive = false;
    }

    /** 取出并清空控制台输出缓冲。 */
    public String drainOutput() {
        synchronized (console) {
            String s = console.toString();
            if (s.length() > 8192) {
                s = s.substring(s.length() - 8192);
            }
            console.setLength(0);
            return s;
        }
    }

    private StringBuilder append(String s) {
        synchronized (console) {
            return console.append(s);
        }
    }

    // ===== 沙盒构建 =====

    private static void pruneGlobals(Globals g) {
        g.set("os", LuaValue.NIL);
        g.set("io", LuaValue.NIL);
        g.set("package", LuaValue.NIL);
        g.set("dofile", LuaValue.NIL);
        g.set("loadfile", LuaValue.NIL);
        g.set("require", LuaValue.NIL);
    }

    private void installApi() {
        globals.set("print", new PrintFunc());
        LuaTable pin = new LuaTable();
        pin.set("read", new PinReadFunc());
        pin.set("write", new PinWriteFunc());
        globals.set("pin", pin);
        LuaTable analog = new LuaTable();
        analog.set("read", new AnalogReadFunc());
        globals.set("analog", analog);
        globals.set("pwm", new PwmFunc());
        // 隐藏 debug 表，避免脚本篡改钩子；globals.debuglib（实例）仍保留以驱动 onInstruction
        globals.set("debug", LuaValue.NIL);
    }

    // ===== Lua 函数实现 =====

    /** print：拼接收集到控制台缓冲。 */
    private final class PrintFunc extends VarArgFunction {
        @Override
        public Varargs invoke(Varargs args) {
            StringBuilder sb = new StringBuilder();
            int n = args.narg();
            for (int i = 1; i <= n; i++) {
                if (i > 1) {
                    sb.append('\t');
                }
                sb.append(args.arg(i).tojstring());
            }
            sb.append('\n');
            append(sb.toString());
            return LuaValue.NONE;
        }
    }

    /** pin.read(n)：数字读，>2.5V→1 否则 0。 */
    private final class PinReadFunc extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue n) {
            return LuaValue.valueOf(digitalRead(n.checkint()));
        }
    }

    /** pin.write(n,v)：数字写，v≠0→5.0V 否则 0.0V。 */
    private final class PinWriteFunc extends TwoArgFunction {
        @Override
        public LuaValue call(LuaValue n, LuaValue v) {
            digitalWrite(n.checkint(), v.todouble());
            return LuaValue.NONE;
        }
    }

    /** analog.read(n)：模拟读，返回原始电压。 */
    private final class AnalogReadFunc extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue n) {
            int i = n.checkint();
            return LuaValue.valueOf(be.getPinInput(i));
        }
    }

    /** pwm(n,duty)：PWM 写，duty∈[0,1]→0..5V。 */
    private final class PwmFunc extends TwoArgFunction {
        @Override
        public LuaValue call(LuaValue n, LuaValue duty) {
            int i = n.checkint();
            double d = duty.todouble();
            if (d < 0.0) d = 0.0;
            if (d > 1.0) d = 1.0;
            be.setPinOutput(i, d * 5.0);
            return LuaValue.NONE;
        }
    }

    private int digitalRead(int i) {
        return be.getPinInput(i) > 2.5 ? 1 : 0;
    }

    private void digitalWrite(int i, double v) {
        be.setPinOutput(i, (v != 0.0) ? 5.0 : 0.0);
    }

    /** 指令计数钩子：每 GRANULARITY 条字节码触发，超预算抛错中止本次 resume。 */
    private final class HookFunc extends TwoArgFunction {
        @Override
        public LuaValue call(LuaValue type, LuaValue arg) {
            tickInstructions += GRANULARITY;
            if (tickInstructions >= budget) {
                throw new LuaError(LIMIT_SENTINEL);
            }
            return LuaValue.NONE;
        }
    }
}
