package cn.blockforge.generated.geargiant1211ngear.entity;

import cn.blockforge.generated.geargiant1211ngear.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 机械化钩爪（玩家工具版）：与巨人 BOSS 的 {@link HookClawEntity} 同款外观（同模型同贴图），
 * 但服务对象反过来——由玩家射出、把"生物"拽到玩家身边。
 *
 * 两种出手：
 * 1) 锁定命中：长按右键锁定了 64 格内视线目标时，爪头追踪该生物（可穿方块，必定勾中），
 *    钉住后一路拽到玩家身前收链（接近自动减速，不会靠惯性冲过头）；
 * 2) 未锁定空放：爪头沿准星直线飞出，飞满射程或撞墙后原路收回，只是一段"没勾中"的动作表现。
 */
public class MechanicalHookClawEntity extends ThrowableProjectile {
    /** 锁定追踪飞行（必中）。 */
    private static final int STATE_FLY = 0;
    /** 已钉住目标，正在拖拽回玩家。 */
    private static final int STATE_LATCHED = 1;
    /** 空放：沿直线飞出后自动收回。 */
    private static final int STATE_MISS = 2;
    /** 收回途中：飞回玩家手中。 */
    private static final int STATE_RETURN = 3;
    /** 读档时无法恢复的"已作废"状态：下一 tick 直接回收。 */
    private static final int STATE_VOID = 5;

    private static final int STATE_MAX_HOMING = 70;
    /** 拖拽硬上限：超过这个刻数无论到没到身边都收链。 */
    private static final int MAX_REEL_TICKS = 60;
    /** 空放飞行上限：飞到这个距离或刻数就折返。 */
    private static final double MISS_RANGE = 26.0;
    private static final int MISS_MAX_TICKS = 40;
    /** 锁定贴脸距离：走到这个范围内直接钉住，不依赖射线命中。 */
    private static final double LATCH_DIST = 1.6;
    /** 目标逃到玩家多远处就放弃追链（比 64 格索敌上限略宽，容忍出手瞬间的移动）。 */
    private static final double GIVEUP_DIST = 72.0;
    /** 收回贴身距离：与手部锚点走近到该范围即刻收链消失（量的是"距手"，不是"距脚底"）。 */
    private static final double RETURN_SNAP_DIST = 2.0;
    /** 拖拽停止锚点：把受害者拽到玩家正前方这么多个格（跟准星朝向）就收链，绝不"穿过玩家"。 */
    private static final double REEL_STOP_AHEAD = 0.8;

    private int stateTicks;
    private int flyTicks;
    private double missOriginX;
    private double missOriginY;
    private double missOriginZ;
    /** 空放初速方向（单位向量），每刻按恒定速率重写，抵消空气阻力。 */
    private Vec3 missDir = Vec3.ZERO;
    private int victimId = -1;

    public MechanicalHookClawEntity(EntityType<? extends MechanicalHookClawEntity> type, Level level) {
        super(type, level);
    }

