package cn.blockforge.generated.geargiant1211ngear.client;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import cn.blockforge.generated.geargiant1211ngear.client.model.HookClawModel;
import cn.blockforge.generated.geargiant1211ngear.client.renderer.BrassGearChestRenderer;
import cn.blockforge.generated.geargiant1211ngear.client.renderer.GearGiantRenderer;
import cn.blockforge.generated.geargiant1211ngear.client.renderer.GearlingRenderer;
import cn.blockforge.generated.geargiant1211ngear.client.renderer.HookClawRenderer;
import cn.blockforge.generated.geargiant1211ngear.client.renderer.MechanicalHookClawRenderer;
import cn.blockforge.generated.geargiant1211ngear.client.screen.BrassGearChestScreen;
import cn.blockforge.generated.geargiant1211ngear.registry.ModEntities;
import cn.blockforge.generated.geargiant1211ngear.registry.ModItems;
import cn.blockforge.generated.geargiant1211ngear.registry.ModMenus;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ModClientEvents {

    public static final ModelLayerLocation HOOK_CLAW_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "hook_claw"), "main");

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GEAR_GIANT.get(), GearGiantRenderer::new);
        event.registerEntityRenderer(ModEntities.GEARLING.get(), GearlingRenderer::new);
        event.registerEntityRenderer(ModEntities.GEAR_PROJECTILE.get(),
                context -> new ThrownItemRenderer(context, 1.4F, false));
        event.registerEntityRenderer(ModEntities.HOOK_CLAW.get(), HookClawRenderer::new);
        event.registerEntityRenderer(ModEntities.MECHANICAL_HOOK_CLAW.get(), MechanicalHookClawRenderer::new);
        // 黄铜齿轮箱：方块实体渲染器负责画出可开合的箱盖
        event.registerBlockEntityRenderer(ModEntities.BRASS_GEAR_CHEST.get(), BrassGearChestRenderer::new);
    }

    /** 箱体与箱盖是两份独立模型，只在方块实体渲染器里用到，需显式登记才会被烘焙。 */
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(BrassGearChestRenderer.BODY_MODEL);
        event.register(BrassGearChestRenderer.LID_MODEL);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BRASS_GEAR_CHEST.get(), BrassGearChestScreen::new);
    }

    /**
     * 机械化钩爪的"出爪"物品属性（{@code gear_giant:cast}）：长按右键瞄准时置 1，
     * 物品模型随之切到 {@code mechanical_hook_cast}——原版钓鱼竿用的正是同一套 cast 变体思路。
     * 于是第三人称下"抬手瞄准"与"垂手待机"是两个姿态，物品始终像一支握在手里的机械竿，
     * 而不是一张平贴在手上的纸片。
     */
    public static void registerItemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.MECHANICAL_HOOK.get(),
                ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "cast"),
                (stack, level, holder, seed) -> holder != null && holder.isUsingItem()
                        && holder.getUseItem() == stack ? 1.0F : 0.0F));
    }

    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HOOK_CLAW_LAYER, HookClawModel::createBodyLayer);
    }

    private ModClientEvents() {
    }
}
