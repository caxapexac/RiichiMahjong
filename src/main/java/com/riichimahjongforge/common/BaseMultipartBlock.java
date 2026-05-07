package com.riichimahjongforge.common;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Base for blocks that occupy a 3×3 horizontal multiblock footprint with a single
 * master cell at the centre. The master owns the {@link net.minecraft.world.level.block.entity.BlockEntity}
 * and renders the model; other parts render invisible and exist only to provide
 * collision for overhanging geometry.
 *
 * <p>Subclasses provide their model bounding-boxes (in master-cell-relative model
 * pixels) and the BE factory + ticker. Placement, breakage, render gating, and
 * per-cell shape carving are handled here.
 *
 * <p>Override {@link #isRotatable()} to opt into {@link BlockStateProperties#HORIZONTAL_FACING}.
 * When enabled: facing is set on placement to the side toward the player, propagated
 * to all parts, and collision shapes are pre-rotated for each facing around the master
 * cell's vertical axis. The corresponding model rotation must be wired in the
 * blockstate JSON (multipart with {@code "y": 0/90/180/270}) so the visual matches.
 */
@SuppressWarnings("deprecation")
public abstract class BaseMultipartBlock extends BaseEntityBlock {

    /** Side length of the multiblock footprint in blocks. */
    public static final int GRID_SIZE = 3;
    /** Index of the master cell on each axis (also the radius from master to any edge). */
    public static final int CENTER = GRID_SIZE / 2;
    /** A Minecraft block in model-space pixels. */
    protected static final double BLOCK_PX = 16.0;

    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, GRID_SIZE - 1);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, GRID_SIZE - 1);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final List<Direction> HORIZONTAL = List.of(
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    private final Map<Direction, VoxelShape[]> partShapesByFacing;

    /**
     * @param modelBoxes tabletop AABBs in model-space pixels, master-cell-relative.
     *                   Each entry is {@code {x1, y1, z1, x2, y2, z2}}.
     */
    protected BaseMultipartBlock(Properties properties, List<double[]> modelBoxes) {
        super(properties);
        this.partShapesByFacing = buildShapesByFacing(modelBoxes);
        BlockState defaults = defaultBlockState().setValue(PART_X, CENTER).setValue(PART_Z, CENTER);
        if (isRotatable()) {
            defaults = defaults.setValue(FACING, Direction.NORTH);
        }
        registerDefaultState(defaults);
    }

    /** Override and return {@code true} to opt into placement-direction rotation. */
    protected boolean isRotatable() {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART_X, PART_Z);
        if (isRotatable()) {
            builder.add(FACING);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isMaster(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return shapeFor(state);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos center = ctx.getClickedPos();
        for (int dx = -CENTER; dx <= CENTER; dx++) {
            for (int dz = -CENTER; dz <= CENTER; dz++) {
                BlockPos p = center.offset(dx, 0, dz);
                if (!level.getBlockState(p).canBeReplaced(ctx)) {
                    return null;
                }
            }
        }
        BlockState s = defaultBlockState().setValue(PART_X, CENTER).setValue(PART_Z, CENTER);
        if (isRotatable()) {
            s = s.setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }
        return s;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !isMaster(state)) {
            return;
        }
        for (int px = 0; px < GRID_SIZE; px++) {
            for (int pz = 0; pz < GRID_SIZE; pz++) {
                if (px == CENTER && pz == CENTER) {
                    continue;
                }
                BlockPos p = pos.offset(px - CENTER, 0, pz - CENTER);
                level.setBlock(p, state.setValue(PART_X, px).setValue(PART_Z, pz), 3);
            }
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return isMaster(state) ? super.getDrops(state, builder) : List.of();
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos master = masterPos(pos, state);
            if (!master.equals(pos) && level.getBlockState(master).is(this)) {
                level.destroyBlock(master, !player.isCreative(), player);
                clearRemainingParts(level, master);
                super.playerWillDestroy(level, pos, state, player);
                return;
            }
            clearRemainingParts(level, master);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    public static boolean isMaster(BlockState state) {
        return state.getValue(PART_X) == CENTER && state.getValue(PART_Z) == CENTER;
    }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        return pos.offset(CENTER - state.getValue(PART_X), 0, CENTER - state.getValue(PART_Z));
    }

    private void clearRemainingParts(Level level, BlockPos masterPos) {
        for (int dx = -CENTER; dx <= CENTER; dx++) {
            for (int dz = -CENTER; dz <= CENTER; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos p = masterPos.offset(dx, 0, dz);
                if (level.getBlockState(p).is(this)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                }
            }
        }
    }

    private VoxelShape shapeFor(BlockState state) {
        Direction facing = isRotatable() ? state.getValue(FACING) : Direction.NORTH;
        VoxelShape[] arr = partShapesByFacing.get(facing);
        int idx = state.getValue(PART_X) + state.getValue(PART_Z) * GRID_SIZE;
        return idx >= 0 && idx < arr.length ? arr[idx] : Shapes.empty();
    }

    private static Map<Direction, VoxelShape[]> buildShapesByFacing(List<double[]> modelBoxes) {
        Map<Direction, VoxelShape[]> map = new EnumMap<>(Direction.class);
        for (Direction d : HORIZONTAL) {
            List<double[]> rotated = rotateBoxes(modelBoxes, rotIndex(d));
            map.put(d, buildPartShapes(rotated));
        }
        return map;
    }

    /** NORTH = 0 (default model orientation), EAST/SOUTH/WEST = 1/2/3 quarter-turns CW. */
    private static int rotIndex(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** Rotate a list of {@code {x1,y1,z1,x2,y2,z2}} boxes by {@code k} quarter-turns CW
     *  around the master-cell vertical axis at {@code (BLOCK_PX/2, *, BLOCK_PX/2)}. */
    private static List<double[]> rotateBoxes(List<double[]> boxes, int k) {
        if ((k & 3) == 0) {
            return boxes;
        }
        List<double[]> out = new ArrayList<>(boxes.size());
        for (double[] b : boxes) {
            out.add(rotateBoxY(b, k));
        }
        return out;
    }

    private static double[] rotateBoxY(double[] b, int k) {
        double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
        return switch (k & 3) {
            case 0 -> b;
            case 1 -> new double[] {BLOCK_PX - z2, y1, x1, BLOCK_PX - z1, y2, x2};
            case 2 -> new double[] {BLOCK_PX - x2, y1, BLOCK_PX - z2, BLOCK_PX - x1, y2, BLOCK_PX - z1};
            case 3 -> new double[] {z1, y1, BLOCK_PX - x2, z2, y2, BLOCK_PX - x1};
            default -> b;
        };
    }

    private static VoxelShape[] buildPartShapes(List<double[]> modelBoxes) {
        VoxelShape[] out = new VoxelShape[GRID_SIZE * GRID_SIZE];
        for (int px = 0; px < GRID_SIZE; px++) {
            for (int pz = 0; pz < GRID_SIZE; pz++) {
                out[px + pz * GRID_SIZE] = buildShapeForPart(modelBoxes, px, pz);
            }
        }
        return out;
    }

    private static VoxelShape buildShapeForPart(List<double[]> modelBoxes, int partX, int partZ) {
        double cellMinX = (partX - CENTER) * BLOCK_PX;
        double cellMinZ = (partZ - CENTER) * BLOCK_PX;
        double cellMaxX = cellMinX + BLOCK_PX;
        double cellMaxZ = cellMinZ + BLOCK_PX;

        ArrayList<VoxelShape> shapes = new ArrayList<>();
        for (double[] b : modelBoxes) {
            double ix1 = Math.max(b[0], cellMinX);
            double iz1 = Math.max(b[2], cellMinZ);
            double ix2 = Math.min(b[3], cellMaxX);
            double iz2 = Math.min(b[5], cellMaxZ);
            if (ix2 <= ix1 || iz2 <= iz1 || b[4] <= b[1]) {
                continue;
            }
            shapes.add(box(ix1 - cellMinX, b[1], iz1 - cellMinZ, ix2 - cellMinX, b[4], iz2 - cellMinZ));
        }
        if (shapes.isEmpty()) {
            return Shapes.empty();
        }
        VoxelShape s = shapes.get(0);
        for (int i = 1; i < shapes.size(); i++) {
            s = Shapes.or(s, shapes.get(i));
        }
        return s;
    }
}
