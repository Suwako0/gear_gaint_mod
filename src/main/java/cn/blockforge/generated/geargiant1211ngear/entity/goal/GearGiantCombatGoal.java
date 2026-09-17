package cn.blockforge.generated.geargiant1211ngear.entity.goal;

import cn.blockforge.generated.geargiant1211ngear.entity.GearGiantAnim;
import cn.blockforge.generated.geargiant1211ngear.entity.GearGiantEntity;
import cn.blockforge.generated.geargiant1211ngear.entity.GearProjectileEntity;
import cn.blockforge.generated.geargiant1211ngear.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 齿轮巨人的战斗状态机（每次进出招式都会把 {@link GearGiantAnim} 姿势同步给模型）。
 * 走位：远距接近 → 中距弧线环绕 → 过近后撤，不再钉在原地。
 * 一阶段：两段连招（砸击+横扫）/ 旋转冲撞 / 跳跃砸击 / 齿轮弹幕 / 机械钩爪（反放风筝）；
 * 二阶段：追加旋风横扫、蒸汽喷发（护盾与召唤由实体在硬直结束时处理）；
 * 三阶段：追加过载激光、踩踏地裂、齿轮暴雨与传送带拉扯（暴走工厂）。
 * 所有固定招式伤害统一乘 skillDamageScale 配置倍率。
 */
public class GearGiantCombatGoal extends Goal {
    private static final int NONE = 0;
    private static final int CHARGE_WINDUP = 1;
    private static final int CHARGE_RUSH = 2;
    private static final int BARRAGE = 3;
    private static final int STEAM_WARN = 4;
    private static final int BEAM_WARM = 5;
    private static final int BEAM_FIRE = 6;
    private static final int PULL = 7;
    private static final int MELEE_WINDUP = 8;
    private static final int MELEE_STRIKE = 9;
    private static final int LEAP_WINDUP = 10;
    private static final int LEAP_AIR = 11;
    private static final int LEAP_LAND = 12;
    private static final int SPIN = 13;
    private static final int STORM = 14;
    private static final int STOMP = 15;
    private static final int HOOK_WINDUP = 16;
    private static final int HOOK_THROW = 17;
    private static final int MELEE_SWEEP = 18;

    private static final int SPIN_TICKS = 46;
    private static final int LEAP_CROUCH_TICKS = 16;

    private final GearGiantEntity giant;
    private int state = NONE;
    private int stateTime;
    private int attackLock;
    private int slamCd;
    private int chargeCd;
    private int barrageCd;
    private int steamCd;
    private int beamCd;
    private int eruptCd;
    private int pullCd;
    private int leapCd;
    private int spinCd;
    private int stormCd;
    private int hookCd;
    private cn.blockforge.generated.geargiant1211ngear.entity.HookClawEntity activeHook;
    /** 钩爪锁定的玩家：从出手起即绑定，爪头飞行中会持续追踪这个人。 */
    private Player hookVictim;
    private double rushX;
    private double rushZ;
    private int barrageWaves;
    private int barrageTick;
    private LivingEntity beamVictim;
    private int beamHitTick;
    private boolean strikeDone;
    private int airTicks;
    private double circleOffset = 7.0;
    private int circleFlipCd = 60;

    public GearGiantCombatGoal(GearGiantEntity giant) {
        this.giant = giant;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (this.giant.isStaggered()) {
            return false;
        }
        return this.state != NONE || validTarget(this.giant.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        cancelAttack();
        this.giant.setFlag(GearGiantEntity.FLAG_PULL, false);
    }

    /** 状态机是否正在执行招式（钩爪姿势接管与 endAttack 收尾都要看它）。 */
    public boolean isAttacking() {
        return this.state != NONE;
    }

    /** 转阶段时由实体调用，强制中断当前招式。 */
    public void cancelAttack() {
        this.state = NONE;
        this.beamVictim = null;
        this.attackLock = 20;
        if (this.activeHook != null && !this.activeHook.isRemoved()) {
            this.activeHook.discard();
        }
        this.activeHook = null;
        this.hookVictim = null;
        this.giant.endHookPull();
        this.giant.setFlag(GearGiantEntity.FLAG_CHARGE, false);
        this.giant.setFlag(GearGiantEntity.FLAG_BEAM_WARM, false);
        this.giant.setFlag(GearGiantEntity.FLAG_BEAM_FIRE, false);
        pose(GearGiantAnim.NONE);
    }

    @Override
    public void tick() {
        Level level = this.giant.level();
        LivingEntity target = this.giant.getTarget();

        if (this.attackLock > 0) this.attackLock--;
        if (this.slamCd > 0) this.slamCd--;
        if (this.chargeCd > 0) this.chargeCd--;
        if (this.barrageCd > 0) this.barrageCd--;
        if (this.steamCd > 0) this.steamCd--;
        if (this.beamCd > 0) this.beamCd--;
        if (this.eruptCd > 0) this.eruptCd--;
        if (this.pullCd > 0) this.pullCd--;
        if (this.leapCd > 0) this.leapCd--;
        if (this.spinCd > 0) this.spinCd--;
        if (this.stormCd > 0) this.stormCd--;
        if (this.hookCd > 0) this.hookCd--;

        // 钩爪牵着玩家收链时：站定保持收链姿势，不开新招
        if (this.state == NONE && this.giant.isHookReeling()) {
            this.giant.getNavigation().stop();
            pose(GearGiantAnim.HOOK_PULL);
            if (validTarget(target)) {
                this.giant.getLookControl().setLookAt(target, 20.0F, 20.0F);
            }
            return;
        }

        if (this.state != NONE) {
            runState(level, target);
            return;
        }
        if (!validTarget(target)) {
            this.giant.getNavigation().stop();
            pose(GearGiantAnim.NONE);
            return;
        }

        this.giant.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distSq = this.giant.distanceToSqr(target);
        double dist = Math.sqrt(distSq);

        // ---- 走位：远追、中绕、近撤，让巨人始终在机动而不是原地罚站
        if (dist > 10.0) {
            this.giant.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(),
                    this.giant.phaseSpeedModifier());
        } else if (dist < 4.6 && this.slamCd > 8) {
            Vec3 away = this.giant.position().subtract(target.position());
            this.giant.getNavigation().moveTo(this.giant.getX() + away.x * 2.0,
                    this.giant.getY(), this.giant.getZ() + away.z * 2.0, 0.75);
        } else {
            reposition(target, dist);
        }

        // ---- 近身蓄力砸击（两段式：举臂蓄力 -> 挥落 + 震波）
        if (distSq <= 34.0 && this.slamCd <= 0 && this.attackLock <= 0) {
            startMelee();
            return;
        }
        maybeStartAttack(target, distSq);
    }

