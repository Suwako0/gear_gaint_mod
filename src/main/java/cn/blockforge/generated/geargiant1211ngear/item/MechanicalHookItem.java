package cn.blockforge.generated.geargiant1211ngear.item;

import cn.blockforge.generated.geargiant1211ngear.entity.GearGiantEntity;
import cn.blockforge.generated.geargiant1211ngear.entity.MechanicalHookClawEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 机械化钩爪（玩家工具）：右键长按进入瞄准，视线内 64 格若有可钩生物则锁定
 * （准星样式改变作提示）；松开右键朝锁定生物射出钩爪（与齿轮巨人 BOSS 同款爪头），
 * 命中后把对方一路拽到自己面前。若松开时没有锁定任何生物，钩爪只是向前飞出再收回，
 * 是一段"未勾中"的空放动作。
 *
 * 客户端（准星提示）与服务端（出手选人）共用 {@link #findLockTarget}，
 * 判定完全一致，不会出现"准星说锁上了、松手却没勾到人"的错位。
 */
public class MechanicalHookItem extends Item {
    /** 索敌半径（格）：与 BOSS 钩爪的 64 格硬上限同一约定。 */
    public static final int LOCK_RANGE = 64;
    /** 命中出手后的冷却（刻）。 */
    private static final int COOLDOWN_HIT = 200;
    /** 空放出手后的冷却（刻）。 */
    private static final int COOLDOWN_MISS = 80;
    /** 锁定锥角：cos(8°)，准星偏离目标超过 8 度不算"看着它"。 */
    private static final double MIN_DOT = 0.990D;
    /** 总耐久：出手即扣，用完即碎。 */
    public static final int MAX_DAMAGE = 325;
    /** 勾中并拖拽一次的耐久损耗。 */
    private static final int DURABILITY_HIT = 3;
    /** 未锁定空放一次的耐久损耗。 */
    private static final int DURABILITY_MISS = 1;

    public MechanicalHookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000; // 可以一直按住，直到玩家松手
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        LivingEntity target = findLockTarget(level, player, LOCK_RANGE);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            MechanicalHookClawEntity.fire(serverLevel, player, target);
            // 耐久损耗：勾中拖拽扣 3 点、空放扣 1 点；扣穿即碎，由原版逻辑负责播报与销毁
            stack.hurtAndBreak(target != null ? DURABILITY_HIT : DURABILITY_MISS,
                    player, player.getEquipmentSlotForItem(stack));
        }
        // 两侧都加冷却：客户端做预测，准星与出手节奏和服务端保持一致
        player.getCooldowns().addCooldown(this, target != null ? COOLDOWN_HIT : COOLDOWN_MISS);
    }

    // ---------------------------------------------------------------- 锁定判定（两侧共用）

    /**
     * 选出当前准星锁定的生物：玩家视线 64 格内、方块视野不遮挡、准星夹角 8° 以内，
     * 取"最贴近准星中心"的一个（角度并列时取更近的）。客户端 HUD 与服务端出手都调它。
     */
    public static LivingEntity findLockTarget(Level level, Player player, double range) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1.0E-6) {
            return null;
        }
        AABB area = player.getBoundingBox().inflate(range);
        LivingEntity best = null;
        double bestDot = MIN_DOT;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area,
                t -> hookable(t, player))) {
            Vec3 center = e.position().add(0.0, e.getBbHeight() * 0.5 + 0.1, 0.0);
            Vec3 to = center.subtract(eye);
            double dist = to.length();
            if (dist < 1.0 || dist > range) {
                continue;
            }
            double dot = to.dot(look) / dist;
            if (dot < bestDot) {
                continue;
            }
            // 视线遮挡检查：眼睛到对方身体中心不能被方块截断
            if (level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
                continue;
            }
            boolean better = dot > bestDot + 1.0E-4
                    || (Math.abs(dot - bestDot) <= 1.0E-4 && dist < bestDist);
            if (better) {
                bestDot = dot;
                bestDist = dist;
                best = e;
            }
        }
        return best;
    }

    /** 可钩对象：活着的生物，排除玩家、护甲架与齿轮巨人（BOSS 不可被拽）。 */
    private static boolean hookable(LivingEntity e, Player player) {
        return e != player
                && e.isAlive()
                && !e.isRemoved()
                && !(e instanceof Player)
                && !(e instanceof ArmorStand)
                && !(e instanceof GearGiantEntity)
                && !(e.isSpectator() || e.isInvulnerable());
    }

    // ---------------------------------------------------------------- 提示

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("gear_giant.hook_tool.desc.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gear_giant.hook_tool.desc.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gear_giant.hook_tool.desc.3").withStyle(ChatFormatting.GRAY));
    }
}
