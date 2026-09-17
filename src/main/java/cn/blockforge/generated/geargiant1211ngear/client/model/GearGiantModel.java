package cn.blockforge.generated.geargiant1211ngear.client.model;

import cn.blockforge.generated.geargiant1211ngear.entity.GearGiantAnim;
import cn.blockforge.generated.geargiant1211ngear.entity.GearGiantEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 齿轮巨人模型：安山岩双腿、黄铜躯干、胸口外露的旋转齿轮、肩部排气管与红石眼头部。
 * 动画由两部分驱动：
 * - 待机/行走（呼吸起伏、摆臂、头部环视）按 limbSwing/ageInTicks 计算；
 * - 招式姿势（{@link GearGiantAnim}，服务端同步）在基准姿态上覆盖关键帧，
 *   姿势切换帧记录 ageInTicks 作为动画时基，保证与服务端状态时长一致。
 */
public class GearGiantModel extends HierarchicalModel<GearGiantEntity> {
    private static final float DEG = Mth.DEG_TO_RAD;
    private static final float BODY_Y = -4.0F;

    private final ModelPart root;
    private final ModelPart legLeft;
    private final ModelPart legRight;
    private final ModelPart body;
    private final ModelPart chestGear;
    private final ModelPart backGear;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart head;
    private final ModelPart stackLeft;
    private final ModelPart stackRight;

    private int trackedPose = GearGiantAnim.NONE;
    private float poseStartAge = 0.0F;
    private float lastAge = Float.NaN;
    private float gearAngle;
    private float backGearAngle;

    public GearGiantModel(ModelPart root) {
        this.root = root;
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
        this.body = root.getChild("body");
        this.chestGear = this.body.getChild("chest_gear");
        this.backGear = this.body.getChild("back_gear");
        this.armLeft = this.body.getChild("arm_left");
        this.armRight = this.body.getChild("arm_right");
        this.head = this.body.getChild("head");
        this.stackLeft = this.body.getChild("stack_left");
        this.stackRight = this.body.getChild("stack_right");
    }