    /** 中距离绕圈：沿目标切线游走，偶尔换向，保持约 7 格的压迫距离。 */
    private void reposition(LivingEntity target, double dist) {
        if (--this.circleFlipCd <= 0) {
            this.circleOffset = -this.circleOffset;
            this.circleFlipCd = 50 + this.giant.getRandom().nextInt(50);
        }
        Vec3 to = target.position().subtract(this.giant.position());
        to = new Vec3(to.x, 0.0, to.z);
        double len = Math.max(1.0E-4, to.length());
        Vec3 tangent = new Vec3(-to.z / len, 0.0, to.x / len).scale(Math.signum(this.circleOffset));
        double pullIn = (dist - 7.0) * 0.6;
        Vec3 dest = this.giant.position()
                .add(tangent.scale(2.6))
                .add(to.scale(pullIn / len));
        this.giant.getNavigation().moveTo(dest.x, dest.y, dest.z,
                0.85 * this.giant.phaseSpeedModifier());
    }

    // ---------------------------------------------------------------- 招式挑选

    private void maybeStartAttack(LivingEntity target, double distSq) {
        if (this.attackLock > 0 || !validTarget(target)) {
            return;
        }
        int phase = this.giant.getGiantPhase();
        List<Integer> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        // 机械钩爪：反放风筝专用。锁定条件不看当前目标距离，
        // 而是"配置区间内存在任意可钩的玩家"——出手必有人被勾。
        if (cn.blockforge.generated.geargiant1211ngear.config.ModConfigs.GIANT_HOOK_ENABLED.get()
                && this.hookCd <= 0 && !this.giant.isHookReeling()) {
            Player hookCandidate = pickHookVictim(target);
            if (hookCandidate != null) {
                add(pool, weights, HOOK_WINDUP, 5.5);
                this.hookVictim = hookCandidate;
            }
        }
        if (this.leapCd <= 0 && distSq > 25.0 && distSq < 340.0) {
            add(pool, weights, LEAP_WINDUP, 5.0);
        }
        if (this.chargeCd <= 0 && distSq > 42.0) {
            add(pool, weights, CHARGE_WINDUP, 4.0);
        }
        if (this.spinCd <= 0 && distSq < 80.0 && phase >= 2) {
            add(pool, weights, SPIN, 4.0);
        }
        if (this.barrageCd <= 0 && distSq > 16.0) {
            add(pool, weights, BARRAGE, 3.0);
        }
        if (phase >= 2 && this.steamCd <= 0 && distSq < 150.0) {
            add(pool, weights, STEAM_WARN, 3.0);
        }
        if (phase >= 3) {
            if (this.beamCd <= 0) {
                add(pool, weights, BEAM_WARM, 3.0);
            }
            if (this.stormCd <= 0 && distSq > 20.0) {
                add(pool, weights, STORM, 3.0);
            }
            if (this.eruptCd <= 0 && distSq < 120.0) {
                add(pool, weights, STOMP, 3.0);
            }
            if (this.pullCd <= 0 && distSq > 64.0) {
                add(pool, weights, PULL, 2.0);
            }
        }
        if (pool.isEmpty()) {
            return;
        }
        double total = 0.0;
        for (double w : weights) {
            total += w;
        }
        double roll = this.giant.getRandom().nextDouble() * total;
        int chosen = pool.get(0);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0.0) {
                chosen = pool.get(i);
                break;
            }
        }
        beginAttack(chosen, target, distSq, phase);
    }

    private static void add(List<Integer> pool, List<Double> weights, int id, double weight) {
        pool.add(id);
        weights.add(weight);
    }

    private void beginAttack(int id, LivingEntity target, double distSq, int phase) {
        switch (id) {
            case LEAP_WINDUP -> startLeap();
            case CHARGE_WINDUP -> {
                this.state = CHARGE_WINDUP;
                this.stateTime = phase >= 3 ? 20 : 26;
                this.chargeCd = phase >= 3 ? 340 : 280;
                this.giant.getNavigation().stop();
                this.giant.setFlag(GearGiantEntity.FLAG_CHARGE, true);
                pose(GearGiantAnim.CHARGE_WINDUP);
                this.giant.playSound(SoundEvents.PISTON_CONTRACT, 1.5F, 0.62F);
            }
            case SPIN -> startSpin();
            case BARRAGE -> {
                this.state = BARRAGE;
                this.barrageWaves = phase >= 3 ? 5 : (phase >= 2 ? 4 : 3);
                this.stateTime = 24 + this.barrageWaves * 12;
                this.barrageTick = 0;
                this.barrageCd = phase >= 3 ? 110 : 170;
                this.giant.getNavigation().stop();
                pose(GearGiantAnim.BARRAGE);
            }
            case STEAM_WARN -> {
                this.state = STEAM_WARN;
                this.stateTime = 24;
                this.steamCd = phase >= 3 ? 160 : 200;
                this.giant.getNavigation().stop();
                pose(GearGiantAnim.STEAM);
                this.giant.playSound(SoundEvents.COPPER_BULB_TURN_ON, 1.2F, 0.7F);
                this.giant.playSound(SoundEvents.SMOKER_SMOKE, 1.0F, 1.25F);
            }
            case BEAM_WARM -> startBeamWarm(target);
            case STORM -> startStorm(target);
            case STOMP -> startStomp();
            case PULL -> startPull();
            case HOOK_WINDUP -> startHook();
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 状态机主体

    private void runState(Level level, LivingEntity target) {
        switch (this.state) {
            case MELEE_WINDUP -> {
                if (validTarget(target)) {
                    this.giant.getLookControl().setLookAt(target, 40.0F, 30.0F);
                }
                this.stateTime--;
                if (this.stateTime <= 0) {
                    this.state = MELEE_STRIKE;
                    this.stateTime = 10;
                    this.strikeDone = false;
                    pose(GearGiantAnim.MELEE_STRIKE);
                }
            }
            case MELEE_STRIKE -> {
                this.stateTime--;
                if (!this.strikeDone && this.stateTime <= 5) {
                    this.strikeDone = true;
                    this.giant.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.3F, 0.7F);
                    if (validTarget(target)) {
                        // 挥落时向前迈一小步，追一下试图后撤的玩家
                        Vec3 lunge = target.position().subtract(this.giant.position());
                        double len = Math.max(1.0E-4, lunge.length());
                        this.giant.setDeltaMovement(lunge.x / len * 0.22,
                                this.giant.getDeltaMovement().y, lunge.z / len * 0.22);
                        this.giant.hasImpulse = true;
                        if (this.giant.doHurtTarget(target) && level instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(ParticleTypes.CLOUD,
                                    target.getX(), target.getY() + 0.4, target.getZ(), 12, 0.5, 0.3, 0.5, 0.05);
                            slamShockwave(serverLevel, target);
                        }
                    }
                }
                if (this.stateTime <= 0) {
                    // 目标没走开就追一记横扫，形成两段连招
                    if (validTarget(target) && this.giant.distanceToSqr(target) <= 42.0) {
                        this.state = MELEE_SWEEP;
                        this.stateTime = 12;
                        this.strikeDone = false;
                        pose(GearGiantAnim.MELEE_SWEEP);
                        this.giant.playSound(SoundEvents.PISTON_CONTRACT, 1.0F, 1.1F);
                    } else {
                        endAttack(8);
                    }
                }
            }
            case MELEE_SWEEP -> {
                this.stateTime--;
                if (!this.strikeDone && this.stateTime <= 6) {
                    this.strikeDone = true;
                    this.giant.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.6F);
                    this.giant.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.2F, 0.85F);
                    if (level instanceof ServerLevel serverLevel) {
                        sweepStrike(serverLevel, target);
                    }
                }
                if (this.stateTime <= 0) {
                    endAttack(10);
                }
            }
            case CHARGE_WINDUP -> {
                if (validTarget(target)) {
                    this.giant.getLookControl().setLookAt(target, 40.0F, 30.0F);
                }
                // 前摇预告：正前方地面划出一条"冲刺弹道"，给玩家走位窗口
                if (level instanceof ServerLevel serverLevel && this.stateTime % 2 == 0) {
                    float yawRad = this.giant.getYHeadRot() * Mth.DEG_TO_RAD;
                    double fx = -Mth.sin(yawRad);
                    double fz = Mth.cos(yawRad);
                    double t = 1.0 - this.stateTime / 26.0;
                    for (int i = 0; i < 3; i++) {
                        double d = 2.0 + (t * 9.0) + i * 1.3;
                        Vec3 p = this.giant.position().add(fx * d, 0.1, fz * d);
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                p.x, p.y, p.z, 2, 0.15, 0.05, 0.15, 0.12);
                    }
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                            this.giant.getX() + fx * 1.8, this.giant.getY() + 1.5,
                            this.giant.getZ() + fz * 1.8, 5, 0.4, 0.7, 0.4, 0.3);
                }
                this.stateTime--;
                if (this.stateTime <= 0) {
                    if (validTarget(target)) {
                        Vec3 dir = target.position().subtract(this.giant.position());
                        double len = Math.max(1.0E-4, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
                        this.rushX = dir.x / len * 0.62;
                        this.rushZ = dir.z / len * 0.62;
                        this.state = CHARGE_RUSH;
                        this.stateTime = 50;
                        pose(GearGiantAnim.CHARGE_RUSH);
                        this.giant.playSound(SoundEvents.PISTON_EXTEND, 1.6F, 0.75F);
                    } else {
                        endCharge();
                    }
                }
            }
            case CHARGE_RUSH -> {
                this.giant.setDeltaMovement(this.rushX, 0.06, this.rushZ);
                this.stateTime--;
                if (level instanceof ServerLevel serverLevel && this.stateTime % 2 == 0) {
                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            this.giant.getX(), this.giant.getY() + 0.2, this.giant.getZ(),
                            4, 1.0, 0.3, 1.0, 0.02);
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            this.giant.getX() - this.rushX * 6.0, this.giant.getY() + 0.4,
                            this.giant.getZ() - this.rushZ * 6.0, 3, 0.5, 0.3, 0.5, 0.03);
                }
                if (level instanceof ServerLevel serverLevel && hitDuringCharge(serverLevel)) {
                    chargeImpact(serverLevel);
                    return;
                }
                if (this.giant.horizontalCollision || this.stateTime <= 0) {
                    if (level instanceof ServerLevel serverLevel) {
                        chargeImpact(serverLevel);
                    } else {
                        endCharge();
                    }
                }
            }
            case LEAP_WINDUP -> {
                if (validTarget(target)) {
                    this.giant.getLookControl().setLookAt(target, 30.0F, 0.0F);
                }
                if (level instanceof ServerLevel serverLevel && this.stateTime % 3 == 0) {
                    // 蓄力：脚下尘土被震得环状浮起
                    double a = serverLevel.getRandom().nextDouble() * Math.PI * 2.0;
                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            this.giant.getX() + Math.cos(a) * 2.6, this.giant.getY() + 0.1,
                            this.giant.getZ() + Math.sin(a) * 2.6, 2, 0.15, 0.05, 0.15, 0.01);
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            this.giant.getX(), this.giant.getY() + 0.3, this.giant.getZ(),
                            3, 1.6, 0.2, 1.6, 0.2);
                }
                this.stateTime--;
                if (this.stateTime <= 0) {
                    launch(target, level);
                }
            }
            case LEAP_AIR -> {
                this.airTicks++;
                this.giant.setDeltaMovement(this.rushX, this.giant.getDeltaMovement().y, this.rushZ);
                this.giant.hasImpulse = true;
                this.giant.fallDistance = 0.0F;
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            this.giant.getX(), this.giant.getY() + 0.1, this.giant.getZ(),
                            2, 0.8, 0.2, 0.8, 0.01);
                }
                boolean falling = this.giant.getDeltaMovement().y < 0.02;
                boolean grounded = this.giant.onGround() && falling && this.airTicks > 6;
                if (grounded || this.airTicks > 90 || (this.giant.horizontalCollision && falling && this.airTicks > 8)) {
                    leapImpact(level);
                }
            }
            case LEAP_LAND -> {
                this.stateTime--;
                if (this.stateTime <= 0) {
                    endAttack(18);
                }
            }
            case SPIN -> {
                this.stateTime--;
                if (validTarget(target)) {
                    this.giant.getLookControl().setLookAt(target, 12.0F, 0.0F);
                    // 旋转中缓慢向目标压进
                    Vec3 to = target.position().subtract(this.giant.position());
                    to = new Vec3(to.x, 0.0, to.z);
                    double len = Math.max(1.0E-4, to.length());
                    Vec3 v = this.giant.getDeltaMovement();
                    this.giant.setDeltaMovement(to.x / len * 0.055, v.y, to.z / len * 0.055);
                    this.giant.hasImpulse = true;
                }
                if (level instanceof ServerLevel serverLevel) {
                    int elapsed = SPIN_TICKS - this.stateTime;
                    double ang = elapsed * 0.42;
                    for (int k = 0; k < 2; k++) {
                        double a = ang + k * Math.PI;
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                this.giant.getX() + Math.cos(a) * 3.5,
                                this.giant.getY() + 0.7 + k * 1.3,
                                this.giant.getZ() + Math.sin(a) * 3.5, 2, 0.12, 0.12, 0.12, 0.22);
                    }
                    if (this.stateTime % 9 == 0) {
                        spinHitWave(serverLevel);
                    }
                    if (elapsed % 16 == 0) {
                        serverLevel.playSound(null, this.giant.blockPosition(),
                                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.5F);
                    }
                }
                if (this.stateTime <= 0) {
                    endAttack(14);
                }
            }
            case BARRAGE -> {
                if (validTarget(target)) {
                    this.giant.getLookControl().setLookAt(target, 20.0F, 20.0F);
                }
                this.stateTime--;
                this.barrageTick--;
                if (this.barrageTick <= 0 && this.barrageWaves > 0 && validTarget(target)) {
                    fireGearVolley(target);
                    this.barrageWaves--;
                    this.barrageTick = this.giant.getGiantPhase() >= 3 ? 9 : 12;
                }
                if (this.stateTime <= 0) {
                    endAttack(16);
                }
            }
            case STORM -> {
                if (validTarget(target)) {
                    this.giant.getLookControl().setLookAt(target, 10.0F, 30.0F);
                }
                this.stateTime--;
                if (level instanceof ServerLevel serverLevel && this.stateTime % 4 == 0) {
                    // 双臂高举，齿轮在头顶盘旋聚拢
                    double a = serverLevel.getRandom().nextDouble() * Math.PI * 2.0;
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            this.giant.getX() + Math.cos(a) * 2.0,
                            this.giant.getY() + 3.2 + serverLevel.getRandom().nextDouble(),
                            this.giant.getZ() + Math.sin(a) * 2.0, 2, 0.2, 0.3, 0.2, 0.2);
                }
                if (this.stateTime <= 0) {
                    endAttack(14);
                }
            }
            case STEAM_WARN -> {
                this.stateTime--;
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            this.giant.getX() + (serverLevel.getRandom().nextDouble() - 0.5) * 4.0,
                            this.giant.getY() + 0.4,
                            this.giant.getZ() + (serverLevel.getRandom().nextDouble() - 0.5) * 4.0,
                            3, 0.2, 0.2, 0.2, 0.08);
                }
                if (this.stateTime <= 0) {
                    steamBurst(level);
                    endAttack(22);
                }
            }
            case BEAM_WARM -> {
                if (!validTarget(this.beamVictim)) {
                    cancelBeam();
                    return;
                }
                Vec3 warmEye = this.beamVictim.getEyePosition();
                this.giant.getLookControl().setLookAt(warmEye.x, warmEye.y, warmEye.z);
                this.stateTime--;
                if (level instanceof ServerLevel serverLevel && this.stateTime % 2 == 0) {
                    beamParticles(serverLevel);
                }
                if (this.stateTime <= 0) {
                    this.giant.setFlag(GearGiantEntity.FLAG_BEAM_WARM, false);
                    this.giant.setFlag(GearGiantEntity.FLAG_BEAM_FIRE, true);
                    this.state = BEAM_FIRE;
                    this.stateTime = 50;
                    this.beamHitTick = 0;
                    pose(GearGiantAnim.BEAM_FIRE);
                    this.giant.playSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.5F);
                }
            }
            case BEAM_FIRE -> {
                this.stateTime--;
                this.beamHitTick++;
                if (!validTarget(this.beamVictim)) {
                    cancelBeam();
                    return;
                }
                Vec3 fireEye = this.beamVictim.getEyePosition();
                this.giant.getLookControl().setLookAt(fireEye.x, fireEye.y, fireEye.z);
                if (level instanceof ServerLevel serverLevel) {
                    beamParticles(serverLevel);
                    if (this.stateTime % 10 == 0) {
                        this.giant.playSound(SoundEvents.FIRE_AMBIENT, 1.0F, 0.7F);
                    }
                    if (this.beamHitTick % 5 == 0) {
                        beamDamage(serverLevel);
                    }
                }
                if (this.stateTime <= 0) {
                    endBeam();
                    this.attackLock = 40;
                }
            }
            case PULL -> {
                this.stateTime--;
                if (level instanceof ServerLevel serverLevel) {
                    pullTick(serverLevel);
                }
                if (validTarget(target)) {
                    this.giant.getLookControl().setLookAt(target, 10.0F, 10.0F);
                }
                if (this.stateTime <= 0) {
                    this.giant.setFlag(GearGiantEntity.FLAG_PULL, false);
                    this.state = NONE;
                    this.attackLock = 12;
                    pose(GearGiantAnim.NONE);
                    if (level instanceof ServerLevel serverLevel) {
                        pullRelease(serverLevel);
                    }
                }
            }
            case STOMP -> {
                if (validTarget(target)) {
                    this.giant.getLookControl().setLookAt(target, 20.0F, 10.0F);
                }
                this.stateTime--;
                if (this.stateTime == 12 && level instanceof ServerLevel serverLevel) {
                    stompImpact(serverLevel, target);
                }
                if (this.stateTime <= 0) {
                    endAttack(10);
                }
            }
            case HOOK_WINDUP -> {
                if (!validTarget(this.hookVictim)) {
                    // 锁定的玩家中途暴毙：换最近的可钩玩家，换不到就收招
                    this.hookVictim = pickHookVictim(target);
                    if (this.hookVictim == null) {
                        endAttack(10);
                        break;
                    }
                }
                this.giant.getLookControl().setLookAt(this.hookVictim, 50.0F, 30.0F);
                // 蓄力：右肩爪仓开合的火花预告 + 一条指向猎物的"弹道"火花
                if (level instanceof ServerLevel serverLevel && this.stateTime % 2 == 0) {
                    Vec3 hand = this.giant.position().add(0.0, 2.55, 0.0)
                            .add(this.giant.getForward().scale(1.3));
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            hand.x, hand.y, hand.z, 3, 0.22, 0.22, 0.22, 0.3);
                    if (this.stateTime % 6 == 0) {
                        serverLevel.sendParticles(ParticleTypes.CRIT,
                                hand.x, hand.y, hand.z, 4, 0.3, 0.3, 0.3, 0.25);
                    }
                    Vec3 lock = this.hookVictim.getEyePosition().subtract(hand).normalize();
                    for (int i = 1; i <= 3; i++) {
                        Vec3 p = hand.add(lock.scale(i * (2.0 + (16 - this.stateTime) * 0.55)));
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.12);
                    }
                }
                this.stateTime--;
                if (this.stateTime <= 0) {
                    throwHook(level);
                }
            }
            case HOOK_THROW -> {
                if (validTarget(this.hookVictim)) {
                    this.giant.getLookControl().setLookAt(this.hookVictim, 40.0F, 20.0F);
                } else if (validTarget(target)) {
                    this.giant.getLookControl().setLookAt(target, 40.0F, 20.0F);
                }
                this.stateTime--;
                if (this.stateTime <= 0) {
                    endAttack(6);
                }
            }
            default -> endAttack(8);
        }
    }

    /** 招式收尾：回到待机并留下短暂硬直；钩爪牵着人时保持收链姿势。 */
    private void endAttack(int lock) {
        this.state = NONE;
        this.attackLock = lock;
        pose(this.giant.isHookReeling() ? GearGiantAnim.HOOK_PULL : GearGiantAnim.NONE);
    }

    private void pose(int animId) {
        this.giant.setAnimPose(animId);
    }

    /** 统一技能伤害折算（跟随 skillDamageScale 配置）。 */
    private float skill(float base) {
        return this.giant.scaledSkillDamage(base);
    }

    // ---------------------------------------------------------------- 近身砸击

    private void startMelee() {
        int p = this.giant.getGiantPhase();
        this.state = MELEE_WINDUP;
        this.stateTime = 18;
        this.slamCd = p >= 3 ? 46 : (p >= 2 ? 56 : 68);
        pose(GearGiantAnim.MELEE_WINDUP);
        this.giant.getNavigation().stop();
        this.giant.playSound(SoundEvents.PISTON_CONTRACT, 1.1F, 0.85F);
    }

    /** 横扫第二段：转身大范围背抽，把绕背的玩家一起扫飞。 */
    private void sweepStrike(ServerLevel level, LivingEntity focus) {
        Vec3 fwd = this.giant.getForward();
        fwd = new Vec3(fwd.x, 0.0, fwd.z).normalize();
        this.giant.setDeltaMovement(fwd.x * 0.18, this.giant.getDeltaMovement().y, fwd.z * 0.18);
        this.giant.hasImpulse = true;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                this.giant.getBoundingBox().inflate(2.6, 1.6, 2.6),
                v -> v != this.giant && v.isAlive() && harmable(v))) {
            victim.hurt(this.giant.damageSources().mobAttack(this.giant),
                    (float) (this.giant.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7));
            Vec3 away = victim.position().subtract(this.giant.position());
            double len = Math.max(1.0E-4, away.length());
            victim.setDeltaMovement(victim.getDeltaMovement().add(
                    away.x / len * 0.6, 0.42, away.z / len * 0.6));
            victim.hasImpulse = true;
            level.sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + 0.9, victim.getZ(), 8, 0.4, 0.5, 0.4, 0.3);
        }
        for (int i = 0; i < 12; i++) {
            double a = i / 12.0 * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.giant.getX() + Math.cos(a) * 2.6, this.giant.getY() + 0.9,
                    this.giant.getZ() + Math.sin(a) * 2.6, 1, 0.08, 0.12, 0.08, 0.04);
        }
    }

    // ---------------------------------------------------------------- 机械钩爪

    /**
     * 选定钩爪锁定对象：优先当前目标（若为活玩家且距离在配置区间附近），
     * 否则取区间内离巨人最近的生存玩家。找不到任何人时返回 null（不出手）。
     */
    private Player pickHookVictim(LivingEntity target) {
        if (!(this.giant.level() instanceof ServerLevel level)
                || !cn.blockforge.generated.geargiant1211ngear.config.ModConfigs.GIANT_HOOK_ENABLED.get()) {
            return null;
        }
        double min = cn.blockforge.generated.geargiant1211ngear.config.ModConfigs.GIANT_HOOK_MIN_DISTANCE.get();
        double max = cn.blockforge.generated.geargiant1211ngear.config.ModConfigs.GIANT_HOOK_RANGE.get();
        if (target instanceof Player tp && hookCandidate(tp, min, max)) {
            return tp;
        }
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : level.getEntitiesOfClass(Player.class,
                this.giant.getBoundingBox().inflate(max + 8.0))) {
            if (!hookCandidate(p, min, max)) {
                continue;
            }
            double d = this.giant.distanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private boolean hookCandidate(Player p, double min, double max) {
        if (!p.isAlive() || p.isRemoved() || p.isSpectator()) {
            return false;
        }
        double d = this.giant.distanceTo(p);
        return d >= min * 0.7 && d <= max + 6.0;
    }

    private void startHook() {
        Player victim = this.hookVictim;
        if (victim == null || !victim.isAlive() || victim.isRemoved() || victim.isSpectator()) {
            this.hookVictim = null;
            this.state = NONE;
            this.attackLock = 10;
            this.hookCd = 60;
            return;
        }
        this.state = HOOK_WINDUP;
        this.stateTime = 16;
        this.giant.getNavigation().stop();
        pose(GearGiantAnim.HOOK_WINDUP);
        this.giant.playSound(SoundEvents.CHAIN_PLACE, 1.3F, 0.75F);
        this.giant.playSound(SoundEvents.PISTON_CONTRACT, 1.1F, 1.2F);
    }

    /** 掷爪：爪头带着锁定玩家的身份飞出，飞行中每刻追踪、可穿方块，必定命中。 */
    private void throwHook(Level level) {
        this.state = HOOK_THROW;
        this.stateTime = 18;
        int p = this.giant.getGiantPhase();
        int cd = cn.blockforge.generated.geargiant1211ngear.config.ModConfigs.GIANT_HOOK_COOLDOWN.get();
        this.hookCd = p >= 3 ? (int) (cd * 0.75) : cd;
        pose(GearGiantAnim.HOOK_THROW);
        this.giant.playSound(SoundEvents.TRIDENT_THROW.value(), 1.5F, 0.55F);
        this.giant.playSound(SoundEvents.CHAIN_FALL, 1.0F, 1.5F);
        Player victim = this.hookVictim;
        if (victim == null || !victim.isAlive() || victim.isRemoved() || victim.isSpectator()) {
            // 蓄力期间锁定对象阵亡：临场换人，保证这一爪必有人被勾
            victim = pickHookVictim(this.giant.getTarget());
        }
        this.hookVictim = victim;
        if (level instanceof ServerLevel serverLevel && victim != null) {
            this.activeHook = cn.blockforge.generated.geargiant1211ngear.entity.HookClawEntity
                    .fire(serverLevel, this.giant, victim);
            // 甩臂的后坐：整体向前压半步
            Vec3 fwd = this.giant.getForward();
            this.giant.setDeltaMovement(fwd.x * 0.12, this.giant.getDeltaMovement().y, fwd.z * 0.12);
            this.giant.hasImpulse = true;
        }
    }

    /** 砸击震波：以目标为圆心的碎屑环，波及旁边的其他玩家。 */
    private void slamShockwave(ServerLevel level, LivingEntity target) {
        for (int i = 0; i < 14; i++) {
            double a = i / 14.0 * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    target.getX() + Math.cos(a) * 1.8, target.getY() + 0.15,
                    target.getZ() + Math.sin(a) * 1.8, 1, 0.05, 0.08, 0.05, 0.03);
        }
        level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_HIT,
                SoundSource.HOSTILE, 0.9F, 0.55F);
        for (Player player : level.getEntitiesOfClass(Player.class,
                target.getBoundingBox().inflate(2.4, 1.0, 2.4),
                p -> p != target && p.isAlive() && !p.isSpectator())) {
            player.hurt(this.giant.damageSources().mobAttack(this.giant), skill(2.5F));
            Vec3 away = player.position().subtract(target.position());
            double len = Math.max(1.0E-4, Math.sqrt(away.x * away.x + away.z * away.z));
            player.setDeltaMovement(player.getDeltaMovement().add(
                    away.x / len * 0.35, 0.28, away.z / len * 0.35));
            player.hasImpulse = true;
        }
    }

    // ---------------------------------------------------------------- 旋转冲撞

    private void endCharge() {
        endAttack(30);
        this.giant.setFlag(GearGiantEntity.FLAG_CHARGE, false);
        this.giant.playSound(SoundEvents.ANVIL_LAND, 1.6F, 0.8F);
    }

    /** 冲撞收尾：撞停点炸开一圈冲击波，把小范围内的目标掀翻弹开。 */
    private void chargeImpact(ServerLevel level) {
        this.giant.setFlag(GearGiantEntity.FLAG_CHARGE, false);
        endAttack(30);
        double cx = this.giant.getX() + this.rushX * 5.0;
        double cz = this.giant.getZ() + this.rushZ * 5.0;
        double cy = this.giant.getY();
        level.sendParticles(ParticleTypes.EXPLOSION,
                cx, cy + 1.2, cz, 4, 1.0, 0.8, 1.0, 0.0);
        for (int i = 0; i < 26; i++) {
            double a = i / 26.0 * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.CLOUD,
                    cx + Math.cos(a) * 2.4, cy + 0.35, cz + Math.sin(a) * 2.4,
                    1, 0.06, 0.12, 0.06, 0.06);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    cx + Math.cos(a) * 2.0, cy + 0.25, cz + Math.sin(a) * 2.0,
                    1, 0.1, 0.2, 0.1, 0.35);
        }
        level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 1.1F, 0.55F);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - 3.4, cy - 1.0, cz - 3.4, cx + 3.4, cy + 2.6, cz + 3.4),
                v -> v != this.giant && v.isAlive() && harmable(v))) {
            victim.hurt(this.giant.damageSources().mobAttack(this.giant), skill(3.5F));
            Vec3 away = victim.position().subtract(cx, victim.getY(), cz);
            double len = Math.max(1.0E-4, Math.sqrt(away.x * away.x + away.z * away.z));
            victim.setDeltaMovement(victim.getDeltaMovement().add(
                    away.x / len * 0.7, 0.5, away.z / len * 0.7));
            victim.hasImpulse = true;
        }
    }

    private boolean hitDuringCharge(ServerLevel level) {
        double damage = this.giant.getAttributeValue(Attributes.ATTACK_DAMAGE);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                this.giant.getBoundingBox().inflate(0.8),
                v -> v != this.giant && v.isAlive() && harmable(v));
        boolean hitAny = false;
        for (LivingEntity victim : victims) {
            victim.hurt(this.giant.damageSources().mobAttack(this.giant), (float) damage);
            Vec3 away = victim.position().subtract(this.giant.position());
            double len = Math.max(1.0E-4, away.length());
            victim.setDeltaMovement(victim.getDeltaMovement().add(away.x / len * 0.8, 0.4, away.z / len * 0.8));
            hitAny = true;
        }
        return hitAny;
    }

    // ---------------------------------------------------------------- 跳跃砸击

    private void startLeap() {
        int p = this.giant.getGiantPhase();
        this.state = LEAP_WINDUP;
        this.stateTime = LEAP_CROUCH_TICKS;
        this.leapCd = p >= 3 ? 190 : 230;
        this.giant.getNavigation().stop();
        pose(GearGiantAnim.LEAP_CROUCH);
        this.giant.playSound(SoundEvents.PISTON_EXTEND, 1.3F, 0.5F);
    }

    private void launch(LivingEntity target, Level level) {
        if (validTarget(target)) {
            Vec3 dir = target.position().subtract(this.giant.position());
            dir = new Vec3(dir.x, 0.0, dir.z);
            double len = Math.max(1.0E-4, dir.length());
            double power = Mth.clamp(len / 26.0, 0.30, 0.55);
            this.rushX = dir.x / len * power;
            this.rushZ = dir.z / len * power;
        } else {
            // 目标丢了就原地起落，声势照旧
            this.rushX = 0.0;
            this.rushZ = 0.0;
        }
        this.state = LEAP_AIR;
        this.airTicks = 0;
        pose(GearGiantAnim.LEAP_AIR);
        this.giant.setDeltaMovement(this.rushX, 0.44, this.rushZ);
        this.giant.hasImpulse = true;
        this.giant.fallDistance = 0.0F;
        this.giant.playSound(SoundEvents.BREEZE_JUMP, 1.6F, 0.45F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.giant.getX(), this.giant.getY() + 0.1, this.giant.getZ(),
                    20, 1.4, 0.2, 1.4, 0.05);
        }
    }

    private void leapImpact(Level level) {
        this.state = LEAP_LAND;
        this.stateTime = 18;
        pose(GearGiantAnim.LEAP_LAND);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double cx = this.giant.getX();
        double cy = this.giant.getY();
        double cz = this.giant.getZ();
        serverLevel.playSound(null, this.giant.blockPosition(),
                SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.8F, 0.6F);
        serverLevel.playSound(null, this.giant.blockPosition(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.5F);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, cx, cy + 0.8, cz, 6, 1.6, 0.6, 1.6, 0.0);
        for (int i = 0; i < 34; i++) {
            double a = i / 34.0 * Math.PI * 2.0;
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    cx + Math.cos(a) * 3.2, cy + 0.25, cz + Math.sin(a) * 3.2,
                    1, 0.12, 0.2, 0.12, 0.08);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    cx + Math.cos(a) * 2.6, cy + 0.2, cz + Math.sin(a) * 2.6,
                    1, 0.15, 0.35, 0.15, 0.4);
        }
        // 落地冲击：大半径伤害 + 强击退
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - 4.8, cy - 1.2, cz - 4.8, cx + 4.8, cy + 3.0, cz + 4.8),
                v -> v != this.giant && v.isAlive() && harmable(v))) {
            victim.hurt(this.giant.damageSources().mobAttack(this.giant), skill(7.0F));
            Vec3 away = victim.position().subtract(cx, victim.getY(), cz);
            double len = Math.max(1.0E-4, Math.sqrt(away.x * away.x + away.z * away.z));
            victim.setDeltaMovement(victim.getDeltaMovement().add(
                    away.x / len * 0.9, 0.62, away.z / len * 0.9));
            victim.hasImpulse = true;
        }
        // 震出的三处地裂补上二次伤害
        for (int i = 0; i < 3; i++) {
            double a = serverLevel.getRandom().nextDouble() * Math.PI * 2.0;
            double d = 3.0 + serverLevel.getRandom().nextDouble() * 2.5;
            Vec3 p = new Vec3(cx + Math.cos(a) * d, cy, cz + Math.sin(a) * d);
            this.giant.queueEruption(new Vec3(p.x, findGround(serverLevel, p), p.z), 10 + i * 8);
        }
    }

    // ---------------------------------------------------------------- 旋风横扫

    private void startSpin() {
        int p = this.giant.getGiantPhase();
        this.state = SPIN;
        this.stateTime = SPIN_TICKS;
        this.spinCd = p >= 3 ? 250 : 320;
        this.giant.getNavigation().stop();
        pose(GearGiantAnim.SPIN);
        this.giant.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.5F);
        this.giant.playSound(SoundEvents.BREEZE_WHIRL, 1.4F, 0.6F);
    }

    private void spinHitWave(ServerLevel level) {
        AABB area = this.giant.getBoundingBox().inflate(3.6, 2.0, 3.6);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
                v -> v != this.giant && v.isAlive() && harmable(v))) {
            victim.hurt(this.giant.damageSources().mobAttack(this.giant), skill(4.5F));
            Vec3 away = victim.position().subtract(this.giant.position());
            double len = Math.max(1.0E-4, away.length());
            victim.setDeltaMovement(victim.getDeltaMovement().add(
                    away.x / len * 0.5, 0.28, away.z / len * 0.5));
            victim.hasImpulse = true;
            level.sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + 0.8, victim.getZ(), 6, 0.35, 0.5, 0.35, 0.25);
        }
        level.playSound(null, this.giant.blockPosition(), SoundEvents.BREEZE_WHIRL,
                SoundSource.HOSTILE, 0.9F, 0.75F);
    }

    // ---------------------------------------------------------------- 齿轮弹幕

    private void fireGearVolley(LivingEntity target) {
        Level level = this.giant.level();
        if (level.isClientSide) {
            return;
        }
        Vec3 origin = this.giant.getEyePosition().subtract(0.0, 0.2, 0.0);
        Vec3 to = target.getEyePosition().subtract(origin);
        double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        float baseYaw = (float) Mth.atan2(to.z, to.x);
        float basePitch = (float) -Mth.atan2(to.y, Math.max(1.0E-4, horiz));
        for (int i = -2; i <= 2; i++) {
            float yaw = baseYaw + i * 0.13F;
            float pitch = basePitch + this.giant.getRandom().nextFloat() * 0.04F - 0.02F;
            GearProjectileEntity gear = new GearProjectileEntity(ModEntities.GEAR_PROJECTILE.get(), this.giant, level);
            gear.setPos(origin.x, origin.y, origin.z);
            gear.setGearDamage(skill(5.0F));
            gear.shoot(
                    -Mth.sin(yaw) * Mth.cos(pitch),
                    -Mth.sin(pitch),
                    Mth.cos(yaw) * Mth.cos(pitch),
                    1.5F, 2.0F);
            level.addFreshEntity(gear);
        }
        // 三阶段追加两发高抛物线"迫击齿轮"，覆盖掩体后方
        if (this.giant.getGiantPhase() >= 3) {
            for (int i = -1; i <= 1; i += 2) {
                float yaw = baseYaw + i * 0.22F;
                GearProjectileEntity lob = new GearProjectileEntity(
                        ModEntities.GEAR_PROJECTILE.get(), this.giant, level);
                lob.setPos(origin.x, origin.y, origin.z);
                lob.setGearDamage(skill(6.0F));
                lob.shoot(-Mth.sin(yaw) * 0.82F, 0.58F, Mth.cos(yaw) * 0.82F, 1.35F, 3.0F);
                level.addFreshEntity(lob);
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    origin.x, origin.y, origin.z, 10, 0.28, 0.28, 0.28, 0.32);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    origin.x, origin.y, origin.z, 4, 0.2, 0.2, 0.2, 0.02);
        }
        this.giant.playSound(SoundEvents.FIRECHARGE_USE, 0.9F, 1.15F);
    }

    // ---------------------------------------------------------------- 齿轮暴雨（三阶段）

    private void startStorm(LivingEntity target) {
        if (!(this.giant.level() instanceof ServerLevel level)) {
            return;
        }
        this.state = STORM;
        this.stateTime = 40;
        this.stormCd = 420;
        this.giant.getNavigation().stop();
        pose(GearGiantAnim.BARRAGE);
        this.giant.playSound(SoundEvents.SHULKER_SHOOT, 1.4F, 0.55F);
        this.giant.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2F, 0.5F);
        // 目标周围一圈 7 处地裂，延迟错开像雨点落下
        Vec3 tp = target.position();
        int n = 7;
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2.0 + level.getRandom().nextDouble() * 0.5;
            double d = 2.2 + level.getRandom().nextDouble() * 3.2;
            Vec3 p = new Vec3(tp.x + Math.cos(a) * d, tp.y, tp.z + Math.sin(a) * d);
            this.giant.queueEruption(new Vec3(p.x, findGround(level, p), p.z), 16 + i * 8);
        }
    }

    // ---------------------------------------------------------------- 踩踏地裂（三阶段）

    private void startStomp() {
        this.state = STOMP;
        this.stateTime = 26;
        this.eruptCd = 300;
        this.giant.getNavigation().stop();
        pose(GearGiantAnim.STOMP);
        this.giant.playSound(SoundEvents.PISTON_EXTEND, 1.4F, 0.55F);
    }

    private void stompImpact(ServerLevel level, LivingEntity target) {
        level.playSound(null, this.giant.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.HOSTILE, 1.5F, 0.65F);
        double cx = this.giant.getX();
        double cy = this.giant.getY();
        double cz = this.giant.getZ();
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.CLOUD,
                    cx + Math.cos(a) * 2.2, cy + 0.15, cz + Math.sin(a) * 2.2,
                    1, 0.1, 0.1, 0.1, 0.03);
        }
        // 踩碎正下方：贴近的玩家先吃一记跺脚
        for (Player player : level.getEntitiesOfClass(Player.class,
                new AABB(cx - 3.2, cy - 1.0, cz - 3.2, cx + 3.2, cy + 2.0, cz + 3.2),
                p -> p.isAlive() && !p.isSpectator())) {
            player.hurt(this.giant.damageSources().mobAttack(this.giant), skill(3.5F));
            player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.5, 0.0));
            player.hasImpulse = true;
        }
        // 目标脚下与通往目标的路线上共 4 处地裂
        Vec3 tp = target != null && validTarget(target)
                ? target.position() : this.giant.getForward().scale(6).add(this.giant.position());
        Vec3 bp = this.giant.position();
        for (int i = 0; i < 4; i++) {
            double t = 0.35 + i * 0.22 + level.getRandom().nextDouble() * 0.08;
            Vec3 p = bp.lerp(tp, Math.min(1.0, t));
            double ground = findGround(level, p);
            this.giant.queueEruption(new Vec3(p.x, ground, p.z), 34 + i * 12);
        }
    }

    // ---------------------------------------------------------------- 蒸汽喷发

    private void steamBurst(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.playSound(null, this.giant.blockPosition(), SoundEvents.SMOKER_SMOKE,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.9F);
        serverLevel.playSound(null, this.giant.blockPosition(), SoundEvents.PISTON_CONTRACT,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.7F);
        serverLevel.sendParticles(ParticleTypes.CLOUD, this.giant.getX(), this.giant.getY() + 1.2,
                this.giant.getZ(), 80, 3.4, 1.2, 3.4, 0.12);
        serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, this.giant.getX(), this.giant.getY() + 1.6,
                this.giant.getZ(), 40, 3.2, 1.4, 3.2, 0.06);
        for (Player player : serverLevel.getEntitiesOfClass(Player.class,
                this.giant.getBoundingBox().inflate(9.0), p -> p.isAlive() && !p.isSpectator())) {
            if (player.distanceToSqr(this.giant) <= 64.0) {
                player.hurt(this.giant.damageSources().mobAttack(this.giant), skill(4.0F));
                // 高温蒸汽遮蔽视野并烫慢脚步
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.4, 0.6, 0.4, 0.04);
                Vec3 away = player.position().subtract(this.giant.position()).normalize();
                player.setDeltaMovement(player.getDeltaMovement().add(away.x * 0.55, 0.35, away.z * 0.55));
                player.hasImpulse = true;
            }
        }
    }

    // ---------------------------------------------------------------- 过载激光

    private void startBeamWarm(LivingEntity target) {
        this.beamVictim = target;
        this.state = BEAM_WARM;
        this.stateTime = 30;
        this.beamCd = 360;
        this.giant.getNavigation().stop();
        this.giant.setFlag(GearGiantEntity.FLAG_BEAM_WARM, true);
        pose(GearGiantAnim.BEAM_WARM);
        this.giant.playSound(SoundEvents.BEACON_POWER_SELECT, 1.4F, 0.8F);
    }

    private void cancelBeam() {
        endBeam();
        this.attackLock = Math.max(this.attackLock, 12);
    }

    private void endBeam() {
        this.state = NONE;
        this.beamVictim = null;
        this.giant.setFlag(GearGiantEntity.FLAG_BEAM_WARM, false);
        this.giant.setFlag(GearGiantEntity.FLAG_BEAM_FIRE, false);
        pose(GearGiantAnim.NONE);
    }

    private Vec3 beamEnd() {
        Vec3 from = this.giant.getEyePosition();
        Vec3 to = this.beamVictim.getEyePosition();
        Vec3 unit = to.subtract(from).normalize();
        Vec3 targetFar = from.add(unit.scale(48.0));
        BlockHitResult hit = this.giant.level().clip(new ClipContext(from, targetFar,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.giant));
        return hit.getLocation();
    }

    private void beamParticles(ServerLevel level) {
        if (this.beamVictim == null) {
            return;
        }
        boolean firing = this.state == BEAM_FIRE;
        Vec3 from = this.giant.getEyePosition();
        Vec3 to = beamEnd();
        for (int i = 1; i <= 16; i++) {
            double t = i / 16.0;
            Vec3 p = from.lerp(to, t);
            double jitter = firing ? 0.16 : 0.08;
            net.minecraft.core.particles.ParticleOptions kind = !firing
                    ? ParticleTypes.ELECTRIC_SPARK
                    : ((i & 3) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME);
            level.sendParticles(kind, p.x, p.y, p.z, 1, jitter, jitter, jitter, 0.0);
        }
        if (firing) {
            // 光束终点灼烧爆点
            level.sendParticles(ParticleTypes.SMOKE, to.x, to.y, to.z, 3, 0.3, 0.3, 0.3, 0.03);
            level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 4, 0.35, 0.35, 0.35, 0.25);
        }
    }

    private void beamDamage(ServerLevel level) {
        if (this.beamVictim == null) {
            return;
        }
        Vec3 from = this.giant.getEyePosition();
        Vec3 to = beamEnd();
        for (Player player : level.getEntitiesOfClass(Player.class,
                this.giant.getBoundingBox().inflate(48.0), p -> p.isAlive() && !p.isSpectator())) {
            Vec3 feet = player.position().add(0.0, 1.0, 0.0);
            if (distanceToSegmentSq(from, to, feet) < 2.2 && player.tickCount % 5 < 3) {
                player.hurt(this.giant.damageSources().mobAttack(this.giant), skill(5.0F));
                player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.25, 0.0));
                player.hasImpulse = true;
                level.sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        4, 0.3, 0.5, 0.3, 0.02);
            }
        }
        for (Monster monster : level.getEntitiesOfClass(Monster.class,
                this.giant.getBoundingBox().inflate(30.0),
                m -> m != this.giant && !(m instanceof cn.blockforge.generated.geargiant1211ngear.entity.GearlingEntity)
                        && m.isAlive())) {
            Vec3 feet = monster.position().add(0.0, 1.0, 0.0);
            if (distanceToSegmentSq(from, to, feet) < 2.0) {
                monster.hurt(this.giant.damageSources().mobAttack(this.giant), skill(6.0F));
            }
        }
    }

    private double findGround(ServerLevel level, Vec3 around) {
        for (int y = (int) (around.y + 2); y >= around.y - 6; y--) {
            net.minecraft.core.BlockPos probe = net.minecraft.core.BlockPos.containing(around.x, y, around.z);
            if (!level.isEmptyBlock(probe)) {
                return probe.getY() + 1.0;
            }
        }
        return around.y;
    }

    // ---------------------------------------------------------------- 传送带拉扯

    private void startPull() {
        this.state = PULL;
        this.stateTime = 50;
        this.pullCd = 260;
        this.giant.getNavigation().stop();
        this.giant.setFlag(GearGiantEntity.FLAG_PULL, true);
        pose(GearGiantAnim.PULL);
        this.giant.playSound(SoundEvents.PISTON_EXTEND, 1.6F, 0.6F);
    }

    private void pullTick(ServerLevel level) {
        double radius = this.giant.getAttributeValue(Attributes.FOLLOW_RANGE);
        for (Player player : level.getEntitiesOfClass(Player.class,
                this.giant.getBoundingBox().inflate(radius), p -> p.isAlive() && !p.isSpectator())) {
            Vec3 dir = this.giant.position().subtract(player.position());
            dir = new Vec3(dir.x, 0.0, dir.z);
            double len = Math.max(1.0E-4, dir.length());
            if (len < 2.4) {
                continue;
            }
            Vec3 pull = new Vec3(dir.x / len * 0.10, 0.012, dir.z / len * 0.10);
            player.setDeltaMovement(player.getDeltaMovement().add(pull));
            player.hasImpulse = true;
            // "传送带"视觉：沿玩家与巨人连线滚过的末端光点
            if (this.stateTime % 3 == 0) {
                double t = (this.stateTime % 12) / 12.0;
                for (int i = 0; i < 2; i++) {
                    double tt = Math.min(1.0, t + i * 0.18);
                    Vec3 p = player.position().add(0.0, 0.35, 0.0).lerp(this.giant.position(), tt);
                    level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
        }
        if (level.getRandom().nextInt(4) == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    this.giant.getX(), this.giant.getY() + 1.2, this.giant.getZ(),
                    3, 3.2, 1.0, 3.2, 0.02);
        }
    }

    /** 拉扯收束：像传送带甩件一样把贴近的玩家猛地抛出去。 */
    private void pullRelease(ServerLevel level) {
        for (Player player : level.getEntitiesOfClass(Player.class,
                this.giant.getBoundingBox().inflate(7.0), p -> p.isAlive() && !p.isSpectator())) {
            if (player.distanceToSqr(this.giant) > 40.0) {
                continue;
            }
            player.hurt(this.giant.damageSources().mobAttack(this.giant), skill(2.0F));
            Vec3 away = player.position().subtract(this.giant.position());
            double len = Math.max(1.0E-4, away.length());
            player.setDeltaMovement(away.x / len * 0.95, 0.68, away.z / len * 0.95);
            player.hasImpulse = true;
        }
        for (int i = 0; i < 18; i++) {
            double a = i / 18.0 * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.POOF,
                    this.giant.getX() + Math.cos(a) * 2.8, this.giant.getY() + 0.5,
                    this.giant.getZ() + Math.sin(a) * 2.8, 1, 0.1, 0.15, 0.1, 0.05);
        }
        this.giant.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.1F, 0.7F);
    }

    // ---------------------------------------------------------------- 工具

    private static boolean validTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved();
    }

    /** 冲撞可以撞到的目标：玩家与其他敌对生物（跳过自己的齿轮仆从）。 */
    private static boolean harmable(LivingEntity victim) {
        if (victim instanceof cn.blockforge.generated.geargiant1211ngear.entity.GearlingEntity) {
            return false;
        }
        return victim instanceof Player || victim instanceof Monster;
    }

    private static double distanceToSegmentSq(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        Vec3 ap = p.subtract(a);
        double lenSq = ab.lengthSqr();
        if (lenSq < 1.0E-6) {
            return ap.lengthSqr();
        }
        double t = Mth.clamp(ap.dot(ab) / lenSq, 0.0, 1.0);
        Vec3 closest = a.add(ab.x * t, ab.y * t, ab.z * t);
        return p.distanceToSqr(closest);
    }
}
