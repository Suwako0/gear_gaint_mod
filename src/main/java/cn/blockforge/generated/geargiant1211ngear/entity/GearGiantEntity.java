package cn.blockforge.generated.geargiant1211ngear.entity;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import cn.blockforge.generated.geargiant1211ngear.config.ModConfigs;
import cn.blockforge.generated.geargiant1211ngear.entity.goal.GearGiantCombatGoal;
import cn.blockforge.generated.geargiant1211ngear.registry.ModEntities;
import cn.blockforge.generated.geargiant1211ngear.util.AnnounceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 齿轮巨人：三阶段机械 BOSS（默认 1000 血，各项数值见 ModConfigs 配置文件）。
 * 100%~50% 两段连招（双臂砸击+横扫）/ 旋转冲撞 / 跳跃砸击 / 齿轮弹幕 / 机械钩爪；
 * 50%~25% 追加应力护盾、召唤齿轮仆从、蒸汽喷发与旋风横扫；
 * 25%~0%  追加过载激光、踩踏地裂、齿轮暴雨与传送带拉扯（"暴走工厂"终章）；
 * 每次转阶段先获得约 2 秒完全无敌（配置可调），结束后进入 3 秒硬直窗口
 * （受到伤害 ×1.5，并释放应力冲击波）；
 * 仆从存活期间会为巨人持续回血（硬直窗口内被切断）。
 * 招式通过 DATA_ANIM 姿势同步到客户端模型播放对应动作。
 */
public class GearGiantEntity extends Monster {
    public static final int FLAG_STAGGER = 1;
    public static final int FLAG_SHIELD = 2;
    public static final int FLAG_CHARGE = 4;
    public static final int FLAG_PULL = 8;
    public static final int FLAG_BEAM_WARM = 16;
    public static final int FLAG_BEAM_FIRE = 32;
    public static final int FLAG_HEALING = 64;
    public static final int FLAG_PHASE_INVULN = 128;
    /** 机械钩爪已命中玩家、正在收链拖拽。 */
    public static final int FLAG_HOOK = 256;

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(GearGiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FLAGS =
            SynchedEntityData.defineId(GearGiantEntity.class, EntityDataSerializers.INT);
    /** 当前动作姿势（{@link GearGiantAnim}），驱动客户端模型播放招式动画。 */
    private static final EntityDataAccessor<Integer> DATA_ANIM =
            SynchedEntityData.defineId(GearGiantEntity.class, EntityDataSerializers.INT);

    private static final int STAGGER_TICKS = 60;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity." + GeneratedMod.MOD_ID + ".gear_giant"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS);

    private final List<Eruption> eruptions = new ArrayList<>();
    private GearGiantCombatGoal combatGoal;
    private int staggerTimer;
    private int shieldTimer;
    /** 转阶段无敌剩余刻数（仅服务端推进）。 */
    private int phaseInvulnTicks;
    private float healAccum;
    private boolean healAnnounced;

    public GearGiantEntity(EntityType<? extends GearGiantEntity> type, Level level) {
        super(type, level);
        // 战斗属性改为可配置：构造后立即覆盖默认属性（读档/新生成都走这里），
        // 阶段阈值与 Boss 条都按 血量/上限 的比例计算，天然兼容任意上限。
        applyConfigAttributes();
    }

    /** 把配置中的血量/攻击/护甲/移速写回属性基底值。 */
    private void applyConfigAttributes() {
        applyBase(Attributes.MAX_HEALTH, Math.max(20.0D, ModConfigs.GIANT_MAX_HEALTH.get()));
        applyBase(Attributes.ATTACK_DAMAGE, Math.max(1.0D, ModConfigs.GIANT_ATTACK_DAMAGE.get()));
        applyBase(Attributes.ARMOR, Math.max(0.0D, ModConfigs.GIANT_ARMOR.get()));
        applyBase(Attributes.ARMOR_TOUGHNESS, Math.max(0.0D, ModConfigs.GIANT_ARMOR_TOUGHNESS.get()));
        applyBase(Attributes.MOVEMENT_SPEED,
                Mth.clamp(ModConfigs.GIANT_MOVEMENT_SPEED.get(), 0.05D, 1.0D));
        if (this.level().isClientSide) {
            if (this.getHealth() > this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
        } else {
            // 新生成即按配置上限满血出生；读档路径会在构造后用 NBT 的 Health 覆盖回来
            this.setHealth(this.getMaxHealth());
        }
    }

    private void applyBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                           double value) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = this.getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** 技能附加伤害统一乘数：所有招式的固定伤害都经它折算，跟随配置实时生效。 */
    public float scaledSkillDamage(float base) {
        return base * Math.max(0.0F, ModConfigs.SKILL_DAMAGE_SCALE.get().floatValue());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_FLAGS, 0);
        builder.define(DATA_ANIM, GearGiantAnim.NONE);
    }