    /**
     * 128x128 贴图 UV 分区（互不重叠，1 像素 = 1 模型单位）：
     * body(0,0,84,32) leg(84,0,128,40) foot(0,32,56,54) arm(56,40,96,76)
     * head(0,54,52,76) pad(0,76,48,94) chest_gear(48,76,90,97) back_gear(90,76,122,92)
     * stack(0,94,16,108) eye_l(16,94,26,98) eye_r(26,94,36,98)
     *
     * 坐标系（与原版铁傀儡/玩家一致，勿再改动）：Mojang 的 LivingEntityRenderer
     * 在渲染模型前做 scale(-1,-1,1) + translate(0,-1.501,0)，因此
     * 世界高度 worldY = 1.501 - modelY/16.0625，**脚底必须落在 modelY=24**，
     * 模型向上为 modelY 递减方向。若把脚底放在 modelY=0，整个模型会整体上浮
     * 1.5 格——这就是此前"浮空、只有上半身有贴图"的根因。
     * 模型总高 54px = 3.375 格，与 3.4 格碰撞箱对齐。
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        // 腿：轴心在髋部（离地 28px，即 modelY=-4），小腿 28px + 前伸的脚掌 6px，
        // 脚底严格落在 modelY=24（贴地）
        rootDef.addOrReplaceChild("leg_left",
                CubeListBuilder.create()
                        .texOffs(84, 0).addBox(-5.0F, 0.0F, -6.0F, 10.0F, 28.0F, 12.0F)
                        .texOffs(0, 32).addBox(-6.0F, 22.0F, -9.0F, 12.0F, 6.0F, 16.0F),
                PartPose.offset(6.0F, -4.0F, 0.0F));
        rootDef.addOrReplaceChild("leg_right",
                CubeListBuilder.create()
                        .texOffs(84, 0).mirror().addBox(-5.0F, 0.0F, -6.0F, 10.0F, 28.0F, 12.0F).mirror(false)
                        .texOffs(0, 32).mirror().addBox(-6.0F, 22.0F, -9.0F, 12.0F, 6.0F, 16.0F).mirror(false),
                PartPose.offset(-6.0F, -4.0F, 0.0F));

        // 躯干：离地 28~44px（modelY -20~-4）
        rootDef.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-13.0F, -16.0F, -8.0F, 26.0F, 16.0F, 16.0F),
                PartPose.offset(0.0F, -4.0F, 0.0F));

        bodyDef(rootDef);

        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void bodyDef(PartDefinition rootDef) {
        PartDefinition body = rootDef.getChild("body");
        // 胸前齿轮：外露圆盘，绕 Z 轴旋转
        body.addOrReplaceChild("chest_gear",
                CubeListBuilder.create().texOffs(48, 76).addBox(-9.0F, -9.0F, -1.5F, 18.0F, 18.0F, 3.0F),
                PartPose.offset(0.0F, -8.0F, -8.5F));
        body.addOrReplaceChild("back_gear",
                CubeListBuilder.create().texOffs(90, 76).addBox(-7.0F, -7.0F, -1.0F, 14.0F, 14.0F, 2.0F),
                PartPose.offset(0.0F, -8.0F, 8.5F));
        // 手臂：轴心在肩部，含肩甲；下垂到 18px 高
        body.addOrReplaceChild("arm_left",
                CubeListBuilder.create()
                        .texOffs(56, 40).addBox(-1.0F, -2.0F, -5.0F, 10.0F, 26.0F, 10.0F)
                        .texOffs(0, 76).addBox(-2.0F, -4.0F, -6.0F, 12.0F, 6.0F, 12.0F),
                PartPose.offset(14.0F, -14.0F, 0.0F));
        body.addOrReplaceChild("arm_right",
                CubeListBuilder.create()
                        .texOffs(56, 40).mirror().addBox(-9.0F, -2.0F, -5.0F, 10.0F, 26.0F, 10.0F).mirror(false)
                        .texOffs(0, 76).mirror().addBox(-10.0F, -4.0F, -6.0F, 12.0F, 6.0F, 12.0F).mirror(false),
                PartPose.offset(-14.0F, -14.0F, 0.0F));
        // 头：44~54px 高，正面朝 -Z，两只红石眼
        body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 54).addBox(-7.0F, -10.0F, -6.0F, 14.0F, 10.0F, 12.0F)
                        .texOffs(16, 94).addBox(-4.5F, -7.0F, -7.5F, 3.0F, 2.0F, 2.0F)
                        .texOffs(26, 94).addBox(1.5F, -7.0F, -7.5F, 3.0F, 2.0F, 2.0F),
                PartPose.offset(0.0F, -16.0F, 0.0F));
        // 肩部排气管：44~54px 高
        body.addOrReplaceChild("stack_left",
                CubeListBuilder.create().texOffs(0, 94).addBox(-2.0F, -10.0F, -2.0F, 4.0F, 10.0F, 4.0F),
                PartPose.offset(9.0F, -16.0F, 4.0F));
        body.addOrReplaceChild("stack_right",
                CubeListBuilder.create().texOffs(0, 94).addBox(-2.0F, -10.0F, -2.0F, 4.0F, 10.0F, 4.0F),
                PartPose.offset(-9.0F, -16.0F, 4.0F));
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(GearGiantEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        int pose = entity != null ? entity.getAnimPose() : GearGiantAnim.NONE;
        if (pose != this.trackedPose) {
            this.trackedPose = pose;
            this.poseStartAge = ageInTicks;
        }
        float pt = ageInTicks - this.poseStartAge;   // 当前姿势已经过的刻数
        float t = GearGiantAnim.progress(pt, pose);  // 0~1 动画进度

        float spin = entity != null ? entity.getGearSpinSpeed() : 0.12F;
        boolean staggered = entity != null && entity.isStaggered();

        // ---------------------------------------------------------------- 基准姿态（呼吸 / 行进步态）
        float bob = Mth.sin(ageInTicks * 0.055F) * 0.25F;
        float stride = Mth.clamp(limbSwingAmount, 0.0F, 1.2F);
        float stepSin = Mth.sin(limbSwing * 0.6662F);
        float stepCos = Mth.cos(limbSwing * 0.6662F);
        // 一个完整步频内身体起伏两次，越跑颠得越沉
        float walkBob = Mth.abs(Mth.sin(limbSwing * 1.3324F)) * 0.55F * stride;

        this.root.zRot = 0.0F;
        this.root.yRot = 0.0F;

        this.body.y = BODY_Y - bob - walkBob;
        this.body.xRot = 0.0F;
        this.body.yRot = 0.0F;
        // 移动惯性俯仰：加速前扑、减速被顶得后仰（用上刻位移推算，客户端也稳定）
        if (entity != null) {
            double dx = entity.getX() - entity.xo;
            double dz = entity.getZ() - entity.zo;
            float yawRad = entity.getYRot() * DEG;
            double ahead = -dx * Mth.sin(yawRad) + dz * Mth.cos(yawRad);
            this.body.xRot += Mth.clamp((float) ahead * 1.1F, -0.06F, 0.1F);
        }
        // 重心随左右落脚交替侧倾
        this.body.zRot = stepCos * 0.055F * stride;

        this.legLeft.xRot = stepCos * 0.62F * stride;
        this.legRight.xRot = -this.legLeft.xRot;
        // 迈步时脚尖外八，重机械踩地的感觉
        this.legLeft.zRot = 0.04F + stepSin * 0.05F * stride;
        this.legRight.zRot = -0.04F - stepSin * 0.05F * stride;
        this.legLeft.yRot = 0.0F;
        this.legRight.yRot = 0.0F;

        float armSway = Mth.sin(ageInTicks * 0.06F) * 0.07F;
        this.armLeft.xRot = armSway - this.legLeft.xRot * 0.8F;
        this.armRight.xRot = -armSway - this.legRight.xRot * 0.8F;
        this.armLeft.yRot = 0.0F;
        this.armRight.yRot = 0.0F;
        this.armLeft.zRot = 0.05F - this.body.zRot * 1.6F;
        this.armRight.zRot = -0.05F - this.body.zRot * 1.6F;

        // 头部反向侧倾稳住视线
        this.head.xRot = headPitch * DEG + Mth.sin(ageInTicks * 0.07F) * 0.02F;
        this.head.yRot = netHeadYaw * DEG;
        this.head.zRot = -this.body.zRot * 0.6F;

        this.stackLeft.xRot = Mth.sin(ageInTicks * 0.09F) * 0.05F;
        this.stackRight.xRot = -this.stackLeft.xRot;
        this.stackLeft.zRot = 0.0F;
        this.stackRight.zRot = 0.0F;

        // 脱战待机时头部缓慢环视
        if (pose == GearGiantAnim.NONE && limbSwingAmount < 0.08F && !staggered) {
            this.head.yRot += Mth.sin(ageInTicks * 0.021F) * 0.3F;
            this.head.xRot += Mth.sin(ageInTicks * 0.033F) * 0.08F;
            this.body.yRot = Mth.sin(ageInTicks * 0.017F) * 0.06F;
        }

        // ---------------------------------------------------------------- 招式姿势
        switch (pose) {
            case GearGiantAnim.MELEE_WINDUP -> {
                float e = GearGiantAnim.easeOut(t);
                this.armLeft.xRot = Mth.lerp(e, this.armLeft.xRot, -2.45F);
                this.armRight.xRot = Mth.lerp(e, this.armRight.xRot, -2.45F);
                this.armLeft.zRot = Mth.lerp(e, 0.05F, -0.24F);
                this.armRight.zRot = Mth.lerp(e, -0.05F, 0.24F);
                this.body.xRot = -0.14F * e;
                this.body.yRot = 0.42F * e;                   // 拧腰蓄力
                this.body.y += 0.5F * e;
                this.head.xRot = Mth.lerp(e, this.head.xRot, -0.15F);
                this.head.yRot = netHeadYaw * DEG + 0.3F * e;
                this.legLeft.zRot = 0.2F * e;                 // 下盘扎稳
                this.legRight.zRot = -0.2F * e;
                this.stackLeft.xRot = -0.4F * e;
                this.stackRight.xRot = -0.4F * e;
            }
            case GearGiantAnim.MELEE_STRIKE -> {
                float e = GearGiantAnim.easeOut(t);
                // 命中帧之后加一小段"过冲回弹"，砸击更有分量
                float snap = Mth.sin(Mth.clamp((t - 0.5F) / 0.5F, 0.0F, 1.0F) * Mth.PI) * 0.2F;
                this.armLeft.xRot = Mth.lerp(e, -2.45F, 1.95F) + snap;
                this.armRight.xRot = Mth.lerp(e, -2.45F, 1.95F) + snap;
                this.armLeft.zRot = Mth.lerp(e, -0.24F, -0.5F);
                this.armRight.zRot = Mth.lerp(e, 0.24F, 0.5F);
                this.body.xRot = Mth.lerp(e, -0.14F, 0.55F);
                this.body.yRot = Mth.lerp(e, 0.42F, -0.5F);   // 拧腰释放，砸击带转身
                this.body.y += Mth.lerp(e, 0.5F, 1.6F);
                this.head.xRot = Mth.lerp(e, -0.15F, 0.45F);
                this.head.yRot = netHeadYaw * DEG + Mth.lerp(e, 0.3F, -0.4F);
                this.legLeft.xRot = -0.25F * e;
                this.legRight.xRot = 0.35F * e;
                this.legLeft.zRot = 0.2F - 0.1F * e;
                this.legRight.zRot = -0.2F + 0.1F * e;
                this.root.zRot = -Mth.sin(Mth.clamp((t - 0.5F) / 0.5F, 0.0F, 1.0F) * Mth.PI * 2.0F) * 0.014F;
            }
            case GearGiantAnim.MELEE_SWEEP -> {
                // 前 30% 把右臂收到身后，后 70% 横摆甩出，避免姿势跳帧
                float wind = GearGiantAnim.easeOut(Mth.clamp(t / 0.3F, 0.0F, 1.0F));
                float sweep = GearGiantAnim.easeInOut(Mth.clamp((t - 0.3F) / 0.7F, 0.0F, 1.0F));
                this.body.yRot = Mth.lerp(sweep, Mth.lerp(wind, 0.0F, 0.55F), -0.75F);
                this.body.xRot = 0.12F;
                this.armRight.xRot = Mth.lerp(sweep, Mth.lerp(wind, 1.95F, -0.4F), -0.15F);
                this.armRight.zRot = Mth.lerp(sweep, Mth.lerp(wind, 0.5F, -1.9F), 1.15F);
                this.armLeft.xRot = Mth.lerp(sweep, 1.95F, 0.35F);
                this.armLeft.zRot = Mth.lerp(sweep, 0.1F, 0.5F);  // 左臂护在胸前配重
                this.legLeft.xRot = 0.2F * sweep;
                this.legRight.xRot = -0.15F * sweep;
                this.legLeft.zRot = Mth.lerp(sweep, 0.05F, 0.22F);
                this.legRight.zRot = -this.legLeft.zRot;
                this.head.yRot = netHeadYaw * DEG + Mth.lerp(sweep, 0.3F, -0.6F);
                this.body.y += 0.4F * GearGiantAnim.pulse(t, 1.0F);
            }
            case GearGiantAnim.CHARGE_WINDUP -> {
                float e = GearGiantAnim.easeOut(t);
                this.body.xRot = 0.38F * e;
                this.body.y += 1.5F * e;
                this.armLeft.xRot = Mth.lerp(e, this.armLeft.xRot, 1.35F);
                this.armRight.xRot = Mth.lerp(e, this.armRight.xRot, 1.35F);
                this.armLeft.zRot = 0.25F * e;
                this.armRight.zRot = -0.25F * e;
                this.legLeft.xRot = 0.45F * e;
                this.legRight.xRot = 0.45F * e;
                this.legLeft.zRot = 0.16F * e;
                this.legRight.zRot = -0.16F * e;
                this.head.xRot = 0.35F * e;
                this.stackLeft.xRot = -0.5F * e;
                this.stackRight.xRot = -0.5F * e;
                this.root.zRot = Mth.sin(pt * 1.7F) * 0.016F * e;   // 引擎轰鸣的震颤
            }
            case GearGiantAnim.CHARGE_RUSH -> {
                float flutter = Mth.sin(pt * 1.1F);
                this.body.xRot = 0.6F + Mth.sin(pt * 0.55F) * 0.04F;  // 引擎节律的起伏
                this.body.y += 0.5F + Mth.abs(flutter) * 0.25F;        // 每一步都踩得整机弹起
                this.body.yRot = Mth.sin(pt * 0.35F) * 0.06F;
                this.armLeft.xRot = 2.1F + flutter * 0.08F;
                this.armRight.xRot = 2.1F - flutter * 0.08F;
                this.armLeft.zRot = 0.35F - flutter * 0.06F;
                this.armRight.zRot = -0.35F + flutter * 0.06F;
                this.legLeft.xRot = flutter * 0.75F;
                this.legRight.xRot = -flutter * 0.75F;
                this.head.xRot = 0.45F - Mth.sin(pt * 0.9F) * 0.03F;
                this.stackLeft.xRot = -0.9F;
                this.stackRight.xRot = -0.9F;
                this.root.zRot = Mth.sin(pt * 0.6F) * 0.02F + Mth.sin(pt * 2.3F) * 0.008F;
            }
            case GearGiantAnim.LEAP_CROUCH -> {
                float e = GearGiantAnim.easeInOut(t);
                this.body.y += 3.6F * e;
                this.body.xRot = -0.12F * e;
                this.legLeft.xRot = 0.6F * e;
                this.legRight.xRot = 0.6F * e;
                this.legLeft.zRot = 0.14F * e;
                this.legRight.zRot = -0.14F * e;
                this.armLeft.xRot = Mth.lerp(e, this.armLeft.xRot, -2.5F);
                this.armRight.xRot = Mth.lerp(e, this.armRight.xRot, -2.5F);
                this.armLeft.zRot = -0.3F * e;
                this.armRight.zRot = 0.3F * e;
                this.head.xRot = Mth.lerp(e, this.head.xRot, -0.2F);
                this.root.zRot = Mth.sin(pt * 2.2F) * 0.012F * e;   // 起跳前压簧抖动
            }
            case GearGiantAnim.LEAP_AIR -> {
                // 身体俯仰跟着弹道走：上升抬头、下落俯冲
                net.minecraft.world.phys.Vec3 vel =
                        entity != null ? entity.getDeltaMovement() : net.minecraft.world.phys.Vec3.ZERO;
                double horiz = Math.max(0.05, Math.sqrt(vel.x * vel.x + vel.z * vel.z));
                float arc = Mth.clamp((float) Mth.atan2(vel.y, horiz), -0.7F, 0.7F);
                this.body.y = BODY_Y - 1.2F;
                this.body.xRot = -arc * 0.55F;
                this.legLeft.xRot = -0.55F;
                this.legRight.xRot = 0.65F;
                this.legLeft.zRot = 0.18F;
                this.legRight.zRot = -0.18F;
                float armSwing = vel.y > 0.0 ? -2.6F : 0.5F;   // 升空高举、下落前摆
                this.armLeft.xRot = armSwing;
                this.armRight.xRot = armSwing;
                this.armLeft.zRot = -0.4F;
                this.armRight.zRot = 0.4F;
                this.head.xRot = -arc * 0.8F;
                this.stackLeft.xRot = -0.9F - arc * 0.4F;
                this.stackRight.xRot = -0.9F - arc * 0.4F;
            }
            case GearGiantAnim.LEAP_LAND -> {
                float c = GearGiantAnim.pulse(t, 0.45F); // 快速下蹲后缓慢回弹
                this.body.y = BODY_Y + 3.5F * c;
                this.body.xRot = 0.5F * c;
                this.legLeft.xRot = 0.9F * c;
                this.legRight.xRot = 0.9F * c;
                this.legLeft.zRot = 0.22F * c;
                this.legRight.zRot = -0.22F * c;
                this.armLeft.xRot = Mth.lerp(c, -2.6F, 1.4F);
                this.armRight.xRot = Mth.lerp(c, -2.6F, 1.4F);
                this.armLeft.zRot = -0.9F * c;
                this.armRight.zRot = 0.9F * c;
                this.head.xRot = 0.5F * c;
                this.root.zRot = (1.0F - t) * Mth.sin(pt * 1.6F) * 0.03F;
            }
            case GearGiantAnim.SPIN -> {
                float e = GearGiantAnim.easeInOut(t);
                float extend = GearGiantAnim.easeOut(Mth.clamp(t / 0.18F, 0.0F, 1.0F)); // 起手先展臂再起飞
                this.root.yRot = -(float) (Math.PI * 2.0) * 2.0F * e;   // 整机自转两圈
                this.armLeft.xRot = Mth.sin(pt * 0.7F) * 0.15F * extend;
                this.armRight.xRot = -this.armLeft.xRot;
                this.armLeft.zRot = (1.62F + Mth.sin(pt * 1.4F) * 0.08F) * extend; // 平展的双臂随风颤动
                this.armRight.zRot = -this.armLeft.zRot;
                this.body.yRot = Mth.sin(pt * 0.8F) * 0.12F;
                this.body.zRot = Mth.sin(pt * 0.4F) * 0.05F * extend;
                this.body.y = BODY_Y - 0.4F * extend - Mth.abs(Mth.sin(pt * 0.8F)) * 0.4F * extend;
                this.legLeft.xRot = Mth.sin(pt * 0.8F) * 0.35F * extend;
                this.legRight.xRot = -this.legLeft.xRot;
                this.stackLeft.zRot = 0.3F * extend;
                this.stackRight.zRot = -0.3F * extend;
                this.head.yRot = this.head.yRot - this.root.yRot; // 头部保持在真实朝向上
            }
            case GearGiantAnim.BARRAGE -> {
                // 每 12 刻一轮齐射：按波次交替抬臂，逐发后坐
                float wave = (pt % 12.0F) / 12.0F;
                float recoil = GearGiantAnim.pulse(wave, 0.4F) * 0.3F;
                boolean rightSide = ((int) (pt / 12.0F) & 1) == 0;
                this.armRight.xRot = rightSide ? -1.45F + recoil : -0.6F;
                this.armRight.zRot = rightSide ? -0.3F : 0.1F;
                this.armLeft.xRot = rightSide ? -0.6F : -1.45F + recoil;
                this.armLeft.zRot = rightSide ? 0.1F : 0.3F;
                this.body.yRot = Mth.sin(pt * 0.75F) * 0.2F;   // 上身随弹幕左右横扫
                this.body.zRot = Mth.sin(pt * 1.5F) * 0.03F;   // 连发的轻微震动
                this.legLeft.zRot = 0.16F;                     // 架稳射击姿态
                this.legRight.zRot = -0.16F;
                this.stackLeft.xRot = -0.4F;
                this.stackRight.xRot = -0.4F;
            }
            case GearGiantAnim.STEAM -> {
                float e = GearGiantAnim.easeOut(t);
                this.armLeft.xRot = Mth.lerp(e, this.armLeft.xRot, -2.6F);
                this.armRight.xRot = Mth.lerp(e, this.armRight.xRot, -2.6F);
                this.armLeft.zRot = -0.55F * e;
                this.armRight.zRot = 0.55F * e;
                this.head.xRot = -0.45F * e;
                this.body.xRot = -0.12F * e;
                this.body.y -= 0.5F * e;
                this.stackLeft.xRot = -0.7F * e;               // 排气管外张泄压
                this.stackRight.xRot = -0.7F * e;
                this.stackLeft.zRot = -0.5F * e;
                this.stackRight.zRot = 0.5F * e;
                this.legLeft.xRot = Mth.sin(pt * 2.0F) * 0.05F;
                this.legRight.xRot = -this.legLeft.xRot;
                this.legLeft.zRot = 0.25F * e;                 // 马步抗冲击
                this.legRight.zRot = -0.25F * e;
                this.root.zRot = Mth.sin(pt * 2.6F) * 0.02F * e;
            }
            case GearGiantAnim.BEAM_WARM -> {
                float e = GearGiantAnim.easeOut(t);
                this.armRight.xRot = Mth.lerp(e, this.armRight.xRot, -1.75F) + Mth.sin(pt * 0.9F) * 0.05F * e;
                this.armRight.zRot = -0.25F * e;
                this.armLeft.xRot = Mth.lerp(e, this.armLeft.xRot, 1.0F);
                this.armLeft.zRot = 0.3F * e;
                this.body.xRot = 0.15F * e;
                this.body.yRot = Mth.sin(pt * 0.5F) * 0.08F * e;  // 炮口缓慢寻的
                this.head.xRot = headPitch * DEG * 1.6F;
                this.stackLeft.xRot = -0.35F * e;
                this.stackRight.xRot = -0.35F * e;
            }
            case GearGiantAnim.BEAM_FIRE -> {
                this.armRight.xRot = -1.95F + Mth.sin(pt * 1.7F) * 0.05F;
                this.armRight.zRot = -0.18F;
                this.armLeft.xRot = 0.9F;
                this.armLeft.zRot = 0.35F;
                this.body.xRot = 0.22F + Mth.sin(pt * 1.1F) * 0.02F;
                this.body.yRot = Mth.sin(pt * 0.45F) * 0.1F;      // 光束横扫追踪
                this.body.y += 0.3F;                            // 被后坐力顶得后坐
                this.head.xRot = headPitch * DEG * 1.6F + Mth.sin(pt * 2.3F) * 0.02F;
                this.legLeft.xRot = 0.15F;
                this.legRight.xRot = -0.1F;
                this.stackLeft.xRot = -0.55F;
                this.stackRight.xRot = -0.55F;
                this.stackLeft.zRot = -0.2F;
                this.stackRight.zRot = 0.2F;
            }
            case GearGiantAnim.PULL -> {
                float close = GearGiantAnim.easeInOut(t);
                float saw = Mth.sin(pt * 1.1F) * 0.12F;        // 传送带绞动的节律
                this.armLeft.xRot = -1.9F + saw;
                this.armRight.xRot = -1.9F - saw;
                this.armLeft.zRot = 0.45F - 0.35F * close;      // 十指逐渐合拢
                this.armRight.zRot = -(0.45F - 0.35F * close);
                this.body.xRot = -0.08F + 0.18F * close;
                this.body.yRot = Mth.sin(pt * 0.55F) * 0.1F;
                this.body.y = this.body.y - 0.3F + 0.6F * close;
                this.legLeft.xRot = 0.2F;
                this.legRight.xRot = 0.2F;
                this.head.xRot = 0.25F;
            }
            case GearGiantAnim.STOMP -> {
                float lift = GearGiantAnim.pulse(t, 0.5F);       // 抬腿-跺下-回位
                float dip = GearGiantAnim.pulse(Mth.clamp((t - 0.35F) / 0.45F, 0.0F, 1.0F), 1.0F) * 1.8F;
                float shock = Mth.clamp((t - 0.5F) / 0.5F, 0.0F, 1.0F); // 落地后的余震
                this.legRight.xRot = -1.1F * lift + 0.3F * shock * Mth.sin(shock * 9.0F);
                this.legRight.yRot = 0.15F * lift;
                this.legLeft.xRot = -0.25F * lift;
                this.body.y = this.body.y - 1.0F * lift + dip;
                this.body.xRot = -0.2F * lift + 0.3F * (dip * 0.5F);
                this.body.zRot = Mth.sin(shock * Mth.PI * 2.0F) * 0.03F * (1.0F - shock);
                this.armLeft.xRot = -0.5F * lift;
                this.armLeft.zRot = 0.9F * lift;                // 抬臂保持平衡
                this.armRight.xRot = -0.5F * lift;
                this.armRight.zRot = -0.9F * lift;
                this.head.xRot = 0.3F * lift;
                this.root.zRot = Mth.sin(pt * 1.9F) * 0.012F * lift
                        + Mth.sin(shock * Mth.PI * 3.0F) * 0.02F * (1.0F - shock);
            }
            case GearGiantAnim.HOOK_WINDUP -> {
                float e = GearGiantAnim.easeOut(t);
                float tremble = Mth.sin(pt * 2.6F) * 0.04F * e;      // 爪仓张合的震颤
                this.body.yRot = -0.6F * e;                           // 大幅侧身，亮出右肩
                this.body.xRot = 0.18F * e;
                this.body.y += 0.4F * e;
                // 右臂像掷标枪一样拉过头顶后方，链索已绷紧
                this.armRight.xRot = Mth.lerp(e, this.armRight.xRot, 1.55F) + tremble;
                this.armRight.zRot = Mth.lerp(e, this.armRight.zRot, -0.55F);
                this.armRight.yRot = -0.35F * e;
                // 左臂前伸虚指目标，压住整条链线
                this.armLeft.xRot = Mth.lerp(e, this.armLeft.xRot, -0.95F);
                this.armLeft.zRot = Mth.lerp(e, this.armLeft.zRot, 0.25F);
                this.head.yRot = netHeadYaw * DEG - 0.5F * e;         // 全程死盯猎物
                this.head.xRot = headPitch * DEG * 1.3F - 0.1F * e;
                // 弓步蓄力：右脚在前、左脚蹬地
                this.legRight.xRot = -0.28F * e;
                this.legLeft.xRot = 0.32F * e;
                this.legLeft.zRot = 0.14F * e;
                this.legRight.zRot = -0.1F * e;
                this.stackLeft.xRot = -0.35F * e;
                this.stackRight.xRot = -0.6F * e;                     // 右侧排气管前倾泄压
                this.root.zRot = Mth.sin(pt * 3.3F) * 0.012F * e;
            }
            case GearGiantAnim.HOOK_THROW -> {
                // 前 60% 加速挥臂（easeIn 越甩越快），后 40% 顺势落定
                float e = t < 0.6F ? (float) Math.pow(t / 0.6F, 1.7) : 1.0F;
                float follow = Mth.clamp((t - 0.6F) / 0.4F, 0.0F, 1.0F);
                this.body.yRot = Mth.lerp(e, -0.6F, 0.42F);           // 全身拧转带动甩臂
                this.body.xRot = Mth.lerp(e, 0.18F, 0.34F);
                this.body.y += Mth.lerp(e, 0.4F, -0.5F);              // 出手瞬间重心沉下去
                this.armRight.xRot = Mth.lerp(e, 1.55F, -1.25F)
                        + Mth.sin(follow * Mth.PI) * 0.2F;            // 脱手帧的过冲回弹
                this.armRight.zRot = Mth.lerp(e, -0.55F, 0.1F);
                this.armRight.yRot = Mth.lerp(e, -0.35F, 0.15F);
                this.armLeft.xRot = Mth.lerp(e, -0.95F, 0.6F);        // 左臂收拢配重
                this.armLeft.zRot = Mth.lerp(e, 0.25F, 0.5F);
                this.head.yRot = netHeadYaw * DEG + Mth.lerp(e, -0.5F, 0.1F);
                this.head.xRot = headPitch * DEG + Mth.lerp(e, -0.1F, 0.3F); // 目光追着爪头
                this.legRight.xRot = Mth.lerp(e, -0.28F, 0.12F);      // 后脚蹬地踢起
                this.legLeft.xRot = Mth.lerp(e, 0.32F, -0.35F);       // 前脚跨步落地
                this.root.zRot = -0.02F * e;
                this.stackRight.xRot = Mth.lerp(e, -0.6F, 0.25F);     // 后坐把排气管顶得倒吹
            }
            case GearGiantAnim.HOOK_PULL -> {
                float e = GearGiantAnim.easeOut(Math.min(1.0F, t * 3.0F));
                float phase = pt * 0.8F;
                float left = Mth.sin(phase);                          // 双臂交替倒链
                float right = Mth.sin(phase + Mth.PI);
                this.armLeft.xRot = Mth.lerp(e, this.armLeft.xRot, -1.05F + left * 0.55F);
                this.armRight.xRot = Mth.lerp(e, this.armRight.xRot, -1.05F + right * 0.55F);
                this.armLeft.zRot = Mth.lerp(e, this.armLeft.zRot, 0.26F - Math.max(0.0F, left) * 0.2F);
                this.armRight.zRot = Mth.lerp(e, this.armRight.zRot, -0.26F - Math.max(0.0F, right) * 0.2F);
                this.body.yRot = right * 0.16F * e;                   // 肩背随收链交替甩
                this.body.xRot = Mth.lerp(e, this.body.xRot, -0.3F - Math.max(0.0F, right) * 0.07F);
                this.body.y += Mth.lerp(e, 0.0F, 0.8F) + Math.max(left, right) * 0.35F;
                this.legLeft.xRot = Mth.lerp(e, this.legLeft.xRot, 0.45F);
                this.legRight.xRot = Mth.lerp(e, this.legRight.xRot, -0.32F);
                this.legLeft.zRot = 0.18F;
                this.legRight.zRot = -0.18F;
                this.head.xRot = Mth.lerp(e, this.head.xRot, 0.4F + Mth.sin(phase * 2.0F) * 0.04F);
                this.stackLeft.xRot = -0.55F * e;
                this.stackRight.xRot = -0.55F * e;
                this.root.zRot = Mth.sin(phase * 2.0F + 1.2F) * 0.01F * e; // 用力时的整体震颤
            }
            default -> {
            }
        }

        // ---------------------------------------------------------------- 硬直覆盖（转阶段时最优先）
        if (staggered) {
            this.root.zRot = Mth.sin(ageInTicks * 0.9F) * 0.06F;
            this.body.xRot = 0.2F + Mth.sin(ageInTicks * 0.4F) * 0.03F;
            this.body.y += 1.2F;                                 // 瘫沉地矮下半截
            this.head.xRot = 0.55F;
            this.head.zRot = Mth.sin(ageInTicks * 0.7F) * 0.06F; // 脑袋无力地晃
            this.armLeft.xRot = 1.1F + Mth.sin(ageInTicks * 0.5F) * 0.1F;
            this.armRight.xRot = 1.1F - Mth.sin(ageInTicks * 0.5F) * 0.1F;
            this.armLeft.zRot = 0.4F;                            // 双臂垮开
            this.armRight.zRot = -0.4F;
            this.legLeft.xRot = 0.25F;                           // 膝盖打软
            this.legRight.xRot = 0.25F;
            this.stackLeft.xRot = 0.3F;
            this.stackRight.xRot = 0.3F;
        }

        // ---------------------------------------------------------------- 齿轮旋转（按刻累积，变速不跳帧）
        float dt = Float.isNaN(this.lastAge) ? 0.0F : Mth.clamp(ageInTicks - this.lastAge, 0.0F, 1.0F);
        this.lastAge = ageInTicks;
        this.gearAngle += spin * dt;
        this.backGearAngle -= spin * 0.75F * dt;
        this.chestGear.zRot = this.gearAngle;
        this.backGear.zRot = this.backGearAngle;
    }
}
