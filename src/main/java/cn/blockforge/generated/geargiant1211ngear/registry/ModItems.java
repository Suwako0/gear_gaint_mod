package cn.blockforge.generated.geargiant1211ngear.registry;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GeneratedMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GeneratedMod.MOD_ID);

    /** 召唤祭台“齿轮巨人核心”，物品提示里带完整召唤步骤（{@code GearGiantCoreItem}）。 */
    public static final DeferredItem<cn.blockforge.generated.geargiant1211ngear.item.GearGiantCoreItem>
            GEAR_GIANT_CORE = ITEMS.register("gear_giant_core",
            () -> new cn.blockforge.generated.geargiant1211ngear.item.GearGiantCoreItem(
                    ModBlocks.GEAR_GIANT_CORE.get(), new Item.Properties()));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> GEAR_TROPHY =
            ITEMS.registerSimpleBlockItem(ModBlocks.GEAR_TROPHY);
    /** 黄铜齿轮箱（9×15 大容器，15 列 × 9 行）。 */
    public static final DeferredItem<cn.blockforge.generated.geargiant1211ngear.item.BrassGearChestItem>
            BRASS_GEAR_CHEST = ITEMS.register("brass_gear_chest",
            () -> new cn.blockforge.generated.geargiant1211ngear.item.BrassGearChestItem(
                    ModBlocks.BRASS_GEAR_CHEST.get(), new Item.Properties().stacksTo(1)));

    /** BOSS 掉落的巨人动力核心（必掉），用于制作奖杯。 */
    public static final DeferredItem<Item> GEAR_GIANT_HEART = ITEMS.registerItem(
            "gear_giant_heart", Item::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant());

    public static final net.neoforged.neoforge.registries.DeferredHolder<CreativeModeTab, CreativeModeTab> GEAR_GIANT_TAB =
            TABS.register("gear_giant", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + GeneratedMod.MOD_ID + ".gear_giant"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(GEAR_GIANT_CORE.get()))
                    .displayItems((params, output) -> {
                        output.accept(GEAR_GIANT_CORE.get());
                        output.accept(GEAR_TROPHY.get());
                        output.accept(GEAR_GIANT_HEART.get());
                        output.accept(BRASS_GEAR_CHEST.get());
                    })
                    .build());

    private ModItems() {
    }
}
