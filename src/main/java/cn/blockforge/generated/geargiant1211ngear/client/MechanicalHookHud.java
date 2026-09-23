package cn.blockforge.generated.geargiant1211ngear.client;

import cn.blockforge.generated.geargiant1211ngear.item.MechanicalHookItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 机械化钩爪的准星 HUD：右键长按（正在使用物品）时屏蔽原版准星，
 * 换成一枚"机械瞄准环"——
 * · 搜索中（视线内没有可钩生物）：灰白色四角括号 + 中心小点；
 * · 锁定成功：括号收拢、变红，中心画十字钉点，一眼就能看出"现在松手必定勾中"。
 *
 * 客户端锁定判定与服务端出手判定共用 {@link MechanicalHookItem#findLockTarget}，
 * 保证提示与结果一致。
 */
public final class MechanicalHookHud {

    private MechanicalHookHud() {
    }

    /** 玩家是否正在用机械化钩爪瞄准。 */
    private static boolean aiming(Minecraft mc) {
        Player player = mc.player;
        return player != null && player.isUsingItem()
                && player.getUseItem().getItem() instanceof MechanicalHookItem;
    }

    /** 本帧的锁定对象（可能为 null）。 */
    private static LivingEntity lockTarget(Minecraft mc) {
        return MechanicalHookItem.findLockTarget(mc.level, mc.player, MechanicalHookItem.LOCK_RANGE);
    }

    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR) && mc.level != null && aiming(mc)) {
            event.setCanceled(true); // 用我们自己的瞄准环替换原版准星
        }
    }

    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !aiming(mc)) {
            return;
        }
        drawReticle(event.getGuiGraphics(), event.getPartialTick(), mc);
    }

    private static void drawReticle(GuiGraphics graphics, DeltaTracker delta, Minecraft mc) {
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        boolean locked = lockTarget(mc) != null;

        // 锁定瞬间括号收拢：给一点"咔哒"合拢的呼吸感
        float t = delta.getGameTimeDeltaPartialTick(false);
        int gap = locked ? 3 : 6 + (int) (Math.sin((mc.level.getGameTime() + t) * 0.35F) * 1.5F);
        int len = 5;
        int thick = locked ? 2 : 1;
        int color = locked ? 0xFFFF5050 : 0xE8FFFFFF;
        int colorDim = locked ? 0xFFFF8080 : 0xA0FFFFFF;

        // 四角括号（每边两段：横 + 竖）
        graphics.fill(cx - gap - len, cy - gap - thick, cx - gap, cy - gap, color);         // 左上横
        graphics.fill(cx - gap - thick, cy - gap - len, cx - gap, cy - gap, color);         // 左上竖
        graphics.fill(cx + gap, cy - gap - thick, cx + gap + len, cy - gap, color);         // 右上横
        graphics.fill(cx + gap, cy - gap - len, cx + gap + thick, cy - gap, color);         // 右上竖
        graphics.fill(cx - gap - len, cy + gap, cx - gap, cy + gap + thick, color);         // 左下横
        graphics.fill(cx - gap - thick, cy + gap, cx - gap, cy + gap + len, color);         // 左下竖
        graphics.fill(cx + gap, cy + gap, cx + gap + len, cy + gap + thick, color);         // 右下横
        graphics.fill(cx + gap, cy + gap, cx + gap + thick, cy + gap + len, color);         // 右下竖

        if (locked) {
            // 中心十字钉点：红色 1px 十字，明确"已咬死目标"
            graphics.fill(cx - 2, cy, cx + 3, cy + 1, color);
            graphics.fill(cx, cy - 2, cx + 1, cy + 3, color);
        } else {
            // 搜索中：中心只留一个暗一点的小点
            graphics.fill(cx, cy, cx + 1, cy + 1, colorDim);
        }
    }
}
