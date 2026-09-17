package cn.blockforge.generated.geargiant1211ngear.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 齿轮巨人核心：给它相邻的动力轴输入旋转（应力），充能完成后在上方召唤齿轮巨人。
 */
public class GearGiantCoreBlock extends BaseEntityBlock {
    public static final BooleanProperty CHARGING = BooleanProperty.create("charging");

    public static final MapCodec<GearGiantCoreBlock> CODEC = simpleCodec(GearGiantCoreBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(
            box(1.0, 12.0, 1.0, 15.0, 16.0, 15.0),
            box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0));

    public GearGiantCoreBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CHARGING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(CHARGING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GearGiantCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, cn.blockforge.generated.geargiant1211ngear.registry.ModEntities.GEAR_CORE.get(),
                        GearGiantCoreBlockEntity::serverTick);
    }
}
