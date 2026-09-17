package cn.blockforge.generated.geargiant1211ngear.registry;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 模组自定义音效（BOSS 战音乐等），资源见 assets/&lt;modid&gt;/sounds.json。 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, GeneratedMod.MOD_ID);

    public static final ResourceLocation GEAR_GIANT_BATTLE_MUSIC_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "music.gear_giant_battle");

    /** 齿轮巨人 BOSS 战音乐（循环曲目，由客户端 BossMusicHandler 播放）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_GIANT_BATTLE_MUSIC =
            SOUND_EVENTS.register("music.gear_giant_battle",
                    () -> SoundEvent.createVariableRangeEvent(GEAR_GIANT_BATTLE_MUSIC_ID));

    private ModSounds() {
    }
}
