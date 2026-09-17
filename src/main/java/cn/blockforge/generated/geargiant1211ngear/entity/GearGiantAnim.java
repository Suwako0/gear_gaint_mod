package cn.blockforge.generated.geargiant1211ngear.entity;

import net.minecraft.util.Mth;

/**
 * 齿轮巨人的动作姿势编号：服务端战斗状态机在每次进出招式时通过实体数据同步给客户端，
 * 客户端模型据此播放对应关键帧动画。{@link #duration(int)} 必须与服务端各招式的
 * 状态时长保持一致（允许 ±2 刻误差，动画进度会被钳制到 0~1）。
 */
public final class GearGiantAnim {
    public static final int NONE = 0;
    /** 近身砸击：双臂举过头顶蓄力。 */
    public static final int MELEE_WINDUP = 1;
    /** 近身砸击：双臂猛挥落下。 */
    public static final int MELEE_STRIKE = 2;
    public static final int CHARGE_WINDUP = 3;
    public static final int CHARGE_RUSH = 4;
    /** 跳跃砸击：下蹲蓄力。 */
    public static final int LEAP_CROUCH = 5;
    /** 跳跃砸击：空中展体。 */
    public static final int LEAP_AIR = 6;
    /** 跳跃砸击：落地深蹲卸力。 */
    public static final int LEAP_LAND = 7;
    /** 旋风横扫：整机自转。 */
    public static final int SPIN = 8;
    public static final int BARRAGE = 9;
    public static final int STEAM = 10;
    public static final int BEAM_WARM = 11;
    public static final int BEAM_FIRE = 12;
    public static final int PULL = 13;
    /** 踩踏：抬腿跺地，触发地裂齿轮。 */
    public static final int STOMP = 14;
    /** 机械钩爪：右臂前探锁定目标（蓄力）。 */
    public static final int HOOK_WINDUP = 15;
    /** 机械钩爪：掷出爪头的甩臂瞬间。 */
    public static final int HOOK_THROW = 16;
    /** 机械钩爪：爪头命中玩家后，巨人双手收链拖拽。 */
    public static final int HOOK_PULL = 17;
    /** 近身连招第二段：横摆背抽。 */
    public static final int MELEE_SWEEP = 18;

    /** 各姿势的标称时长（刻），客户端用它把经过刻数折算成 0~1 的动画进度。 */
    public static float duration(int pose) {
        return switch (pose) {
            case MELEE_WINDUP -> 18.0F;
            case MELEE_STRIKE -> 10.0F;
            case MELEE_SWEEP -> 10.0F;
            case CHARGE_WINDUP -> 26.0F;
            case CHARGE_RUSH -> 50.0F;
            case LEAP_CROUCH -> 16.0F;
            case LEAP_AIR -> 30.0F;
            case LEAP_LAND -> 18.0F;
            case SPIN -> 46.0F;
            case BARRAGE -> 48.0F;
            case STEAM -> 24.0F;
            case BEAM_WARM -> 30.0F;
            case BEAM_FIRE -> 50.0F;
            case PULL -> 50.0F;
            case STOMP -> 26.0F;
            case HOOK_WINDUP -> 16.0F;
            case HOOK_THROW -> 18.0F;
            case HOOK_PULL -> 44.0F;
            default -> 1.0F;
        };
    }

    public static float progress(float elapsedTicks, int pose) {
        return Mth.clamp(elapsedTicks / duration(pose), 0.0F, 1.0F);
    }

    /** 平滑收尾（开始快、结尾慢）。 */
    public static float easeOut(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - (1.0F - c) * (1.0F - c);
    }

    /** 平滑起落（两端慢、中间快）。 */
    public static float easeInOut(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    /** 单峰脉冲：0→1→0，pow 控制峰值出现的早晚（<1 提前到达峰值）。 */
    public static float pulse(float t, float pow) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return Mth.sin((float) Math.pow(c, pow) * Mth.PI);
    }

    private GearGiantAnim() {
    }
}