    // ---------------------------------------------------------------- 阶段与同步

    public int getGiantPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    private void setGiantPhase(int phase) {
        this.entityData.set(DATA_PHASE, phase);
    }

    /** 当前动作姿势（客户端模型据此播放动画）。 */
    public int getAnimPose() {
        return this.entityData.get(DATA_ANIM);
    }

    /** 由战斗状态机在进入/退出招式时调用（仅服务端有意义）。 */
    public void setAnimPose(int pose) {
        if (this.entityData.get(DATA_ANIM) != pose) {
            this.entityData.set(DATA_ANIM, pose);
        }
    }

    private boolean flag(int bit) {
        return (this.entityData.get(DATA_FLAGS) & bit) != 0;
    }

    public void setFlag(int bit, boolean on) {
        int current = this.entityData.get(DATA_FLAGS);
        int next = on ? (current | bit) : (current & ~bit);
        if (current != next) {
            this.entityData.set(DATA_FLAGS, next);
        }
    }

    public boolean isStaggered() {
        return flag(FLAG_STAGGER);
    }

    public boolean isShieldActive() {
        return flag(FLAG_SHIELD);
    }

    public boolean isChargeActive() {
        return flag(FLAG_CHARGE);
    }

    public boolean isPullActive() {
        return flag(FLAG_PULL);
    }

    public boolean isBeamCharging() {
        return flag(FLAG_BEAM_WARM);
    }

    public boolean isBeamFiring() {
        return flag(FLAG_BEAM_FIRE);
    }

    /** 是否正在被齿轮仆从回灌应力（供客户端模型/粒子使用）。 */
    public boolean isReceivingStress() {
        return flag(FLAG_HEALING);
    }

    /** 是否处于转阶段无敌窗口（服务端计时，经 FLAG_PHASE_INVULN 同步到客户端）。 */
    public boolean isPhaseInvulnerable() {
        return flag(FLAG_PHASE_INVULN);
    }

    /** 机械钩爪是否正牵着玩家收链。 */
    public boolean isHookReeling() {
        return flag(FLAG_HOOK);
    }

    /** 钩爪命中玩家时由 {@link HookClawEntity} 调用：进入收链姿势（战斗状态机空闲时）。 */
    public void startHookPull() {
        setFlag(FLAG_HOOK, true);
        if (isCombatIdle()) {
            setAnimPose(GearGiantAnim.HOOK_PULL);
        }
    }

    /** 收链结束（甩摔/脱靶/巨人死亡）：退出钩爪姿势。 */
    public void endHookPull() {
        setFlag(FLAG_HOOK, false);
        if (isCombatIdle() && getAnimPose() == GearGiantAnim.HOOK_PULL) {
            setAnimPose(GearGiantAnim.NONE);
        }
    }

    /** 战斗状态机当前是否没有进行中的招式（钩爪姿势接管的前提）。 */
    public boolean isCombatIdle() {
        return this.combatGoal == null || !this.combatGoal.isAttacking();
    }

