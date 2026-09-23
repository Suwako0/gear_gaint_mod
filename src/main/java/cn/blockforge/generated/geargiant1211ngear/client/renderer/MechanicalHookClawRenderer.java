package cn.blockforge.generated.geargiant1211ngear.client.renderer;

import cn.blockforge.generated.geargiant1211ngear.client.ModClientEvents;
import cn.blockforge.generated.geargiant1211ngear.client.model.HookClawModel;
import cn.blockforge.generated.geargiant1211ngear.entity.MechanicalHookClawEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 机械化钩爪（玩家工具版）渲染器：与 BOSS 的 {@link HookClawRenderer} 同款模型与贴图，
 * 爪尖对准飞行方向、飞行中绕轴翻滚、爪口大张；钉住/收链时闭合爪口并对准主人玩家。
 */
public class MechanicalHookClawRenderer extends EntityRenderer<MechanicalHookClawEntity> {
    public static final ResourceLocation TEXTURE = HookClawRenderer.TEXTURE;

    private final HookClawModel model;

    public MechanicalHookClawRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new HookClawModel(context.bakeLayer(ModClientEvents.HOOK_CLAW_LAYER));
    }

    @Override
    public void render(MechanicalHookClawEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.35, 0.0);
        float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        // 钉住期间爪头由服务端摆位，这里补一次对准主人的朝向
        if (entity.isLatched() && entity.getOwner() instanceof Player owner) {
            Vec3 face = owner.position().add(0.0, 1.1, 0.0).subtract(entity.position());
            double horiz = Math.max(1.0E-4, Math.sqrt(face.x * face.x + face.z * face.z));
            yaw = (float) (Mth.atan2(face.x, face.z) * Mth.RAD_TO_DEG);
            pitch = (float) (-Mth.atan2(face.y, horiz) * Mth.RAD_TO_DEG);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        poseStack.scale(1.05F, 1.05F, 1.05F);
        boolean latched = entity.isLatched();
        float spin = latched ? 0.0F : (entity.tickCount + partialTicks) * 0.42F;
        this.model.animate(latched ? 0.0F : 1.0F, spin);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MechanicalHookClawEntity entity) {
        return TEXTURE;
    }
}
