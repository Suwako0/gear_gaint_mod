package cn.blockforge.generated.geargiant1211ngear.registry;

import cn.blockforge.generated.geargiant1211ngear.GeneratedMod;
import cn.blockforge.generated.geargiant1211ngear.block.GearGiantCoreBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GeneratedMod.MOD_ID);

    /** 齿轮巨人核心：输入机械动力旋转应力即可召唤 BOSS 的祭台方块。 */
    public static final DeferredBlock<GearGiantCoreBlock> GEAR_GIANT_CORE = BLOCKS.registerBlock(
            "gear_giant_core",
            GearGiantCoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(30.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** 齿轮巨人奖杯：用战利品核心制作的纪念装饰方块。 */
    public static final DeferredBlock<net.minecraft.world.level.block.Block> GEAR_TROPHY =
            BLOCKS.registerSimpleBlock("gear_trophy",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(4.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .lightLevel(state -> 5));

    /** 黄铜齿轮箱：巨人动力核心 + 黄铜板 + 坚固板合成的 9×15（135 格）大容器。 */
    public static final DeferredBlock<cn.blockforge.generated.geargiant1211ngear.block.BrassGearChestBlock>
            BRASS_GEAR_CHEST = BLOCKS.registerBlock(
            "brass_gear_chest",
            cn.blockforge.generated.geargiant1211ngear.block.BrassGearChestBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(8.0F, 24.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());

    private ModBlocks() {
    }
}
