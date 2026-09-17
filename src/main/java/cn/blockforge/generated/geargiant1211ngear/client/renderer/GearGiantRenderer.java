package cn.blockforge.generated.geargiant1211ngear.client.renderer;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import cn.blockforge.generated.geargiant1211ngear.client.model.GearGiantModel;
import cn.blockforge.generated.geargiant1211ngear.entity.GearGiantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GearGiantRenderer extends MobRenderer<GearGiantEntity, GearGiantModel> {
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "textures/entity/gear_giant.png");

    public GearGiantRenderer(EntityRendererProvider.Context context) {
        // 阴影半径与 2.4 格宽的巨人身体匹配，避免"脚下无影"造成的浮空感
        super(context, new GearGiantModel(GearGiantModel.createBodyLayer().bakeRoot()), 1.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(GearGiantEntity entity) {
        return TEXTURE;
    }
}
