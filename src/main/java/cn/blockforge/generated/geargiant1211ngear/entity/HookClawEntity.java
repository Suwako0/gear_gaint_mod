package cn.blockforge.generated.geargiant1211ngear.entity;

import cn.blockforge.generated.geargiant1211ngear.config.ModConfigs;
import cn.blockforge.generated.geargiant1211ngear.registry.ModEntities;
import cn.blockforge.generated.geargiant1211ngear.util.AnnounceUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 机械钩爪（锁定玩家版）：掷出时即绑定一名玩家，爪头每刻向该玩家胸口转向、
 * 可穿过方块，因此必定命中——不存在"打空"。命中后把玩家一路拖拽到巨人脚下
 * 并狠狠甩摔；拖拽若被地形/客户端碰撞卡住，则降级为小步传送继续拉人。
 *
 * 拖拽的实现要点：1.21.1 的 ServerEntity 只有实体带 {@code hurtMarked} 标记时，
 * 才会把速度包发给"受害者自己的客户端"。玩家客户端本地输入每 tick 都会向服务端
 * 上报位置，服务端速度若不同步给本人，拖拽位移会立刻被对方的位置上报冲掉——
 * 这就是早期"钩住了却拉不动"的根因。收链期间每刻写速度并置 hurtMarked，
 * 走的正是原版爆炸击退同一条通道。
 */
public class HookClawEntity extends ThrowableProjectile {
    private static final int STATE_FLY = 0;
    private static final int STATE_LATCHED = 1;
    /** 读档时无法恢复的"已作废"状态：下一 tick 直接回收。 */
    private static final int STATE_VOID = 3;

