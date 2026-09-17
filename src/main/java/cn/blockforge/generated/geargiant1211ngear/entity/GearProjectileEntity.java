package cn.blockforge.generated.geargiant1211ngear.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** 齿轮弹幕：旋转着飞出的机械齿轮，投射视觉直接用 Create 的齿轮 item（模组本身硬依赖 Create）。 */
public class GearProjectileEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Float> DATA_DAMAGE =
            SynchedEntityData.defineId(GearProjectileEntity.class, EntityDataSerializers.FLOAT);

    private int life;

    public GearProjectileEntity(EntityType<? extends GearProjectileEntity> type, Level level) {
        super(type, level);
    }

    public GearProjectileEntity(EntityType<? extends GearProjectileEntity> type, LivingEntity owner, Level level) {
        super(type, owner, level);
    }

    public GearProjectileEntity(EntityType<? extends GearProjectileEntity> type, double x, double y, double z, Level level) {
        super(type, x, y, z, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DAMAGE, 5.0F);
    }

    /** 命中伤害（由发射方按 skillDamageScale 折算后写入，随实体数据同步）。 */
    public void setGearDamage(float damage) {
        this.entityData.set(DATA_DAMAGE, Math.max(0.0F, damage));
    }

    public float getGearDamage() {
        return this.entityData.get(DATA_DAMAGE);
    }

    @Override
    protected Item getDefaultItem() {
        Item cogwheel = BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:cogwheel"));
        return cogwheel == null || cogwheel == Items.AIR ? Items.IRON_NUGGET : cogwheel;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.025;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.life++;
            if (this.life > 240) {
                this.discard();
            }
        }
        if (this.level().isClientSide && this.random.nextInt(6) == 0) {
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.discard();
            return;
        }
        // 客户端：命中瞬间齿轮"磕碎"的火花与碎块
        for (int i = 0; i < 10; i++) {
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY(), this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.35,
                    this.random.nextDouble() * 0.25,
                    (this.random.nextDouble() - 0.5) * 0.35);
        }
        for (int i = 0; i < 4; i++) {
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    this.getX(), this.getY(), this.getZ(), 0.0, 0.08, 0.0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        if (this.level().isClientSide || target == this.getOwner()) {
            return;
        }
        DamageSource source = this.getOwner() instanceof LivingEntity living
                ? this.damageSources().mobProjectile(this, living)
                : this.damageSources().thrown(this, this.getOwner());
        target.hurt(source, this.getGearDamage());
        this.playSound(SoundEvents.ANVIL_HIT, 0.7F, 1.5F);
    }

    @Override
    protected void onDeflection(Entity bounceFrom, boolean critical) {
        super.onDeflection(bounceFrom, critical);
    }
}
