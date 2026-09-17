package cn.blockforge.generated.geargiant1211ngear.block;

import cn.blockforge.generated.geargiant1211ngear.registry.ModEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 黄铜齿轮箱：占用一格的 135 格（9×15，即 15 列 × 9 行）大容量容器。
 *
 * <p>渲染走方块实体渲染器（{@code BaseEntityBlock} 默认的 ENTITYBLOCK_ANIMATED），
 * 这样箱盖才能像原版箱子一样平滑开合；blockstate 指向的模型只留一份贴图给破坏粒子。</p>
 */
public class BrassGearChestBlock extends BaseEntityBlock {
    /** 模型正面（锁扣面）朝 -Z，渲染器按 180 - toYRot() 定向。 */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final MapCodec<BrassGearChestBlock> CODEC = simpleCodec(BrassGearChestBlock::new);

    public BrassGearChestBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrassGearChestBlockEntity(pos, state);
    }

    /**
     * 与原版箱子一致：不进区块网格，改由方块实体渲染器逐帧画（才能转箱盖）；
     * 同时保留破坏粒子——BaseEntityBlock 默认的 INVISIBLE 会把粒子也省掉。
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        // 只有客户端需要逐刻推进箱盖开合动画。
        return level.isClientSide() ? createTickerHelper(type, ModEntities.BRASS_GEAR_CHEST.get(),
                BrassGearChestBlockEntity::lidTick) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        MenuProvider menuProvider = this.getMenuProvider(state, level, pos);
        if (menuProvider != null) {
            player.openMenu(menuProvider);
            player.awardStat(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.OPEN_CHEST));
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof BrassGearChestBlockEntity chest ? chest : null;
    }

    // 注意：这里故意不调用 Containers.dropContentsOnDestroy。
    // 135 格一旦落地散开会炸出上百个掉落物实体，既难看又容易被烧掉；
    // 箱内内容改由战利品表的 copy_components 随箱子物品一起带走（潜影盒式）。

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BrassGearChestBlockEntity chest) {
            chest.recheckOpen();
        }
    }
}
