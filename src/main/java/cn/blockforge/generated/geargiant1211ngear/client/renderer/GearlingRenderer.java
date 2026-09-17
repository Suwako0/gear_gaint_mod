package cn.blockforge.generated.geargiant1211ngear.client.renderer;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import cn.blockforge.generated.geargiant1211ngear.client.model.GearlingModel;
import cn.blockforge.generated.geargiant1211ngear.entity.GearlingEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GearlingRenderer extends MobRenderer<GearlingEntity, GearlingModel> {
    /** 仆从是 64x64 的独立贴图，与巨人 128x128 的 UV 布局不通用。 */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "textures/entity/gearling.png");

    public GearlingRenderer(EntityRendererProvider.Context context) {
        super(context, new GearlingModel(GearlingModel.createBodyLayer().bakeRoot()), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(GearlingEntity entity) {
        return TEXTURE;
    }
}