    private static final EntityDataAccessor<Float> DATA_DAMAGE =
            SynchedEntityData.defineId(HookClawEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(HookClawEntity.class, EntityDataSerializers.INT);

    /** 追踪兜底时限：锁定追踪基本 1~2 秒内必然贴脸，超时说明目标丢失。 */
    private static final int MAX_FLY_TICKS = 110;
    /** 拖拽硬上限：超过这个刻数无论到没到身边都强制甩摔。 */
    private static final int MAX_REEL_TICKS = 70;
    /** 飞行中爪头与目标的极限距离（目标传送逃远则放弃）。 */
    private static final double GIVEUP_DIST = 56.0;
    /** 锁定判定的贴脸距离：走到这个范围内直接钉住，不依赖射线命中。 */
    private static final double LATCH_DIST = 1.35;

    private int flyTicks;
    private int stateTicks;
    private int victimId = -1;
    private double lastHoriz = Double.MAX_VALUE;
    private int stuckTicks;

    public HookClawEntity(EntityType<? extends HookClawEntity> type, Level level) {
        super(type, level);
    }

    public HookClawEntity(EntityType<? extends HookClawEntity> type, LivingEntity owner, Level level) {
        super(type, owner, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DAMAGE, 5.0F);
        builder.define(DATA_STATE, STATE_FLY);
    }

    /** 命中与甩摔的伤害基数（已含 skillDamageScale，由巨人写入）。 */
    public void setHookDamage(float damage) {
        this.entityData.set(DATA_DAMAGE, Math.max(0.0F, damage));
    }

    public float getHookDamage() {
        return this.entityData.get(DATA_DAMAGE);
    }

    /** 是否已钩住目标正在拖拽（客户端渲染器据此闭合爪口）。 */
    public boolean isLatched() {
        return this.entityData.get(DATA_STATE) == STATE_LATCHED;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0; // 追踪弹道每刻重写速度，不挂重力
    }

    private GearGiantEntity giant() {
        return this.getOwner() instanceof GearGiantEntity giant ? giant : null;
    }

    private Player victimOrNull() {
        if (this.victimId < 0) {
            return null;
        }
        Entity entity = this.level().getEntity(this.victimId);
        if (entity instanceof Player player && player.isAlive()
                && !player.isRemoved() && !player.isSpectator()) {
            return player;
        }
        return null;
    }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            // 钩住后本实体由服务端每刻摆位，客户端不做抛物线预测
            if (this.entityData.get(DATA_STATE) != STATE_FLY) {
                this.setDeltaMovement(Vec3.ZERO);
            }
            super.tick();
            clientFx();
            return;
        }
        GearGiantEntity giant = this.giant();
        if (giant == null || !giant.isAlive()) {
            this.discard();
            return;
        }
        int state = this.entityData.get(DATA_STATE);
        if (state == STATE_FLY) {
            this.flyTicks++;
            if (this.flyTicks > MAX_FLY_TICKS) {
                retractFx(giant);
                this.discard();
                return;
            }
            if (!steerTowardVictim(giant)) {
                return; // 本刻内已回收/已钉住
            }
        } else if (state == STATE_LATCHED) {
            this.setDeltaMovement(Vec3.ZERO); // 防止 Projectile 每刻累加重力
        } else {
            this.discard();
            return;
        }
        super.tick(); // 物理积分 + 沿运动向量的实体命中检测（onHit → latch）
        if (this.isRemoved()) {
            return;
        }
        if (this.entityData.get(DATA_STATE) == STATE_FLY) {
            flightFx(giant);
        } else if (this.entityData.get(DATA_STATE) == STATE_LATCHED) {
            reelTick(giant);
        }
    }

    // ---------------------------------------------------------------- 飞行（锁定追踪）

    /** 每刻把速度重写为"指向锁定的玩家胸口"。返回 false 表示本刻内实体已被回收。 */
    private boolean steerTowardVictim(GearGiantEntity giant) {
        Player victim = victimOrNull();
        if (victim == null || giant.distanceTo(victim) > GIVEUP_DIST) {
            retractFx(giant);
            this.discard();
            return false;
        }
        Vec3 chest = victim.position().add(0.0, 1.05, 0.0);
        Vec3 to = chest.subtract(this.position());
        double dist = to.length();
        if (dist <= LATCH_DIST) {
            latch(giant, victim); // 贴脸强制钉住：不依赖射线命中，必定勾中
            return false;
        }
        double speed = Math.min(2.35, 1.30 + this.flyTicks * 0.06);
        this.setDeltaMovement(to.scale(speed / dist));
        return true;
    }

    /** 追踪尾迹 + 从巨人手掌到爪头的锁链光带（让玩家看清"这次跑不掉"）。 */
    private void flightFx(GearGiantEntity giant) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.24);
            if (this.flyTicks > 2) {
                chainFx(giant, this.position());
            }
        }
    }

    // ---------------------------------------------------------------- 命中处理

    @Override
    protected void onHit(HitResult result) {
        if (this.level().isClientSide || this.entityData.get(DATA_STATE) != STATE_FLY) {
            return;
        }
        GearGiantEntity giant = this.giant();
        if (giant == null) {
            this.discard();
            return;
        }
        if (result.getType() == HitResult.Type.ENTITY) {
            Entity hit = ((EntityHitResult) result).getEntity();
            if (hit == giant || hit instanceof GearlingEntity) {
                return; // 穿过自己人，继续追踪
            }
            if (hit instanceof Player player && !player.isSpectator() && player.isAlive()) {
                // 射线扫到别的玩家也算命中：锁定目标即刻转移到被勾中的人
                latch(giant, player);
            }
            return; // 其他实体：穿透，不打断锁定
        }
        // 撞方块：锁定追踪模式下直接穿过（必定命中设计的核心），火花抹过表面
        if (result.getType() == HitResult.Type.BLOCK && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    result.getLocation().x, result.getLocation().y, result.getLocation().z,
                    3, 0.12, 0.12, 0.12, 0.2);
        }
    }

    private void latch(GearGiantEntity giant, Player victim) {
        this.entityData.set(DATA_STATE, STATE_LATCHED);
        this.stateTicks = 0;
        this.flyTicks = 0;
        this.victimId = victim.getId();
        this.lastHoriz = Double.MAX_VALUE;
        this.stuckTicks = 0;
        this.setDeltaMovement(Vec3.ZERO);
        victim.hurt(this.damageSources().mobProjectile(this, giant), getHookDamage());
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
        giant.startHookPull();
        this.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.8F);
        sparks(victim.getX(), victim.getY() + 1.0, victim.getZ(), 12);
        if (this.level() instanceof ServerLevel serverLevel) {
            AnnounceUtil.actionBar(serverLevel, victim.position(), 96.0,
                    Component.translatable("gear_giant.hook.caught"));
        }
    }

    // ---------------------------------------------------------------- 拖拽

    private void reelTick(GearGiantEntity giant) {
        Entity entity = this.level().getEntity(this.victimId);
        if (!(entity instanceof Player victim) || !victim.isAlive()
                || victim.isRemoved() || victim.isSpectator()) {
            giant.endHookPull();
            this.discard();
            return;
        }
        this.stateTicks++;

        Vec3 fwd = giant.getForward();
        fwd = new Vec3(fwd.x, 0.0, fwd.z).normalize();
        Vec3 anchor = giant.position().add(0.0, 1.35, 0.0).add(fwd.scale(2.2));
        Vec3 mid = victim.position().add(0.0, 0.9, 0.0);
        Vec3 to = anchor.subtract(mid);
        double len = Math.max(1.0E-4, to.length());

        double horizDist = Math.hypot(victim.getX() - giant.getX(), victim.getZ() - giant.getZ());
        // 卡住判定：写进速度的本刻位移没有兑现（被地形碰撞或客户端输入顶住）
        if (horizDist > this.lastHoriz - 0.06) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }
        this.lastHoriz = horizDist;

        double speed = Math.min(0.95, 0.30 + this.stateTicks * 0.024);
        Vec3 pull = new Vec3(to.x / len * speed,
                Mth.clamp(to.y / len * speed, -0.30, 0.42), to.z / len * speed);
        victim.fallDistance = 0.0F;
        victim.setDeltaMovement(pull);
        victim.hasImpulse = true;
        // 关键：让速度包发给受害者本人的客户端（原版爆炸击退同通道），
        // 否则对方的本地输入会把服务端位移直接顶掉，表现就是"钩住了拉不动"。
        victim.hurtMarked = true;

        // 兜底：连续 8 刻没进展就改小步传送继续拖（仅当落点无方块碰撞，不把人了墙）
        if (this.stuckTicks >= 8 && horizDist > 3.6 && victim instanceof ServerPlayer serverPlayer) {
            Vec3 stepDir = new Vec3(to.x / len, 0.0, to.z / len).scale(0.9);
            var movedBox = serverPlayer.getBoundingBox().move(stepDir);
            // 只往"没有方块挡路"的位置挪，绝不把人传送进墙里
            if (serverPlayer.level().noCollision(serverPlayer, movedBox) && movedBox.minY > -64.0) {
                Vec3 center = movedBox.getCenter();
                serverPlayer.teleportTo(center.x, movedBox.minY, center.z);
                serverPlayer.setDeltaMovement(pull);
                serverPlayer.hurtMarked = true;
                this.stuckTicks = 0;
                this.lastHoriz = horizDist;
            }
        }

        // 爪头钉在受害者身上、朝向巨人
        Vec3 clawPos = mid.add(to.scale(-0.35));
        this.setPos(clawPos.x, clawPos.y, clawPos.z);
        Vec3 face = giant.position().add(0.0, 2.2, 0.0).subtract(clawPos);
        double horiz = Math.max(1.0E-4, Math.sqrt(face.x * face.x + face.z * face.z));
        this.setYRot((float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F);
        this.setXRot((float) (-Mth.atan2(face.y, horiz) * Mth.RAD_TO_DEG));

        chainFx(giant, clawPos);
        if (this.level() instanceof ServerLevel serverLevel && this.stateTicks % 10 == 0) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + 1.1, victim.getZ(), 3, 0.25, 0.3, 0.25, 0.2);
        }
        if (this.stateTicks % 7 == 0) {
            this.playSound(SoundEvents.CHAIN_FALL, 0.45F, 1.65F);
        }
        if (this.stateTicks % 20 == 0) {
            this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 0.6F);
        }

        if (horizDist < 2.9 || this.stateTicks > MAX_REEL_TICKS) {
            slam(giant, victim);
        }
    }

    /** 拖到脚边的甩摔：砸地伤害 + 抛飞 + 减速，收尾干净利落。 */
    private void slam(GearGiantEntity giant, Player victim) {
        Vec3 away = victim.position().subtract(giant.position());
        away = new Vec3(away.x, 0.0, away.z);
        double len = Math.max(1.0E-4, away.length());
        victim.hurt(this.damageSources().mobProjectile(this, giant), getHookDamage() * 0.8F);
        victim.setDeltaMovement(away.x / len * 0.62, 0.45, away.z / len * 0.62);
        victim.hasImpulse = true;
        victim.hurtMarked = true;
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        giant.playSound(SoundEvents.ANVIL_LAND, 1.3F, 1.25F);
        sparks(victim.getX(), victim.getY() + 0.5, victim.getZ(), 16);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    victim.getX(), victim.getY() + 0.4, victim.getZ(), 10, 0.4, 0.3, 0.4, 0.05);
        }
        giant.endHookPull();
        this.discard();
    }

    private void retractFx(GearGiantEntity giant) {
        giant.playSound(SoundEvents.CHAIN_FALL, 0.8F, 1.2F);
    }

    // ---------------------------------------------------------------- 特效

    /** "锁链"视觉：沿巨人手掌到爪头的连线撒一串末端光点与电火花。 */
    private void chainFx(GearGiantEntity giant, Vec3 clawPos) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 fwd = giant.getForward();
        fwd = new Vec3(fwd.x, 0.0, fwd.z).normalize();
        Vec3 hand = giant.position().add(0.0, 2.55, 0.0).add(fwd.scale(1.3));
        int segments = 9;
        for (int i = 1; i < segments; i++) {
            Vec3 p = hand.lerp(clawPos, i / (double) segments);
            serverLevel.sendParticles(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD,
                    p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private void sparks(double x, double y, double z, int count) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, count, 0.3, 0.3, 0.3, 0.28);
        }
    }

    private void clientFx() {
        if (this.entityData.get(DATA_STATE) == STATE_FLY && this.random.nextInt(2) == 0) {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    // ---------------------------------------------------------------- 存档

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HcState", this.entityData.get(DATA_STATE));
        tag.putInt("HcFly", this.flyTicks);
        tag.putInt("HcTick", this.stateTicks);
        tag.putInt("HcVictim", this.victimId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.flyTicks = tag.getInt("HcFly");
        this.stateTicks = tag.getInt("HcTick");
        this.victimId = tag.getInt("HcVictim");
        int state = tag.getInt("HcState");
        // 存读档瞬间追踪弹没有意义：已钉住的还按钉住恢复，其余一律作废回收
        this.entityData.set(DATA_STATE, state == STATE_LATCHED ? STATE_LATCHED : STATE_VOID);
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** 由巨人发射：绑定锁定玩家，初始速度先指向对方胸口，之后每刻追踪修正。 */
    public static HookClawEntity fire(ServerLevel level, GearGiantEntity giant, Player victim) {
        Vec3 origin = giant.position().add(0.0, 2.55, 0.0).add(giant.getForward().scale(1.3));
        HookClawEntity hook = new HookClawEntity(ModEntities.HOOK_CLAW.get(), giant, level);
        hook.setPos(origin.x, origin.y, origin.z);
        hook.setHookDamage(giant.scaledSkillDamage(ModConfigs.GIANT_HOOK_DAMAGE.get()));
        Vec3 dir = victim.position().add(0.0, 1.05, 0.0).subtract(origin);
        double len = Math.max(1.0E-4, dir.length());
        hook.victimId = victim.getId();
        hook.setDeltaMovement(dir.scale(1.25 / len));
        level.addFreshEntity(hook);
        return hook;
    }
}