    /**
     * 仆从充能回血：存活且距离最近的巨人就是"我们"的仆从，会持续把应力
     * 回灌给巨人（硬直窗口内回血被切断——打断召唤物才是正确打法）。
     */
    private void tickGearlingHealing(ServerLevel level) {
        boolean allowed = ModConfigs.GEARLING_HEALING.get() && !isStaggered() && !isRemoved();
        int linked = 0;
        List<GearlingEntity> nearby = allowed
                ? level.getEntitiesOfClass(GearlingEntity.class,
                        this.getBoundingBox().inflate(48.0), GearlingEntity::isAlive)
                : List.of();
        for (GearlingEntity gearling : nearby) {
            if (isHealLinkFrom(gearling, level)) {
                linked++;
            }
        }
        int counted = Math.min(linked, Math.max(1, ModConfigs.GEARLING_HEAL_LINK_CAP.get()));
        boolean healing = counted > 0 && this.getHealth() < this.getMaxHealth() - 0.01F;
        setFlag(FLAG_HEALING, healing);
        if (!healing) {
            this.healAnnounced = false;
            return;
        }

        this.healAccum += counted * ModConfigs.GEARLING_HEAL_PER_SECOND.get().floatValue() / 20.0F;
        if (this.healAccum >= 0.5F) {
            this.heal(this.healAccum);
            this.healAccum = 0.0F;
        }

        if (!this.healAnnounced) {
            this.healAnnounced = true;
            AnnounceUtil.actionBar(level, this.position(), 96.0,
                    Component.translatable("gear_giant.healing"));
            this.playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.55F);
        }

