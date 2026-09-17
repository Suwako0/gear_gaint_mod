package cn.blockforge.generated.geargiant1211ngear.block;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import cn.blockforge.generated.geargiant1211ngear.entity.GearGiantEntity;
import cn.blockforge.generated.geargiant1211ngear.integration.CreateStressProbe;
import cn.blockforge.generated.geargiant1211ngear.registry.ModBlocks;
import cn.blockforge.generated.geargiant1211ngear.registry.ModEntities;
import cn.blockforge.generated.geargiant1211ngear.util.AnnounceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 齿轮巨人核心的方块实体：检测相邻 Create 旋转速度，累计充能并在顶端生成 BOSS。
 * 召唤完成后核心会在数十刻内"过载爆碎"——只有声光效果，不破坏任何地形，
 * 碎完后核心自身从世界上消失（无掉落），使命结束。
 */
public class GearGiantCoreBlockEntity extends BlockEntity {
    /** 需要达到的最低转速（RPM），对应 Create 网络中至少一格有效旋转输入。 */
    public static final float MIN_ROTATION_RPM = 16.0F;
    public static final int CHARGE_TOTAL = 200;
    public static final int SUMMON_COOLDOWN = 2400;
    /** 召唤后核心过载爆碎的倒计时（刻），给玩家留出先看清巨人的时间。 */
    public static final int SHATTER_DELAY = 45;

    private int charge;
    private int cooldown;
    private int explodeTimer;
    private boolean lastCharging;

    public GearGiantCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntities.GEAR_CORE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GearGiantCoreBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (be.explodeTimer > 0) {
            be.explodeTimer--;
            be.shatterWarning(serverLevel, pos);
            if (be.explodeTimer == 0) {
                be.detonate(serverLevel, pos);
            }
            return;
        }
        if (be.cooldown > 0) {
            be.cooldown--;
            if (be.charge > 0) {
                be.charge = 0;
                be.syncCharging(serverLevel, pos, state, false);
            }
            return;
        }

        float speed = CreateStressProbe.maxAdjacentRotation(level, pos);
        boolean spinning = speed >= MIN_ROTATION_RPM;

        if (spinning) {
            // 同一区域已有巨人时不启动新的召唤，保持待命。
            if (bossNearby(serverLevel, pos)) {
                return;
            }
            be.charge = Math.min(CHARGE_TOTAL, be.charge + 1);
            be.syncCharging(serverLevel, pos, state, true);
            be.chargeFx(serverLevel, pos);
            if (be.charge >= CHARGE_TOTAL) {
                be.summon(serverLevel, pos);
                be.charge = 0;
                be.cooldown = SUMMON_COOLDOWN;
                be.syncCharging(serverLevel, pos, state, false);
            }
        } else if (be.charge > 0) {
            be.charge = Math.max(0, be.charge - 2);
            if (be.charge == 0) {
                be.syncCharging(serverLevel, pos, state, false);
            }
        }
    }

    private static boolean bossNearby(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(GearGiantEntity.class, new AABB(pos).inflate(96.0)).isEmpty();
    }

    private void chargeFx(ServerLevel level, BlockPos pos) {
        if (this.charge % 8 == 0) {
            Vec3 center = Vec3.atCenterOf(pos).add(0.0, 0.9, 0.0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    center.x, center.y, center.z, 2, 0.45, 0.25, 0.45, 0.0);
        }
        if (this.charge % 24 == 0) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F,
                    0.8F + (this.charge / (float) CHARGE_TOTAL) * 0.8F);
        }
    }

    private void summon(ServerLevel level, BlockPos pos) {
        BlockPos spawnPos = pos.above(2);
        while (spawnPos.getY() < level.getMaxBuildHeight() - 4 && !level.isEmptyBlock(spawnPos)) {
            spawnPos = spawnPos.above();
        }
        GearGiantEntity giant = ModEntities.GEAR_GIANT.get().create(level);
        if (giant == null) {
            return;
        }
        giant.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        level.addFreshEntity(giant);

        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.6F, 0.7F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.7, 0.7, 0.7, 0.05);
        AnnounceUtil.broadcastTitle(level, Vec3.atCenterOf(pos), 96.0,
                Component.translatable("gear_giant.summon.title"),
                Component.translatable("gear_giant.summon.subtitle"),
                Component.translatable("gear_giant.summon.chat"));

        // 核心已完成使命：短暂过载后爆碎消失（纯声光，不破坏地形、不伤人）。
        this.explodeTimer = SHATTER_DELAY;
    }

    /** 爆碎前的过载抖动：闪烁火花 + 越来越急的金属声。 */
    private void shatterWarning(ServerLevel level, BlockPos pos) {
        if (this.explodeTimer % 6 == 0) {
            Vec3 center = Vec3.atCenterOf(pos);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    center.x, center.y, center.z, 6, 0.5, 0.5, 0.5, 0.28);
            float f = 1.0F - this.explodeTimer / (float) SHATTER_DELAY;
            level.playSound(null, pos, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
                    SoundSource.BLOCKS, 0.8F, 0.8F + f * 1.4F);
        }
    }

    /** 过载爆碎：一次性声光 + 碎块粒子，只移除核心本身，周边地形毫发无损。 */
    private void detonate(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos).add(0.0, 0.35, 0.0);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, pos, SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 1.4F, 0.8F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y, center.z, 2, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                center.x, center.y, center.z, 45, 0.7, 0.6, 0.7, 0.09);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y, center.z, 70, 0.85, 0.7, 0.85, 0.4);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                center.x, center.y, center.z, 20, 0.5, 0.45, 0.5, 0.12);
        // 用方块自身的碎块粒子表现"炸成零件"，drop=false：不掉落、不更新周边方块。
        BlockState self = level.getBlockState(pos);
        if (self.is(ModBlocks.GEAR_GIANT_CORE.get())) {
            level.sendParticles(
                    new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.BLOCK, self),
                    center.x, center.y, center.z, 40, 0.45, 0.4, 0.45, 0.16);
        }
        AnnounceUtil.actionBar(level, Vec3.atCenterOf(pos), 64.0,
                Component.translatable("gear_giant.core.shattered"));
        level.destroyBlock(pos, false, null);
    }

    private void syncCharging(ServerLevel level, BlockPos pos, BlockState state, boolean charging) {
        if (this.lastCharging != charging) {
            this.lastCharging = charging;
            level.setBlock(pos, state.setValue(GearGiantCoreBlock.CHARGING, charging), 2);
        }
    }

    public float chargeProgress() {
        return this.charge / (float) CHARGE_TOTAL;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("gg_charge", this.charge);
        tag.putInt("gg_cooldown", this.cooldown);
        tag.putInt("gg_explode", this.explodeTimer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.charge = tag.getInt("gg_charge");
        this.cooldown = tag.getInt("gg_cooldown");
        this.explodeTimer = tag.getInt("gg_explode");
    }
}
