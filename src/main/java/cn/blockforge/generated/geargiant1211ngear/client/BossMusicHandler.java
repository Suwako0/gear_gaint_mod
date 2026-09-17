package cn.blockforge.generated.geargiant1211ngear.client;

import cn.blockforge.generated.geargiant1211ngear.config.ModConfigs;
import cn.blockforge.generated.geargiant1211ngear.entity.GearGiantEntity;
import cn.blockforge.generated.geargiant1211ngear.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * BOSS 战音乐：玩家进入存活的齿轮巨人 {@value #MUSIC_RADIUS} 格范围内时，
 * 在"音乐"通道播放专属循环曲；离开范围或巨人死亡后 2 秒淡出。
 *
 * 注意：SoundEngine.play() 会把"音量为 0 且 canStartSilent()==false"的实例直接丢弃
 * （且不登记进 tickingSounds，tick() 永远得不到调用），所以淡入式实例必须同时满足：
 * 1) 覆写 canStartSilent() 返回 true；
 * 2) 用 isActive() 兜底检测——若实例被引擎丢弃则重建重放，避免卡死在"已创建未播放"状态。
 */
public final class BossMusicHandler {
    /** 触发战斗音乐的半径（格）。 */
    public static final double MUSIC_RADIUS = 56.0;
    /** 脱离战斗后继续淡出的宽限时间（刻）。 */
    private static final int GRACE_TICKS = 40;
    /** 扫描间隔（刻）。 */
    private static final int CHECK_INTERVAL = 10;

    private static BossMusicInstance current;
    private static int silenceTicks = GRACE_TICKS;
    private static int tickCounter;
    /** 本轮战斗已经淡入到位：重建实例（换世界/被引擎回收）时直接以目标音量续播，不再重新淡入。 */
    private static boolean ramped;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset(mc);
            return;
        }
        if (++tickCounter % CHECK_INTERVAL != 0) {
            return;
        }

        boolean wanted = Boolean.TRUE.equals(ModConfigs.BATTLE_MUSIC.get())
                && giantNear(mc.player);
        if (wanted) {
            silenceTicks = GRACE_TICKS;
            boolean alive = current != null && !current.isStopped()
                    && mc.getSoundManager().isActive(current);
            if (!alive) {
                stopQuietly(mc);
                current = new BossMusicInstance(
                        ModSounds.GEAR_GIANT_BATTLE_MUSIC.get(), ramped);
                mc.getSoundManager().play(current);
            }
            if (current != null && current.isAtTarget()) {
                ramped = true;
            }
        } else if (silenceTicks > 0) {
            silenceTicks -= CHECK_INTERVAL;
        } else if (current != null && !current.isStopped()) {
            current.fadeOut();
        }
    }

    private static void reset(Minecraft mc) {
        stopQuietly(mc);
        current = null;
        silenceTicks = GRACE_TICKS;
        ramped = false;
    }

    private static void stopQuietly(Minecraft mc) {
        if (current != null && !current.isStopped()) {
            mc.getSoundManager().stop(current);
        }
        current = null;
    }

    private static boolean giantNear(Player player) {
        List<GearGiantEntity> giants = player.level().getEntitiesOfClass(
                GearGiantEntity.class,
                player.getBoundingBox().inflate(MUSIC_RADIUS),
                e -> e.isAlive());
        double radiusSq = MUSIC_RADIUS * MUSIC_RADIUS;
        for (GearGiantEntity giant : giants) {
            if (giant.distanceToSqr(player) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private BossMusicHandler() {
    }

    /** 一条全局循环播放的音乐实例：不随距离衰减，音量线性淡入淡出。 */
    private static final class BossMusicInstance extends AbstractTickableSoundInstance {
        /** 每刻淡入步长：约 0.9 秒从静音升到目标音量。 */
        private static final float FADE_IN_STEP = 0.02F;
        /** 每刻淡出步长：约 0.7 秒收尾。 */
        private static final float FADE_OUT_STEP = 0.025F;

        private float target;
        private boolean fading;

        BossMusicInstance(SoundEvent event, boolean instant) {
            super(event, SoundSource.MUSIC, RandomSource.create());
            this.looping = true;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
            this.target = configVolume();
            this.volume = instant ? this.target : 0.0F;
        }

        private static float configVolume() {
            return Mth.clamp(ModConfigs.BATTLE_MUSIC_VOLUME.get().floatValue(), 0.0F, 1.0F);
        }

        void fadeOut() {
            this.fading = true;
        }

        boolean isAtTarget() {
            return !this.fading && this.volume >= this.target - 0.001F;
        }

        @Override
        public void tick() {
            if (this.fading) {
                this.volume -= FADE_OUT_STEP;
                if (this.volume <= 0.0F) {
                    this.volume = 0.0F;
                    this.stop();
                }
            } else {
                this.target = configVolume();
                if (this.volume < this.target) {
                    this.volume = Math.min(this.target, this.volume + FADE_IN_STEP);
                } else {
                    this.volume = this.target;
                }
            }
        }

        @Override
        public boolean canPlaySound() {
            return true;
        }

        /** 关键：允许引擎接受淡入起点的"零音量"实例，否则实例会在 play() 里被直接丢弃。 */
        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
