package cn.blockforge.generated.geargiant1211ngear.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 机械钩爪模型：掌心方块 + 两片可开合的弯爪 + 尾部锁环。
 * 贴图 16x16（textures/entity/hook_claw.png），爪尖朝 -Z（飞行前方）。
 */
public class HookClawModel {
    private final ModelPart root;
    private final ModelPart jawTop;
    private final ModelPart jawBottom;

    public HookClawModel(ModelPart root) {
        this.root = root;
        this.jawTop = root.getChild("jaw_top");
        this.jawBottom = root.getChild("jaw_bottom");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition def = mesh.getRoot();
        // 掌心齿轮块
        def.addOrReplaceChild("palm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        // 上/下弯爪：向前(-Z)伸出并向下/向上弯
        def.addOrReplaceChild("jaw_top",
                CubeListBuilder.create().texOffs(0, 8).addBox(-1.5F, -1.0F, -6.0F, 3.0F, 2.0F, 6.0F)
                        .texOffs(8, 10).addBox(-1.5F, -2.5F, -10.5F, 3.0F, 2.0F, 5.0F),
                PartPose.offsetAndRotation(0.0F, -2.0F, -2.0F, -0.45F, 0.0F, 0.0F));
        def.addOrReplaceChild("jaw_bottom",
                CubeListBuilder.create().texOffs(0, 8).addBox(-1.5F, -1.0F, -6.0F, 3.0F, 2.0F, 6.0F)
                        .texOffs(8, 10).addBox(-1.5F, -2.5F, -10.5F, 3.0F, 2.0F, 5.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, -2.0F, 0.45F, 0.0F, 0.0F));
        // 尾部锁环（链条挂点）
        def.addOrReplaceChild("ring",
                CubeListBuilder.create().texOffs(8, 0).addBox(-2.0F, -2.0F, 3.0F, 4.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 16, 16);
    }

    /**
     * @param open 爪口开合度：0 = 闭合（已钩住），1 = 大张（飞行中）
     * @param spin 绕行进轴（Z）的翻滚角，制造"旋转飞出"的观感
     */
    public void animate(float open, float spin) {
        this.jawTop.xRot = -0.45F - 0.55F * open;
        this.jawBottom.xRot = 0.45F + 0.55F * open;
        this.root.zRot = spin;
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay);
    }
}
