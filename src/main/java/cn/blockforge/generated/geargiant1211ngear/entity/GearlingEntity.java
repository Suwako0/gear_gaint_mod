package cn.blockforge.generated.geargiant1211ngear.entity;

import cn.blockforge.generated.geargiant1211ngear.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 齿轮仆从：第二阶段由巨人放出，替它缠住玩家。 */
public class GearlingEntity extends Monster {

    public GearlingEntity(EntityType<? extends GearlingEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        // 仆从贴近主人作战：若主巨人已死亡则随其消散（由巨人死亡时统一 discard）。
        if (this.level().isClientSide && this.random.nextInt(10) == 0 && this.getTarget() == null) {
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
                    this.getY() + 0.8 + this.random.nextDouble() * 0.4,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.8,
                    0.0, 0.03, 0.0);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ANVIL_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, 0.4F, 1.6F);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }
}
