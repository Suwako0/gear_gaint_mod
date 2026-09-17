package cn.blockforge.generated.geargiant1211ngear.menu;

import cn.blockforge.generated.geargiant1211ngear.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 黄铜齿轮箱的容器菜单：15 列 × 9 行 = 135 格，下方再排玩家自己的 27 + 9 格。
 *
 * <p>槽位坐标是公开常量，界面 {@code BrassGearChestScreen} 直接按同一套数值画，
 * 保证“看到的格子”和“点得到的格子”永远对齐。</p>
 */
public class BrassGearChestMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 15;
    public static final int ROWS = 9;
    /** 9×15 共 135 格。 */
    public static final int CHEST_SIZE = COLUMNS * ROWS;

    // ---- 界面布局（菜单与界面共用）----
    /** 8 + 15 列 × 18 + 8 = 286 像素宽。 */
    public static final int IMAGE_WIDTH = 8 + COLUMNS * 18 + 8;
    public static final int IMAGE_HEIGHT = 270;
    public static final int GRID_LEFT = 8;
    public static final int GRID_TOP = 14;
    /** 玩家背包（9 列）在面板里水平居中。 */
    public static final int INV_LEFT = (IMAGE_WIDTH - 9 * 18) / 2;
    public static final int INV_TOP = 188;
    public static final int HOTBAR_TOP = 246;

    private final Container container;

    /** 服务端：绑定真实箱子库存。 */
    public BrassGearChestMenu(int id, Inventory playerInventory, Container container) {
        super(ModMenus.BRASS_GEAR_CHEST.get(), id);
        checkContainerSize(container, CHEST_SIZE);
        this.container = container;
        container.startOpen(playerInventory.player);
        this.addChestSlots(container);
        this.addPlayerSlots(playerInventory);
    }

    /** 客户端：槽位内容由服务端逐格同步，本地只放一个空容器占位。 */
    public BrassGearChestMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(CHEST_SIZE));
    }

    private void addChestSlots(Container container) {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.addSlot(new Slot(container, row * COLUMNS + column,
                        GRID_LEFT + column * 18, GRID_TOP + row * 18));
            }
        }
    }

    private void addPlayerSlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        INV_LEFT + column * 18, INV_TOP + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, INV_LEFT + column * 18, HOTBAR_TOP));
        }
    }

    public Container getContainer() {
        return this.container;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack source = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            source = stack.copy();
            if (slotIndex < CHEST_SIZE) {
                // 箱子里 -> 玩家背包（优先热键栏）
                if (!this.moveItemStackTo(stack, CHEST_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, CHEST_SIZE, false)) {
                // 玩家背包 -> 箱子（从空位开始填）
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return source;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
