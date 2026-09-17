package cn.blockforge.generated.geargiant1211ngear.client.renderer;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import cn.blockforge.generated.geargiant1211ngear.block.BrassGearChestBlock;
import cn.blockforge.generated.geargiant1211ngear.block.BrassGearChestBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 黄铜齿轮箱渲染器：分别画出“箱体”和“箱盖”两份烘焙模型，箱盖绕后侧上棱的铰链
 * 按 {@code getOpenNess} 旋转，开关动画与原版箱子一样平滑、且对所有人同步。
 *
 * <p>烘焙方块模型的顶点位于“以方块角点为原点”的 [0,1] 空间（模型 JSON 里的
 * 0..16 像素），而方块实体渲染器拿到的 poseStack 原点恰好也是方块角点——所以
 * 绕竖轴定向时必须 translate(0.5, 0, 0.5) → 旋转 → translate(-0.5, 0, -0.5)
 * 把转轴挪到方块中心再挪回来。若只平移不挪回（曾经的写法），整只箱子会朝
 * 右前上方斜着错开半格，贴图看着就“悬在模型位置斜上方”。</p>
 *
 * <p>模型正面做在 -Z（锁扣、齿轮纹章那一面），因此绕竖轴转 {@code 180 - toYRot()}
 * （原版箱子把正面做在 +Z，才用 {@code -toYRot()}）。铰链在模型 (8, 11, 15)，
 * 换算到 [0,1] 空间即 (任意, 0.6875, 0.9375)，绕局部 +X 正角抬起 90° 是“向上往后开”。</p>
 */
@OnlyIn(Dist.CLIENT)
public class BrassGearChestRenderer implements BlockEntityRenderer<BrassGearChestBlockEntity> {
    public static final ModelResourceLocation BODY_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "block/brass_gear_chest_body"));
    public static final ModelResourceLocation LID_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "block/brass_gear_chest_lid"));

    /** 铰链：模型里是 (8, 11, 15)，换到 [0,1] 方块空间即 (0.6875, 0.9375)。 */
    private static final float HINGE_Y = 11.0F / 16.0F;
    private static final float HINGE_Z = 15.0F / 16.0F;
    /** 完全打开时箱盖抬起 90 度。 */
    private static final float MAX_LID_ANGLE = 90.0F;

    public BrassGearChestRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BrassGearChestBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = blockEntity.getBlockState().getValue(BrassGearChestBlock.FACING);
        ModelBlockRenderer modelRenderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());
        BakedModel body = Minecraft.getInstance().getModelManager().getModel(BODY_MODEL);
        BakedModel lid = Minecraft.getInstance().getModelManager().getModel(LID_MODEL);

        // 把“绕方块中心的竖轴”旋转换算回角点原点：平移 → 旋转 → 平移回去。
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        poseStack.translate(-0.5, 0.0, -0.5);

        modelRenderer.renderModel(poseStack.last(), consumer, blockEntity.getBlockState(), body,
                1.0F, 1.0F, 1.0F, packedLight, packedOverlay, ModelData.EMPTY, RenderType.solid());

        poseStack.pushPose();
        poseStack.translate(0.0F, HINGE_Y, HINGE_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(MAX_LID_ANGLE * blockEntity.getOpenNess(partialTick)));
        poseStack.translate(0.0F, -HINGE_Y, -HINGE_Z);
        modelRenderer.renderModel(poseStack.last(), consumer, blockEntity.getBlockState(), lid,
                1.0F, 1.0F, 1.0F, packedLight, packedOverlay, ModelData.EMPTY, RenderType.solid());
        poseStack.popPose();

        poseStack.popPose();
    }

    /** 箱盖抬起时会伸出方块上界，把包围盒放大一圈，避免被视锥剔除。 */
    @Override
    public AABB getRenderBoundingBox(BrassGearChestBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(1.0, 1.0, 1.0);
    }
}
