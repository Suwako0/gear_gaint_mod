package cn.blockforge.generated.geargiant1211ngear.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

/**
 * 通过反射读取 Create（机械动力）动力网络的旋转速度，
 * 避免对本模组工程引入 Create 的编译期依赖。
 * 目标是 com.simibubi.create.content.kinetics.base.KineticBlockEntity#getRotationSpeed()。
 */
public final class CreateStressProbe {
    private static final String KINETIC_CLASS = "com.simibubi.create.content.kinetics.base.KineticBlockEntity";
    private static final String[] CANDIDATE_METHODS = {"getRotationSpeed", "getTheoreticalSpeed"};

    private static boolean resolved;
    private static Class<?> kineticClass;
    private static Method speedMethod;

    /** 返回 pos 六个相邻方块中最大的旋转速度（RPM 绝对值）；无 Create 或无转动组件时为 0。 */
    public static float maxAdjacentRotation(Level level, BlockPos pos) {
        Class<?> clazz = resolve();
        if (clazz == null || speedMethod == null) {
            return 0.0F;
        }
        float max = 0.0F;
        for (Direction direction : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(direction));
            if (be == null || !clazz.isInstance(be)) {
                continue;
            }
            try {
                Object result = speedMethod.invoke(be);
                if (result instanceof Number number) {
                    max = Math.max(max, Math.abs(number.floatValue()));
                }
            } catch (Throwable ignored) {
                // Create 版本不匹配时静默降级为 0，不影响游戏。
            }
        }
        return max;
    }

    /** Create 是否可用（用于提示文本）。 */
    public static boolean createLoaded() {
        return resolve() != null;
    }

    private static synchronized Class<?> resolve() {
        if (resolved) {
            return kineticClass;
        }
        resolved = true;
        try {
            kineticClass = Class.forName(KINETIC_CLASS);
            for (String name : CANDIDATE_METHODS) {
                try {
                    speedMethod = kineticClass.getMethod(name);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // 尝试下一个候选名。
                }
            }
            if (speedMethod == null) {
                kineticClass = null;
            }
        } catch (Throwable t) {
            kineticClass = null;
            speedMethod = null;
        }
        return kineticClass;
    }

    private CreateStressProbe() {
    }
}
