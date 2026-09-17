package cn.blockforge.generated.geargiant1211ngear.block;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import cn.blockforge.generated.geargiant1211ngear.menu.BrassGearChestMenu;
import cn.blockforge.generated.geargiant1211ngear.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 黄铜齿轮箱的方块实体：135 格库存 + 一套与原版箱子一致的开盖状态机。
 *
 * <p>开盖人数由 {@link ContainerOpenersCounter} 统计，服务端通过 blockEvent(1, count)
 * 广播给周围客户端；客户端 {@link #triggerEvent} 收到后交给 {@link ChestLidController}
 * 逐刻插值，渲染器读 {@link #getOpenNess(float)} 旋转箱盖——箱盖动画因此对所有人同步，
 * 也不会在关到一半时被硬切。</p>
 */
public class BrassGearChestBlockEntity extends BaseContainerBlockEntity implements LidBlockEntity {
    /** 与原版箱子一致：1 号 blockEvent 携带“正在看箱子的人数”。 */
    private static final int EVENT_SET_OPEN_COUNT = 1;

    private NonNullList<ItemStack> items = NonNullList.withSize(BrassGearChestMenu.CHEST_SIZE, ItemStack.EMPTY);
    private final ChestLidController lidController = new ChestLidController();

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            BrassGearChestBlockEntity.this.playSound(level, pos, SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            BrassGearChestBlockEntity.this.playSound(level, pos, SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int oldCount) {
            level.blockEvent(pos, state.getBlock(), EVENT_SET_OPEN_COUNT, count);
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            if (player.containerMenu instanceof BrassGearChestMenu menu) {
                return menu.getContainer() == BrassGearChestBlockEntity.this;
            }
            return false;
        }
    };

    public BrassGearChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntities.BRASS_GEAR_CHEST.get(), pos, state);
    }

    /** 客户端逐刻推进箱盖角度。 */
    public static void lidTick(Level level, BlockPos pos, BlockState state, BrassGearChestBlockEntity be) {
        be.lidController.tickLid();
    }

    @Override
    public float getOpenNess(float partialTick) {
        return this.lidController.getOpenness(partialTick);
    }

    @Override
    public int getContainerSize() {
        return BrassGearChestMenu.CHEST_SIZE;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container." + GeneratedMod.MOD_ID + ".brass_gear_chest");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
        return new BrassGearChestMenu(id, playerInventory, this);
    }

    @Override
    public void startOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openersCounter.incrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openersCounter.decrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_SET_OPEN_COUNT) {
            this.lidController.shouldBeOpen(param > 0);
            return true;
        }
        return super.triggerEvent(id, param);
    }

    void playSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS,
                0.6F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        this.items = newItems;
    }
}
