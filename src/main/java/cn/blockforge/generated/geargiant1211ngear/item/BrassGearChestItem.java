package cn.blockforge.generated.geargiant1211ngear.item;

import cn.blockforge.generated.geargiant1211ngear.menu.BrassGearChestMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** 黄铜齿轮箱的物品形态：把容量写在提示里，免得玩家不知道这箱子有多大。 */
public class BrassGearChestItem extends BlockItem {
    public BrassGearChestItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("gear_giant.brass_gear_chest.tooltip",
                        BrassGearChestMenu.ROWS, BrassGearChestMenu.COLUMNS, BrassGearChestMenu.CHEST_SIZE)
                .withStyle(ChatFormatting.GRAY));
    }
}
