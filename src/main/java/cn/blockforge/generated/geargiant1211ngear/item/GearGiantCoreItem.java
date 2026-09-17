package cn.blockforge.generated.geargiant1211ngear.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 齿轮巨人核心（召唤祭台）的物品形态：把完整的召唤步骤写进悬停提示，
 * 玩家不用翻说明书就知道怎么唤醒 BOSS。
 */
public class GearGiantCoreItem extends BlockItem {
    public GearGiantCoreItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("gear_giant.core.desc.header").withStyle(ChatFormatting.GOLD));
        for (int i = 1; i <= 4; i++) {
            tooltip.add(Component.translatable("gear_giant.core.desc." + i).withStyle(ChatFormatting.GRAY));
        }
    }
}
