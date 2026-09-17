package cn.blockforge.generated.geargiant1211ngear.client.screen;

import cn.blockforge.generated.geargiant1211ngear.menu.BrassGearChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 黄铜齿轮箱界面：15 列 × 9 行的容器格子区，下方接玩家背包。
 *
 * <p>面板是逐块填充画出来的（暗钢 + 黄铜包边 + 铆钉），格子底用原版
 * {@code container/slot} 精灵，这样槽位的手感、高亮、提示都与原版容器一致。</p>
 */
@OnlyIn(Dist.CLIENT)
public class BrassGearChestScreen extends AbstractContainerScreen<BrassGearChestMenu> {
    private static final net.minecraft.resources.ResourceLocation SLOT_SPRITE =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("container/slot");

    private static final int STEEL_TOP = 0xFF4A5160;
    private static final int STEEL_BOTTOM = 0xFF2E323C;
    private static final int FRAME_DARK = 0xFF22242B;
    private static final int BRASS = 0xFFB79A4F;
    private static final int BRASS_LIT = 0xFFF0D68A;
    private static final int BRASS_DARK = 0xFF6B4E22;
    private static final int GROOVE_DARK = 0xFF1B1D23;
    private static final int TITLE_COLOR = 0xFFF2DCA4;
    private static final int LABEL_COLOR = 0xFFB9C0CC;

    public BrassGearChestScreen(BrassGearChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BrassGearChestMenu.IMAGE_WIDTH;
        this.imageHeight = BrassGearChestMenu.IMAGE_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 3;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 178;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;

        // 面板本体
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, FRAME_DARK);
        graphics.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, STEEL_TOP, STEEL_BOTTOM);

        // 黄铜包边：上亮、侧中、下暗
        graphics.fill(x, y, x + w, y + 2, BRASS_LIT);
        graphics.fill(x, y + h - 2, x + w, y + h, BRASS_DARK);
        graphics.fill(x, y, x + 2, y + h, BRASS);
        graphics.fill(x + w - 2, y, x + w, y + h, BRASS);

        // 包边内侧的暗缝，让钢板像是嵌在框里
        graphics.fill(x + 2, y + 2, x + w - 2, y + 3, GROOVE_DARK);
        graphics.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, GROOVE_DARK);
        graphics.fill(x + 2, y + 2, x + 3, y + h - 2, GROOVE_DARK);
        graphics.fill(x + w - 3, y + 2, x + w - 2, y + h - 2, GROOVE_DARK);

        // 四角铆钉
        this.rivet(graphics, x + 2, y + 2);
        this.rivet(graphics, x + w - 4, y + 2);
        this.rivet(graphics, x + 2, y + h - 4);
        this.rivet(graphics, x + w - 4, y + h - 4);

        // 容器区与背包区之间的凹槽（紧贴格子区底边）
        int gridBottom = BrassGearChestMenu.GRID_TOP + BrassGearChestMenu.ROWS * 18;
        int sepTop = y + gridBottom;
        graphics.fill(x + 4, sepTop, x + w - 4, sepTop + 1, GROOVE_DARK);
        graphics.fill(x + 4, sepTop + 1, x + w - 4, sepTop + 2, 0xFF5C6474);

        // 格子底：直接用原版槽位精灵，保证与鼠标判定完全对齐
        for (Slot slot : this.menu.slots) {
            graphics.blitSprite(SLOT_SPRITE, x + slot.x, y + slot.y, 0, 18, 18);
        }
    }

    private void rivet(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 2, y + 2, BRASS_LIT);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, BRASS_DARK);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE_COLOR, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                LABEL_COLOR, false);
    }
}
