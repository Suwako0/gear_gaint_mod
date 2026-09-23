package cn.blockforge.generated.geargiant1211ngear.registry;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import cn.blockforge.generated.geargiant1211ngear.block.GearGiantCoreBlockEntity;
import cn.blockforge.generated.geargiant1211ngear.entity.GearGiantEntity;
import cn.blockforge.generated.geargiant1211ngear.entity.GearProjectileEntity;
import cn.blockforge.generated.geargiant1211ngear.entity.GearlingEntity;
import cn.blockforge.generated.geargiant1211ngear.entity.HookClawEntity;
import cn.blockforge.generated.geargiant1211ngear.entity.MechanicalHookClawEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, GeneratedMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, GeneratedMod.MOD_ID);

    /** 齿轮巨人 BOSS 本体。 */
    public static final DeferredHolder<EntityType<?>, EntityType<GearGiantEntity>> GEAR_GIANT =
            ENTITY_TYPES.register("gear_giant", () -> EntityType.Builder
                    .of(GearGiantEntity::new, MobCategory.MONSTER)
                    .sized(2.4F, 3.4F)
                    .eyeHeight(3.0F)
                    .fireImmune()
                    .canSpawnFarFromPlayer()
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("gear_giant"));

    /** 齿轮仆从（第二阶段召唤物）。 */
    public static final DeferredHolder<EntityType<?>, EntityType<GearlingEntity>> GEARLING =
            ENTITY_TYPES.register("gearling", () -> EntityType.Builder
                    .of(GearlingEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 1.15F)
                    .clientTrackingRange(8)
                    .build("gearling"));

    /** 齿轮弹幕投射物。 */
    public static final DeferredHolder<EntityType<?>, EntityType<GearProjectileEntity>> GEAR_PROJECTILE =
            ENTITY_TYPES.register("gear_projectile", () -> EntityType.Builder
                    .<GearProjectileEntity>of(GearProjectileEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(4)
                    .updateInterval(4)
                    .build("gear_projectile"));

    /** 机械钩爪投射物（拖拽远处玩家）。 */
    public static final DeferredHolder<EntityType<?>, EntityType<HookClawEntity>> HOOK_CLAW =
            ENTITY_TYPES.register("hook_claw", () -> EntityType.Builder
                    .<HookClawEntity>of(HookClawEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("hook_claw"));

    /** 玩家工具"机械化钩爪"的爪头投射物（与 BOSS 钩爪同款外观）。 */
    public static final DeferredHolder<EntityType<?>, EntityType<MechanicalHookClawEntity>> MECHANICAL_HOOK_CLAW =
            ENTITY_TYPES.register("mechanical_hook_claw", () -> EntityType.Builder
                    .<MechanicalHookClawEntity>of(MechanicalHookClawEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("mechanical_hook_claw"));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GearGiantCoreBlockEntity>> GEAR_CORE =
            BLOCK_ENTITY_TYPES.register("gear_giant_core",
                    () -> BlockEntityType.Builder
                            .of(GearGiantCoreBlockEntity::new, ModBlocks.GEAR_GIANT_CORE.get())
                            .build(null));

    /** 黄铜齿轮箱：9×15 大容器的方块实体。 */
    @SuppressWarnings("unchecked")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<
            cn.blockforge.generated.geargiant1211ngear.block.BrassGearChestBlockEntity>> BRASS_GEAR_CHEST =
            BLOCK_ENTITY_TYPES.register("brass_gear_chest",
                    () -> BlockEntityType.Builder
                            .of(cn.blockforge.generated.geargiant1211ngear.block.BrassGearChestBlockEntity::new,
                                    ModBlocks.BRASS_GEAR_CHEST.get())
                            .build(null));

    private ModEntities() {
    }
}
