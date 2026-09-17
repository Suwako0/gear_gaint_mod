package cn.blockforge.generated.geargiant1211ngear.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import cn.blockforge.generated.geargiant1211ngear.entity.GearlingEntity;

/** 齿轮仆从模型：一枚直立旋转的黄铜齿轮。 */
public class GearlingModel extends HierarchicalModel<GearlingEntity> {
    private final ModelPart root;
    private final ModelPart gear;

    public GearlingModel(ModelPart root) {
        this.root = root;
        this.gear = root.getChild("gear");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();
        // Mojang 渲染映射 worldY = 1.501 - modelY/16.0625：脚底须落在 modelY=24。
        // 齿轮 14px 高，中心放 modelY=17，则底缘 24（贴地）、顶缘 10（离地 0.875 格）。
        rootDef.addOrReplaceChild("gear",
                CubeListBuilder.create().texOffs(0, 34).addBox(-7.0F, -7.0F, -1.5F, 14.0F, 14.0F, 3.0F),
                PartPose.offset(0.0F, 17.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(GearlingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        float moving = Math.abs(limbSwingAmount);
        this.gear.zRot = ageInTicks * (0.15F + moving * 0.4F);
        this.gear.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.5F;
    }
}
