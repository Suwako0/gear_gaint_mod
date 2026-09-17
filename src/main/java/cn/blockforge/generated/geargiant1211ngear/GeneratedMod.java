package cn.blockforge.generated.geargiant1211ngear;

import cn.blockforge.generated.geargiant1211ngear.client.BossMusicHandler;
import cn.blockforge.generated.geargiant1211ngear.client.ModClientEvents;
import cn.blockforge.generated.geargiant1211ngear.config.ModConfigs;
import cn.blockforge.generated.geargiant1211ngear.entity.GearGiantEntity;
import cn.blockforge.generated.geargiant1211ngear.entity.GearlingEntity;
import cn.blockforge.generated.geargiant1211ngear.registry.ModBlocks;
import cn.blockforge.generated.geargiant1211ngear.registry.ModEntities;
import cn.blockforge.generated.geargiant1211ngear.registry.ModItems;
import cn.blockforge.generated.geargiant1211ngear.registry.ModMenus;
import cn.blockforge.generated.geargiant1211ngear.registry.ModSounds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * gear_giant：依赖 Create（机械动力）的齿轮巨人 BOSS 模组。
 * 合成并放置“齿轮巨人核心”，向其相邻动力轴输入旋转应力即可唤醒三阶段齿轮巨人。
 */
@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {
    public static final String MOD_ID = "gear_giant";

    public GeneratedMod(IEventBus modBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenus.MENU_TYPES.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, ModConfigs.SPEC);

        modBus.addListener(this::registerAttributes);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(ModClientEvents::registerEntityRenderers);
            modBus.addListener(ModClientEvents::registerLayerDefinitions);
            modBus.addListener(ModClientEvents::registerAdditionalModels);
            modBus.addListener(ModClientEvents::registerScreens);
            NeoForge.EVENT_BUS.addListener(BossMusicHandler::onClientTick);
        }
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GEAR_GIANT.get(), GearGiantEntity.createAttributes().build());
        event.put(ModEntities.GEARLING.get(), GearlingEntity.createAttributes().build());
    }
}
