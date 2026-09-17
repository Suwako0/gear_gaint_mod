package cn.blockforge.generated.geargiant1211ngear.registry;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import cn.blockforge.generated.geargiant1211ngear.menu.BrassGearChestMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 容器菜单类型注册：黄铜齿轮箱的 9×15 大容器界面。 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, GeneratedMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<BrassGearChestMenu>> BRASS_GEAR_CHEST =
            MENU_TYPES.register("brass_gear_chest", () -> IMenuTypeExtension.create(
                    (id, playerInventory, buffer) -> new BrassGearChestMenu(id, playerInventory)));

    private ModMenus() {
    }
}