    public MechanicalHookClawEntity(EntityType<? extends MechanicalHookClawEntity> type, LivingEntity owner, Level level) {
        super(type, owner, level);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, STATE_FLY);
    }

    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_STATE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MechanicalHookClawEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    /** 是否已钩住目标正在拖拽（渲染器据此闭合爪口）。 */
    public boolean isLatched() {
        return this.entityData.get(DATA_STATE) == STATE_LATCHED;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0; // 弹道全部由本类每刻重写速度控制
    }

    private Player hookOwner() {
        return this.getOwner() instanceof Player player && !player.isRemoved() ? player : null;
    }

    private LivingEntity victimOrNull() {
        if (this.victimId < 0) {
            return null;
        }
        Entity entity = this.level().getEntity(this.victimId);
        if (entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved()) {
            return living;
        }
        return null;
    }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            if (this.entityData.get(DATA_STATE) != STATE_FLY && this.entityData.get(DATA_STATE) != STATE_MISS) {
                this.setDeltaMovement(Vec3.ZERO);
            }
            super.tick();
            return;
        }
        Player owner = hookOwner();
        if (owner == null) {
            this.discard();
            return;
        }
        int state = this.entityData.get(DATA_STATE);
        switch (state) {
            case STATE_FLY -> {
                this.flyTicks++;
                if (this.flyTicks > STATE_MAX_HOMING) {
                    beginReturn(); // 追踪超时：转入收回，不能原地空转（旧写法会永久卡在 FLY 状态）
                    return;
                }
                if (!steerTowardVictim(owner)) {
                    return; // 已转入收回/钉住，或本刻内被回收
                }
            }
            case STATE_MISS -> {
                this.flyTicks++;
                double dx = this.getX() - this.missOriginX;
                double dy = this.getY() - this.missOriginY;
                double dz = this.getZ() - this.missOriginZ;
                if (this.flyTicks > MISS_MAX_TICKS || dx * dx + dy * dy + dz * dz > MISS_RANGE * MISS_RANGE) {
                    beginReturn();
                    return;
                }
                // 空放就是沿出手方向的匀速直线：抵消阻力，飞满即折返
                this.setDeltaMovement(this.missDir.scale(1.75));
            }
            case STATE_LATCHED -> {
                this.setDeltaMovement(Vec3.ZERO);
                reelTick(owner);
                return; // reel 内部自行处理位置，不走下面的物理积分
            }
            case STATE_RETURN -> {
                this.stateTicks++;
                if (this.stateTicks > 40) {
                    retractFx(owner);
                    this.discard();
                    return;
                }
                Vec3 hand = owner.getEyePosition().subtract(0.0, 0.35, 0.0);
                double dist = this.position().distanceTo(hand);
                // 回到手心范围即刻收链：判定量"距手锚点"，此点可达，不会再绕着玩家转圈
                if (dist <= RETURN_SNAP_DIST) {
                    retractFx(owner);
                    this.discard();
                    return;
                }
                // 单刻位移不超过剩余距离：从源头消灭"每刻冲过头→绕圈"的另一半成因
                steerTo(hand, Math.min(2.6, dist));
            }
            default -> {
                this.discard();
                return;
            }
        }
        super.tick(); // 物理积分 + 沿运动向量的命中检测（onHit）
        if (this.isRemoved()) {
            return;
        }
        if (this.level() instanceof ServerLevel serverLevel && this.flyTicks % 2 == 0) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY(), this.getZ(), 1, 0.08, 0.08, 0.08, 0.2);
            if (this.flyTicks > 2 && this.entityData.get(DATA_STATE) != STATE_RETURN) {
                chainFx(serverLevel, owner, this.position());
            }
        }
    }

    // ---------------------------------------------------------------- 锁定追踪

    /** 每刻把速度重写为"指向锁定生物胸口"。返回 false 表示本刻内状态已切换。 */
    private boolean steerTowardVictim(Player owner) {
        LivingEntity victim = victimOrNull();
        if (victim == null || owner.distanceTo(victim) > GIVEUP_DIST) {
            beginReturn();
            return false;
        }
        Vec3 chest = victim.position().add(0.0, Math.max(0.4, victim.getBbHeight() * 0.55), 0.0);
        Vec3 to = chest.subtract(this.position());
        double dist = to.length();
        if (dist <= LATCH_DIST) {
            latch(owner, victim);
            return false;
        }
        double speed = Math.min(2.6, 1.5 + this.flyTicks * 0.07);
        this.setDeltaMovement(to.scale(speed / dist));
        return true;
    }

    /** 通用"朝某点飞"：空放折返与收回都用它。 */
    private void steerTo(Vec3 target, double speed) {
        Vec3 to = target.subtract(this.position());
        double dist = Math.max(1.0E-4, to.length());
        this.setDeltaMovement(to.scale(speed / dist));
    }

    // ---------------------------------------------------------------- 命中处理

    @Override
    protected void onHit(HitResult result) {
        if (this.level().isClientSide) {
            return;
        }
        int state = this.entityData.get(DATA_STATE);
        if (result.getType() == HitResult.Type.ENTITY) {
            if (state != STATE_FLY) {
                return;
            }
            Entity hit = ((EntityHitResult) result).getEntity();
            if (hit == this.getOwner() || hit instanceof GearlingEntity) {
                return;
            }
            if (hit == victimOrNull() && hit instanceof LivingEntity living) {
                Player owner = hookOwner();
                if (owner != null) {
                    latch(owner, living);
                }
            }
            return; // 其他实体：穿透，不打断锁定
        }
        if (result.getType() == HitResult.Type.BLOCK) {
            if (state == STATE_MISS || state == STATE_RETURN) {
                // 空放撞墙：就地火花，随即折返/直接消失
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            result.getLocation().x, result.getLocation().y, result.getLocation().z,
                            4, 0.12, 0.12, 0.12, 0.22);
                }
                if (state == STATE_MISS) {
                    beginReturn();
                } else {
                    this.discard();
                }
            }
            // STATE_FLY：锁定追踪模式穿方块，保证必中（与巨人同款手感）
        }
    }

    /**
     * 钉住目标。纯工具行为：不打伤害、不走 {@code hurt} 通道，因此不会写入
     * {@code lastHurtBy}——中立生物（铁傀儡、狼、猪灵等）被勾中也不会反过来仇恨玩家，
     * 钩爪全程只是"抓住并拖拽"。
     */
    private void latch(Player owner, LivingEntity victim) {
        this.entityData.set(DATA_STATE, STATE_LATCHED);
        this.stateTicks = 0;
        this.victimId = victim.getId();
        this.setDeltaMovement(Vec3.ZERO);
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 0));
        this.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.9F, 1.3F);
        if (this.level() instanceof ServerLevel serverLevel && owner instanceof ServerPlayer serverPlayer) {
            // 勾中确认音：直接播到发射者耳边（不受距离衰减影响），金属"哐当"+暴击脆响双层
            serverLevel.playSound(serverPlayer, serverPlayer, SoundEvents.CHAIN_PLACE,
                    SoundSource.PLAYERS, 1.0F, 1.5F);
            serverLevel.playSound(serverPlayer, serverPlayer, SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 0.7F, 1.6F);
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    10, 0.3, 0.3, 0.3, 0.26);
        }
    }

    // ---------------------------------------------------------------- 拖拽（拽向玩家）

    private void reelTick(Player owner) {
        LivingEntity victim = victimOrNull();
        if (victim == null) {
            beginReturn();
            return;
        }
        this.stateTicks++;

        // 停止锚点：玩家正前方 REEL_STOP_AHEAD（0.8）格、跟准星朝向。
        // 停止线按"受害者身体边缘贴到锚点"计算：中心离锚点还剩约自身半个身宽即到站，
        // 大小生物都会停在同一条"跟前"线上，绝不"穿过玩家"
        Vec3 look = owner.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0.0, look.z);
        if (fwd.lengthSqr() > 1.0E-6) {
            fwd = fwd.normalize();
        }
        Vec3 anchor = owner.position().add(fwd.scale(REEL_STOP_AHEAD)).add(0.0, 1.0, 0.0);
        Vec3 mid = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
        Vec3 to = anchor.subtract(mid);
        double len = Math.max(1.0E-4, to.length());
        Vec3 flat = new Vec3(to.x, 0.0, to.z);
        double flatLen = Math.max(1.0E-4, flat.length());
        double stopFlat = victim.getBbWidth() * 0.5 + 0.2;

        double speed = Math.min(1.05, 0.45 + this.stateTicks * 0.028);
        // 接近减速：本刻位移不超过"到停止线的剩余距离"，从写入端消灭惯性冲过头
        speed = Math.min(speed, Math.max(0.0, flatLen - stopFlat));
        victim.fallDistance = 0.0F;
        victim.setDeltaMovement(new Vec3(
                to.x / len * speed,
                Mth.clamp(to.y / len * speed, -0.32, 0.5),
                to.z / len * speed));
        victim.hasImpulse = true;
        victim.hurtMarked = true;

        // 爪头钉在受害者身上、朝向玩家
        Vec3 clawPos = mid.add(to.scale(-0.3));
        this.setPos(clawPos.x, clawPos.y, clawPos.z);
        Vec3 face = anchor.subtract(clawPos);
        double horiz = Math.max(1.0E-4, Math.sqrt(face.x * face.x + face.z * face.z));
        this.setYRot((float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F);
        this.setXRot((float) (-Mth.atan2(face.y, horiz) * Mth.RAD_TO_DEG));

        if (this.level() instanceof ServerLevel serverLevel) {
            chainFx(serverLevel, owner, clawPos);
            if (this.stateTicks % 8 == 0) {
                serverLevel.playSound(null, this.blockPosition(), SoundEvents.CHAIN_FALL,
                        SoundSource.PLAYERS, 0.35F, 1.7F);
            }
        }

        if (flatLen < stopFlat + 0.45 || this.stateTicks > MAX_REEL_TICKS) {
            // 身体边缘已到玩家正前方 0.8 格的停止线：收链并把残留水平速度清零，杜绝滑行甩到玩家背后
            if (victim instanceof Mob mob) {
                mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            Vec3 rest = victim.getDeltaMovement();
            victim.setDeltaMovement(0.0, Math.min(0.0, rest.y), 0.0);
            victim.fallDistance = 0.0F;
            victim.hasImpulse = true;
            victim.hurtMarked = true;
            this.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 0.8F);
            beginReturn();
        }
    }

    private void beginReturn() {
        this.entityData.set(DATA_STATE, STATE_RETURN);
        this.stateTicks = 0;
        this.flyTicks = 0;
        this.victimId = -1;
    }

    private void retractFx(Player owner) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, owner.blockPosition(), SoundEvents.CHAIN_PLACE,
                    SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }

    /** "锁链"视觉：沿玩家手部到爪头的连线撒一串末端光点与电火花。 */
    private void chainFx(ServerLevel serverLevel, Player owner, Vec3 clawPos) {
        Vec3 hand = owner.getEyePosition().subtract(0.0, 0.45, 0.0).add(owner.getLookAngle().scale(0.9));
        int segments = 8;
        for (int i = 1; i < segments; i++) {
            Vec3 p = hand.lerp(clawPos, i / (double) segments);
            serverLevel.sendParticles(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD,
                    p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    // ---------------------------------------------------------------- 存档

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("MhState", this.entityData.get(DATA_STATE));
        tag.putInt("MhFly", this.flyTicks);
        tag.putInt("MhTick", this.stateTicks);
        tag.putInt("MhVictim", this.victimId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.flyTicks = tag.getInt("MhFly");
        this.stateTicks = tag.getInt("MhTick");
        this.victimId = tag.getInt("MhVictim");
        // 存读档瞬间追踪/拖拽都没有意义：一律作废回收
        this.entityData.set(DATA_STATE, STATE_VOID);
        this.setDeltaMovement(Vec3.ZERO);
    }

    // ---------------------------------------------------------------- 出手

    /**
     * 由玩家射出。target 为 null 即"未锁定空放"：沿准星直线飞出后自动收回。
     */
    public static MechanicalHookClawEntity fire(ServerLevel level, Player owner, LivingEntity target) {
        Vec3 look = owner.getLookAngle();
        Vec3 origin = owner.getEyePosition().add(look.scale(0.7))
                .subtract(0.0, 0.12, 0.0);
        MechanicalHookClawEntity hook =
                new MechanicalHookClawEntity(ModEntities.MECHANICAL_HOOK_CLAW.get(), owner, level);
        hook.setPos(origin.x, origin.y, origin.z);
        hook.missOriginX = origin.x;
        hook.missOriginY = origin.y;
        hook.missOriginZ = origin.z;
        if (target != null) {
            Vec3 dir = target.position().add(0.0, Math.max(0.4, target.getBbHeight() * 0.55), 0.0).subtract(origin);
            double len = Math.max(1.0E-4, dir.length());
            hook.victimId = target.getId();
            hook.entityData.set(DATA_STATE, STATE_FLY);
            hook.setDeltaMovement(dir.scale(1.5 / len));
        } else {
            hook.entityData.set(DATA_STATE, STATE_MISS);
            hook.missDir = look;
            hook.setDeltaMovement(look.scale(1.75));
        }
        // 爪头朝向出手方向（渲染器按 xRot/yRot 对准）
        hook.setYRot((float) (Mth.atan2(hook.getDeltaMovement().z, hook.getDeltaMovement().x) * Mth.RAD_TO_DEG));
        hook.setXRot((float) (-Mth.atan2(hook.getDeltaMovement().y,
                Math.hypot(hook.getDeltaMovement().x, hook.getDeltaMovement().z)) * Mth.RAD_TO_DEG));
        level.addFreshEntity(hook);
        level.playSound(null, owner, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.9F, 1.15F);
        level.playSound(null, owner, SoundEvents.CHAIN_FALL, SoundSource.PLAYERS, 0.6F, 1.5F);
        return hook;
    }
}