        if (this.tickCount % 20 == 0) {
            int drawn = 0;
            for (GearlingEntity gearling : nearby) {
                if (drawn >= counted) {
                    break;
                }
                if (!isHealLinkFrom(gearling, level)) {
                    continue;
                }
                drawn++;
                Vec3 from = gearling.getEyePosition();
                Vec3 to = this.position().add(0.0, this.getBbHeight() * 0.62, 0.0);
                for (int i = 1; i <= 5; i++) {
                    Vec3 p = from.lerp(to, i / 6.0);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            p.x, p.y, p.z, 1, 0.06, 0.06, 0.06, 0.0);
                }
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                    this.getX(), this.getY() + this.getBbHeight() * 0.62, this.getZ(),
                    8, 1.0, 0.9, 1.0, 0.15);
        }
        if (this.tickCount % 60 == 0) {
            level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.HOSTILE, 0.5F, 1.45F);
        }
    }

    /** 仆从只充能离自己最近的那头巨人，避免多头巨人共用一个仆从回血。 */
    private boolean isHealLinkFrom(GearlingEntity gearling, ServerLevel level) {
        GearGiantEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (GearGiantEntity giant : level.getEntitiesOfClass(GearGiantEntity.class,
                gearling.getBoundingBox().inflate(56.0), GearGiantEntity::isAlive)) {
            double d = giant.distanceToSqr(gearling);
            if (d < best) {
                best = d;
                nearest = giant;
            }
        }
        return nearest == this;
    }

    public int getShieldTicks() {
        return this.shieldTimer;
    }

    public boolean hasPendingEruptions() {
        return !this.eruptions.isEmpty();
    }

    /** 齿轮动画转速：进攻姿态时越快，硬随时停转。 */
    public float getGearSpinSpeed() {
        if (isStaggered()) {
            return 0.03F;
        }
        switch (getAnimPose()) {
            case GearGiantAnim.SPIN:
                return 1.0F;
            case GearGiantAnim.CHARGE_RUSH:
            case GearGiantAnim.BARRAGE:
            case GearGiantAnim.BEAM_FIRE:
                return 0.7F;
            case GearGiantAnim.HOOK_PULL:
                return 0.62F;
            case GearGiantAnim.HOOK_THROW:
                return 0.45F;
            case GearGiantAnim.HOOK_WINDUP:
                return 0.35F;
            default:
                break;
        }
        if (isChargeActive() || isBeamFiring()) {
            return 0.55F;
        }
        if (isReceivingStress()) {
            return 0.42F;
        }
        if (isBeamCharging() || isPullActive()) {
            return 0.35F;
        }
        if (isShieldActive()) {
            return 0.22F;
        }
        return 0.12F;
    }

    public double phaseSpeedModifier() {
        return switch (getGiantPhase()) {
            case 2 -> 1.15;
            case 3 -> 1.35;
            default -> 1.0;
        };
    }

    // ---------------------------------------------------------------- 伤害与阶段推进

    /** 转阶段无敌：除 /kill、虚空这类"绕过无敌"的伤害外，窗口内完全免疫。 */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (this.phaseInvulnTicks > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || amount <= 0.0F) {
            return super.hurt(source, amount);
        }
        if (this.phaseInvulnTicks > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // 免疫，但给出被"弹开"的反馈（音＋火花），让玩家知道现在打不动。
            this.playSound(SoundEvents.SHIELD_BLOCK, 1.1F, 0.55F);
            return false;
        }
        float applied = amount;
        if (isStaggered()) {
            applied *= 1.5F;
        }
        if (isShieldActive()) {
            applied *= 0.3F;
            this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.7F);
        }
        boolean hurt = super.hurt(source, applied);
        if (hurt) {
            updatePhase();
        }
        return hurt;
    }

    /** 血量跨过 50% / 25% 阈值时进入转阶段硬直。 */
    public void updatePhase() {
        if (this.isRemoved()) {
            return;
        }
        float ratio = this.getHealth() / this.getMaxHealth();
        int phase = getGiantPhase();
        if (phase == 1 && ratio <= 0.5F) {
            enterPhase(2);
        } else if (phase == 2 && ratio <= 0.25F) {
            enterPhase(3);
        }
    }

    private void enterPhase(int next) {
        setGiantPhase(next);
        this.staggerTimer = STAGGER_TICKS;
        setFlag(FLAG_STAGGER, true);
        // 先给一段完全无敌的时间让转场演出落地，之后才是"受击 ×1.5"的硬直窗口
        int invuln = Math.max(0, ModConfigs.GIANT_PHASE_INVULN_TICKS.get());
        this.phaseInvulnTicks = invuln;
        setFlag(FLAG_PHASE_INVULN, invuln > 0);
        if (this.combatGoal != null) {
            this.combatGoal.cancelAttack();
        }
        this.getNavigation().stop();

        this.bossEvent.setColor(BossEvent.BossBarColor.WHITE);
        this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.4F, next == 3 ? 0.7F : 0.9F);
        this.playSound(SoundEvents.ANVIL_LAND, 1.6F, 0.7F);

        if (this.level() instanceof ServerLevel serverLevel) {
            // 硬直冲击波：把附近玩家弹开。
            for (Player player : serverLevel.getEntitiesOfClass(Player.class,
                    this.getBoundingBox().inflate(12.0), p -> p.isAlive() && !p.isSpectator())) {
                Vec3 push = player.position().subtract(this.position());
                double len = Math.max(1.0E-4, Math.sqrt(push.x * push.x + push.z * push.z));
                player.setDeltaMovement(player.getDeltaMovement().add(push.x / len * 0.9, 0.45, push.z / len * 0.9));
                player.hurt(this.damageSources().mobAttack(this), scaledSkillDamage(2.0F));
                player.hasImpulse = true;
            }
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY() + 1.8, this.getZ(), 60, 2.2, 1.4, 2.2, 0.25);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    this.getX(), this.getY() + 1.2, this.getZ(), 40, 2.0, 1.0, 2.0, 0.12);
            AnnounceUtil.broadcastTitle(serverLevel, this.position(), 96.0, null,
                    Component.translatable("gear_giant.phase." + next), null);
        }
    }

    private void onStaggerEnd() {
        setFlag(FLAG_STAGGER, false);
        this.playSound(SoundEvents.ANVIL_USE, 1.4F, 0.9F);
        if (getGiantPhase() == 2) {
            activateShield();
            summonGearlings(2);
            if (this.level() instanceof ServerLevel serverLevel) {
                AnnounceUtil.actionBar(serverLevel, this.position(), 96.0,
                        Component.translatable("gear_giant.shield.on"));
            }
        } else if (getGiantPhase() == 3 && this.level() instanceof ServerLevel serverLevel) {
            AnnounceUtil.actionBar(serverLevel, this.position(), 96.0,
                    Component.translatable("gear_giant.overload"));
        }
    }

    // ---------------------------------------------------------------- 应力护盾 / 召唤

    public void activateShield() {
        this.shieldTimer = 200;
        setFlag(FLAG_SHIELD, true);
        this.playSound(SoundEvents.BEACON_ACTIVATE, 1.2F, 0.6F);
    }

    public void summonGearlings(int count) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int alive = serverLevel.getEntitiesOfClass(GearlingEntity.class,
                this.getBoundingBox().inflate(48.0)).size();
        int toSpawn = Math.max(0, Math.min(count, 6 - alive));
        RandomSource random = serverLevel.getRandom();
        for (int i = 0; i < toSpawn; i++) {
            BlockPos around = this.blockPosition().offset(
                    random.nextInt(5) - 2, 1, random.nextInt(5) - 2);
            GearlingEntity gearling = ModEntities.GEARLING.get().create(serverLevel);
            if (gearling == null) {
                continue;
            }
            gearling.setPos(around.getX() + 0.5, around.getY(), around.getZ() + 0.5);
            if (this.getTarget() instanceof LivingEntity living && living.isAlive()) {
                gearling.setTarget(living);
            }
            serverLevel.addFreshEntity(gearling);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    gearling.getX(), gearling.getY() + 0.5, gearling.getZ(), 12, 0.4, 0.3, 0.4, 0.04);
        }
        this.playSound(SoundEvents.ANVIL_USE, 1.2F, 1.1F);
    }

    // ---------------------------------------------------------------- 地裂齿轮

    public void queueEruption(Vec3 pos, int delay) {
        this.eruptions.add(new Eruption(pos.x, pos.y, pos.z, delay));
    }

    public void tickEruptions(ServerLevel level) {
        Iterator<Eruption> it = this.eruptions.iterator();
        while (it.hasNext()) {
            Eruption eruption = it.next();
            eruption.time--;
            if (eruption.time == 30) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                        eruption.x, eruption.y + 0.15, eruption.z, 10, 0.35, 0.1, 0.35, 0.02);
                level.playSound(null, eruption.x, eruption.y, eruption.z,
                        SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 0.8F, 0.7F);
            } else if (eruption.time == 14) {
                // 第二段预警：裂缝即将炸开
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                        eruption.x, eruption.y + 0.1, eruption.z, 6, 0.3, 0.08, 0.3, 0.02);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        eruption.x, eruption.y + 0.15, eruption.z, 8, 0.3, 0.1, 0.3, 0.22);
            } else if (eruption.time <= 0) {
                it.remove();
                erupt(level, eruption);
            }
        }
    }

    private void erupt(ServerLevel level, Eruption eruption) {
        BlockPos pos = BlockPos.containing(eruption.x, eruption.y, eruption.z);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.1F, 0.75F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                eruption.x, eruption.y + 0.2, eruption.z, 18, 0.4, 0.4, 0.4, 0.06);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                eruption.x, eruption.y + 0.4, eruption.z, 10, 0.3, 0.5, 0.3, 0.02);
        // 地面被顶裂：喷出的碎块直接采用现场方块，观感更真实（地形不会被破坏）
        BlockState ground = level.getBlockState(pos.below());
        if (!ground.isAir()) {
            level.sendParticles(
                    new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
                    eruption.x, eruption.y + 0.35, eruption.z, 26, 0.5, 0.3, 0.5, 0.14);
        }

        AABB area = new AABB(eruption.x - 2.2, eruption.y - 0.6, eruption.z - 2.2,
                eruption.x + 2.2, eruption.y + 2.6, eruption.x + 2.2);
        for (Player player : level.getEntitiesOfClass(Player.class, area, p -> p.isAlive() && !p.isSpectator())) {
            player.hurt(this.damageSources().mobAttack(this), scaledSkillDamage(6.0F));
            player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.55, 0.0));
            player.hasImpulse = true;
        }

        GearProjectileEntity shard = new GearProjectileEntity(ModEntities.GEAR_PROJECTILE.get(), this, level);
        shard.setPos(eruption.x, eruption.y + 0.6, eruption.z);
        shard.setGearDamage(scaledSkillDamage(4.0F));
        shard.shoot(0.0, 0.85, 0.0, 0.55F, 10.0F);
        level.addFreshEntity(shard);
    }

    // ---------------------------------------------------------------- AI 骨架

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.combatGoal = new GearGiantCombatGoal(this);
        this.goalSelector.addGoal(2, this.combatGoal);
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        if (this.phaseInvulnTicks > 0) {
            this.phaseInvulnTicks--;
            if (this.phaseInvulnTicks == 0) {
                setFlag(FLAG_PHASE_INVULN, false);
                this.playSound(SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.3F);
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            this.getX(), this.getY() + 2.0, this.getZ(), 40, 1.8, 1.4, 1.8, 0.3);
                    AnnounceUtil.actionBar(serverLevel, this.position(), 96.0,
                            Component.translatable("gear_giant.invuln.end"));
                }
            }
        }
        if (this.staggerTimer > 0) {
            this.staggerTimer--;
            this.getNavigation().stop();
            if (this.staggerTimer == 0) {
                onStaggerEnd();
            }
        }
        if (this.shieldTimer > 0) {
            this.shieldTimer--;
            if (this.shieldTimer == 0) {
                setFlag(FLAG_SHIELD, false);
                this.playSound(SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.7F);
                if (this.level() instanceof ServerLevel serverLevel) {
                    AnnounceUtil.actionBar(serverLevel, this.position(), 96.0,
                            Component.translatable("gear_giant.shield.off"));
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                            this.getX(), this.getY() + 1.8, this.getZ(), 25, 1.6, 1.2, 1.6, 0.08);
                }
            }
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            this.tickEruptions(serverLevel);
            this.tickGearlingHealing(serverLevel);
            this.syncBossBarColor();
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        super.customServerAiStep();
    }

    /** Boss 条颜色跟随状态：转场无敌白 / 硬直黄 / 仆从充能绿 / 二阶段粉 / 三阶段红。 */
    private void syncBossBarColor() {
        BossEvent.BossBarColor want;
        if (isPhaseInvulnerable()) {
            want = BossEvent.BossBarColor.WHITE;
        } else if (isStaggered()) {
            want = BossEvent.BossBarColor.YELLOW;
        } else if (isReceivingStress()) {
            want = BossEvent.BossBarColor.GREEN;
        } else if (getGiantPhase() == 3) {
            want = BossEvent.BossBarColor.RED;
        } else if (getGiantPhase() == 2) {
            want = BossEvent.BossBarColor.PINK;
        } else {
            want = BossEvent.BossBarColor.YELLOW;
        }
        if (this.bossEvent.getColor() != want) {
            this.bossEvent.setColor(want);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientAmbientFx();
        }
    }

    private void clientAmbientFx() {
        RandomSource random = this.getRandom();
        if (isPhaseInvulnerable()) {
            // 应力护罩全开：通体白色光点环绕，明确告诉玩家"现在打不动"
            for (int i = 0; i < 3; i++) {
                double ang = random.nextDouble() * Math.PI * 2.0;
                double r = 1.8 + random.nextDouble() * 0.9;
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        this.getX() + Math.cos(ang) * r,
                        this.getY() + 0.4 + random.nextDouble() * 3.0,
                        this.getZ() + Math.sin(ang) * r,
                        0.0, 0.03, 0.0);
            }
        }
        if (isStaggered()) {
            if (random.nextInt(2) == 0) {
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        this.getX() + (random.nextDouble() - 0.5) * 2.4,
                        this.getY() + 1.6 + random.nextDouble() * 1.6,
                        this.getZ() + (random.nextDouble() - 0.5) * 2.4,
                        0.0, 0.05, 0.0);
            }
        }
        if (isShieldActive() && random.nextInt(3) == 0) {
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    this.getX() + (random.nextDouble() - 0.5) * 3.4,
                    this.getY() + random.nextDouble() * 3.2,
                    this.getZ() + (random.nextDouble() - 0.5) * 3.4,
                    0.0, 0.0, 0.0);
        }
        boolean hot = getGiantPhase() >= 3 || isChargeActive() || isBeamFiring();
        if (hot && random.nextInt(3) == 0) {
            this.level().addParticle(
                    random.nextBoolean() ? net.minecraft.core.particles.ParticleTypes.SMOKE
                            : net.minecraft.core.particles.ParticleTypes.FLAME,
                    this.getX() + (random.nextDouble() - 0.5) * 2.0,
                    this.getY() + 2.6 + random.nextDouble() * 0.8,
                    this.getZ() + (random.nextDouble() - 0.5) * 2.0,
                    0.0, 0.06, 0.0);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
        this.bossEvent.setVisible(true);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        this.bossEvent.setProgress(0.0F);
        setFlag(FLAG_HEALING, false);
        setFlag(FLAG_HOOK, false);
        this.phaseInvulnTicks = 0;
        setFlag(FLAG_PHASE_INVULN, false);
        this.playSound(SoundEvents.ANVIL_BREAK, 2.0F, 0.7F);
        if (this.level() instanceof ServerLevel serverLevel) {
            for (GearlingEntity gearling : serverLevel.getEntitiesOfClass(GearlingEntity.class,
                    this.getBoundingBox().inflate(64.0))) {
                gearling.discard();
            }
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW_SQUID_INK,
                    this.getX(), this.getY() + 1.6, this.getZ(), 30, 1.4, 1.4, 1.4, 0.0);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                    this.getX(), this.getY() + 1.6, this.getZ(), 6, 1.6, 1.2, 1.6, 0.0);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        this.bossEvent.removeAllPlayers();
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public int getBaseExperienceReward() {
        // 经验随配置血量小幅缩放：默认 1000 血约 200 点。
        return (int) Mth.clamp(this.getMaxHealth() * 0.2F, 50.0F, 800.0F);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_AMBIENT;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ANVIL_HIT, 0.25F, 0.75F);
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    // ---------------------------------------------------------------- 存档

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("GgPhase", getGiantPhase());
        tag.putInt("GgStagger", this.staggerTimer);
        tag.putInt("GgShield", this.shieldTimer);
        tag.putInt("GgInvuln", this.phaseInvulnTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setGiantPhase(Math.max(1, Math.min(3, tag.getInt("GgPhase"))));
        this.staggerTimer = tag.getInt("GgStagger");
        this.shieldTimer = tag.getInt("GgShield");
        this.phaseInvulnTicks = tag.getInt("GgInvuln");
        setFlag(FLAG_STAGGER, this.staggerTimer > 0);
        setFlag(FLAG_SHIELD, this.shieldTimer > 0);
        setFlag(FLAG_PHASE_INVULN, this.phaseInvulnTicks > 0);
        if (getGiantPhase() != 1) {
            this.bossEvent.setColor(getGiantPhase() == 3 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PINK);
        }
    }

    private static final class Eruption {
        final double x;
        final double y;
        final double z;
        int time;

        Eruption(double x, double y, double z, int time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
        }
    }
}
