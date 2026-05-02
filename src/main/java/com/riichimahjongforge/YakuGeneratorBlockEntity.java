package com.riichimahjongforge;

import com.mahjongcore.*;
import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.mahjongcore.hands.Hands;
import com.mahjongcore.tile.Tile;
import com.mahjongcore.tile.TileType;
import com.mahjongcore.yaku.normals.NormalYaku;
import org.slf4j.Logger;

public class YakuGeneratorBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int TILE_SLOTS = 14;
    public static final int TILE_KIND_COUNT = 34;
    public static final int TILE_POOL_SIZE = 136;
    public static final int MAX_ENERGY = 100_000_000;
    public static final int HAN_FOR_YAKUMAN = 13;
    private static final double NO_YAKU_EFFECT_RADIUS = 4.5;

    private static final String TAG_TILES = "YgTiles";
    private static final String TAG_ENERGY = "YgEnergy";
    private static final String TAG_GEN_TICKS = "YgTicks";
    private static final String TAG_GEN_RFPT = "YgRfpt";
    private static final String TAG_LAST_HAN = "YgLastHan";
    private static final String TAG_LAST_RFPT = "YgLastRfpt";
    private static final String TAG_LAST_DURATION = "YgLastDur";
    private static final String TAG_LAST_YAKUMAN = "YgLastYakuman";
    private static final String TAG_REMAINING = "YgRemain";
    private static final String TAG_DRAWS_USED = "YgDrawsUsed";
    private static final String TAG_AUTO_SORT = "YgAutoSort";

    private final int[] tileCodes = new int[TILE_SLOTS];
    private final int[] remainingCopies = new int[TILE_KIND_COUNT];
    private int drawsUsed;

    private int energyStored;
    private int generationTicksRemaining;
    private int currentRfPerTick;
    private int lastHan;
    private int lastRfPerTick;
    private int lastDurationTicks;
    private boolean lastYakuman;
    private boolean autoSortOnReroll = true;

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = Math.max(0, Math.min(maxExtract, energyStored));
            if (!simulate && extracted > 0) {
                energyStored -= extracted;
                setChanged();
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return energyStored;
        }

        @Override
        public int getMaxEnergyStored() {
            return MAX_ENERGY;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    private final LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energyStorage);

    public YakuGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(RiichiMahjongForgeMod.YAKU_GENERATOR_BLOCK_ENTITY.get(), pos, state);
        setupNewRound(RandomSource.create());
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide() && !isRoundDataValid()) {
            setupNewRound(level.random);
            setChanged();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, YakuGeneratorBlockEntity be) {
        be.tickServer();
    }

    private void tickServer() {
        boolean changed = false;
        if (generationTicksRemaining > 0 && currentRfPerTick > 0) {
            int accepted = addEnergy(currentRfPerTick);
            if (accepted > 0) {
                changed = true;
            }
            generationTicksRemaining--;
            changed = true;
            if (generationTicksRemaining <= 0) {
                generationTicksRemaining = 0;
                currentRfPerTick = 0;
            }
        }
        if (energyStored > 0 && pushEnergyToNeighbors() > 0) {
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    public void resetMachine() {
        if (level == null || level.isClientSide()) {
            return;
        }
        setupNewRound(level.random);
        generationTicksRemaining = 0;
        currentRfPerTick = 0;
        lastHan = 0;
        lastRfPerTick = 0;
        lastDurationTicks = 0;
        lastYakuman = false;
        setChanged();
    }

    public void rerollSlot(int slot) {
        if (level == null || level.isClientSide() || slot < 0 || slot >= TILE_SLOTS) {
            return;
        }
        if (isRoundExhausted()) {
            setupNewRound(level.random);
            setChanged();
            return;
        }
        int draw = drawTileCodeFromRemaining(level.random);
        if (draw < 0) {
            setupNewRound(level.random);
            setChanged();
            return;
        }
        tileCodes[slot] = draw;
        drawsUsed++;
        if (autoSortOnReroll) {
            Arrays.sort(tileCodes);
        }
        if (level instanceof ServerLevel sl) {
            playTilePlaceSound(sl);
        }
        setChanged();
    }

    public void tsumo(ServerPlayer actor) {
        if (level == null || level.isClientSide() || actor == null) {
            return;
        }
        applyTsumoResult(actor, evaluateCurrentHand(), true);
    }

    public void debugTriggerOutcome(ServerPlayer actor, int outcomeIndex) {
        if (level == null || level.isClientSide() || actor == null) {
            return;
        }
        TsumoResult result = switch (outcomeIndex) {
            case 0 -> TsumoResult.noWin();
            case 1 -> new TsumoResult(3, false, List.of("RIICHI"), List.of());
            case 2 -> new TsumoResult(5, false, List.of("HONITSU"), List.of());
            case 3 -> new TsumoResult(7, false, List.of("CHINITSU"), List.of());
            case 4 -> new TsumoResult(9, false, List.of("SANANKO"), List.of());
            case 5 -> new TsumoResult(11, false, List.of("JUNCHAN"), List.of());
            case 6 -> new TsumoResult(HAN_FOR_YAKUMAN, true, List.of("DAISANGEN"), List.of("DAISANGEN"));
            default -> null;
        };
        if (result == null) {
            return;
        }
        applyTsumoResult(actor, result, false);
    }

    private void applyTsumoResult(ServerPlayer actor, TsumoResult result, boolean setupNewRoundAfterResolution) {
        lastHan = result.han;
        lastYakuman = result.yakuman;
        if (level instanceof ServerLevel sl) {
            MinecraftForge.EVENT_BUS.post(new MahjongRoundResolvedEvent(sl, worldPosition, lastHan));
        }
        if (result.han <= 0) {
            triggerNoYakuBacklash(actor);
            generationTicksRemaining = 0;
            currentRfPerTick = 0;
            lastRfPerTick = 0;
            lastDurationTicks = 0;
            if (setupNewRoundAfterResolution || isRoundExhausted()) {
                setupNewRound(level.random);
            }
            setChanged();
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            MahjongWinEffects.playWinEffects(
                    serverLevel,
                    actor,
                    actor.getName().getString(),
                    result.han(),
                    result.yakuman(),
                    result.yakuNames(),
                    result.yakumanNames());
        }
        Reward reward = rewardForHan(result.han, result.yakuman);
        currentRfPerTick = reward.rfPerTick;
        generationTicksRemaining = reward.durationTicks;
        lastRfPerTick = reward.rfPerTick;
        lastDurationTicks = reward.durationTicks;
        if (setupNewRoundAfterResolution) {
            setupNewRound(level.random);
        }
        setChanged();
    }

    private void triggerNoYakuBacklash(ServerPlayer actor) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.5;
        double z = worldPosition.getZ() + 0.5;
        serverLevel.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0f, 1.0f);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        applyNoYakuBlastDamageAndKnockback(serverLevel, actor, new Vec3(x, y, z));
        if (serverLevel.getServer() != null) {
            serverLevel.getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(
                            Component.literal(actor.getName().getString() + " has no Yaku, what a loser"),
                            false);
        }
    }

    private void applyNoYakuBlastDamageAndKnockback(ServerLevel serverLevel, ServerPlayer actor, Vec3 center) {
        AABB area = new AABB(center, center).inflate(NO_YAKU_EFFECT_RADIUS);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator())) {
            double dx = entity.getX() - center.x;
            double dy = entity.getY(0.5) - center.y;
            double dz = entity.getZ() - center.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > NO_YAKU_EFFECT_RADIUS) {
                continue;
            }
            double strength = 1.0 - (distance / NO_YAKU_EFFECT_RADIUS);
            float damage = (float) (8.0 + (strength * 28.0));
            // Explosion visuals can set hurt cooldown; reset so the punishment damage always lands.
            entity.invulnerableTime = 0;
            entity.hurt(serverLevel.damageSources().generic(), damage);

            Vec3 pushDir;
            if (distance < 1.0e-4) {
                pushDir = new Vec3(0.0, 1.0, 0.0);
            } else {
                pushDir = new Vec3(dx / distance, 0.2, dz / distance).normalize();
            }
            double horizontalPush = 2.6 * strength + 0.6;
            double verticalPush = 0.7 * strength + 0.25;
            entity.push(pushDir.x * horizontalPush, verticalPush, pushDir.z * horizontalPush);
            entity.hurtMarked = true;
        }
    }

    public int getTileCode(int slot) {
        if (slot < 0 || slot >= TILE_SLOTS) {
            return 0;
        }
        return tileCodes[slot];
    }

    public int getEnergyStored() {
        return energyStored;
    }

    public int getGenerationTicksRemaining() {
        return generationTicksRemaining;
    }

    public int getCurrentRfPerTick() {
        return currentRfPerTick;
    }

    public int getLastHan() {
        return lastHan;
    }

    public int getLastRfPerTick() {
        return lastRfPerTick;
    }

    public int getLastDurationTicks() {
        return lastDurationTicks;
    }

    public boolean isLastYakuman() {
        return lastYakuman;
    }

    public int getDrawLimit() {
        return resolveTier().drawLimit();
    }

    public int getDrawsUsed() {
        return drawsUsed;
    }

    public int getDrawsRemaining() {
        return Math.max(0, getDrawLimit() - drawsUsed);
    }

    public int getTierIndex() {
        return resolveTier().index();
    }

    public boolean isAutoSortOnReroll() {
        return autoSortOnReroll;
    }

    public void toggleAutoSortOnReroll() {
        if (level == null || level.isClientSide()) {
            return;
        }
        autoSortOnReroll = !autoSortOnReroll;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray(TAG_TILES, tileCodes);
        tag.putIntArray(TAG_REMAINING, remainingCopies);
        tag.putInt(TAG_DRAWS_USED, drawsUsed);
        tag.putInt(TAG_ENERGY, energyStored);
        tag.putInt(TAG_GEN_TICKS, generationTicksRemaining);
        tag.putInt(TAG_GEN_RFPT, currentRfPerTick);
        tag.putInt(TAG_LAST_HAN, lastHan);
        tag.putInt(TAG_LAST_RFPT, lastRfPerTick);
        tag.putInt(TAG_LAST_DURATION, lastDurationTicks);
        tag.putBoolean(TAG_LAST_YAKUMAN, lastYakuman);
        tag.putBoolean(TAG_AUTO_SORT, autoSortOnReroll);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] loadedTiles = tag.getIntArray(TAG_TILES);
        if (loadedTiles.length == TILE_SLOTS) {
            System.arraycopy(loadedTiles, 0, tileCodes, 0, TILE_SLOTS);
        } else {
            Arrays.fill(tileCodes, 0);
        }
        int[] loadedRemaining = tag.getIntArray(TAG_REMAINING);
        if (loadedRemaining.length == TILE_KIND_COUNT) {
            System.arraycopy(loadedRemaining, 0, remainingCopies, 0, TILE_KIND_COUNT);
        } else {
            Arrays.fill(remainingCopies, 0);
        }
        drawsUsed = Math.max(0, tag.getInt(TAG_DRAWS_USED));
        energyStored = clamp(tag.getInt(TAG_ENERGY), 0, MAX_ENERGY);
        generationTicksRemaining = Math.max(0, tag.getInt(TAG_GEN_TICKS));
        currentRfPerTick = Math.max(0, tag.getInt(TAG_GEN_RFPT));
        lastHan = Math.max(0, tag.getInt(TAG_LAST_HAN));
        lastRfPerTick = Math.max(0, tag.getInt(TAG_LAST_RFPT));
        lastDurationTicks = Math.max(0, tag.getInt(TAG_LAST_DURATION));
        lastYakuman = tag.getBoolean(TAG_LAST_YAKUMAN);
        autoSortOnReroll = tag.contains(TAG_AUTO_SORT) ? tag.getBoolean(TAG_AUTO_SORT) : true;
        if (!isRoundDataValid()) {
            RandomSource random = level != null ? level.random : RandomSource.create();
            setupNewRound(random);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
    }

    private int addEnergy(int amount) {
        int accepted = Math.max(0, Math.min(amount, MAX_ENERGY - energyStored));
        if (accepted > 0) {
            energyStored += accepted;
        }
        return accepted;
    }

    private int pushEnergyToNeighbors() {
        if (level == null || level.isClientSide() || energyStored <= 0) {
            return 0;
        }
        int totalSent = 0;
        for (Direction direction : Direction.values()) {
            if (energyStored <= 0) {
                break;
            }
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbor == null) {
                continue;
            }
            LazyOptional<IEnergyStorage> targetCap = neighbor.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if (!targetCap.isPresent()) {
                continue;
            }
            IEnergyStorage target = targetCap.orElse(null);
            if (target == null || !target.canReceive()) {
                continue;
            }
            int offer = energyStored;
            int accepted = target.receiveEnergy(offer, true);
            if (accepted <= 0) {
                continue;
            }
            int sent = target.receiveEnergy(Math.min(offer, accepted), false);
            if (sent > 0) {
                energyStored -= sent;
                totalSent += sent;
            }
        }
        return totalSent;
    }

    private void setupNewRound(RandomSource random) {
        Arrays.fill(remainingCopies, 4);
        for (int i = 0; i < TILE_SLOTS; i++) {
            int draw = drawTileCodeFromRemaining(random);
            tileCodes[i] = draw >= 0 ? draw : 0;
        }
        drawsUsed = 0;
    }

    private int drawTileCodeFromRemaining(RandomSource random) {
        int total = remainingWallTiles();
        if (total <= 0) {
            return -1;
        }
        int pick = random.nextInt(total);
        for (int code = 0; code < TILE_KIND_COUNT; code++) {
            int count = remainingCopies[code];
            if (count <= 0) {
                continue;
            }
            if (pick < count) {
                remainingCopies[code] = count - 1;
                return code;
            }
            pick -= count;
        }
        return -1;
    }

    private int remainingWallTiles() {
        int total = 0;
        for (int count : remainingCopies) {
            total += Math.max(0, count);
        }
        return total;
    }

    private boolean isRoundExhausted() {
        return drawsUsed >= getDrawLimit() || remainingWallTiles() <= 0;
    }

    private boolean isRoundDataValid() {
        if (drawsUsed < 0 || drawsUsed > getDrawLimit()) {
            return false;
        }
        for (int code : tileCodes) {
            if (code < 0 || code >= TILE_KIND_COUNT) {
                return false;
            }
        }
        for (int count : remainingCopies) {
            if (count < 0 || count > 4) {
                return false;
            }
        }
        return true;
    }

    private void playTilePlaceSound(ServerLevel sl) {
        float pitch = 0.95f + (sl.random.nextFloat() * 0.1f);
        sl.playSound(
                null,
                worldPosition,
                RiichiMahjongForgeMod.TILE_PLACE_SOUND.get(),
                SoundSource.BLOCKS,
                0.65f,
                pitch);
    }

    private YakuGeneratorBlock.Tier resolveTier() {
        if (getBlockState().getBlock() instanceof YakuGeneratorBlock block) {
            return block.tier();
        }
        return YakuGeneratorBlock.Tier.TIER_2;
    }

    private TsumoResult evaluateCurrentHand() {
        int[] counts = new int[TILE_KIND_COUNT];
        for (int code : tileCodes) {
            if (code >= 0 && code < TILE_KIND_COUNT) {
                counts[code]++;
            }
        }
        Tile last = Tile.valueOf(clamp(tileCodes[TILE_SLOTS - 1], 0, TILE_KIND_COUNT - 1));
        try {
            Hands hands = new Hands(counts, last);
            if (!hands.getCanWin()) {
                return TsumoResult.noWin();
            }
            MahjongGeneralSituation general = new MahjongGeneralSituation();
            general.setFirstRound(false);
            general.setHoutei(false);
            general.setBakaze(Tile.TON);
            general.setDora(List.of());
            general.setUradora(List.of());
            MahjongPersonalSituation personal = new MahjongPersonalSituation(true, false, false, false, false, false, Tile.TON);
            MahjongPlayer player = new MahjongPlayer(hands, general, personal);
            player.calculate();
            boolean yakuman = !player.getYakumanList().isEmpty();
            if (yakuman) {
                List<String> yakumanNames = player.getYakumanList().stream()
                        .map(Object::toString)
                        .toList();
                return new TsumoResult(HAN_FOR_YAKUMAN, true, yakumanNames, yakumanNames);
            }
            int han = Math.max(0, Math.min(HAN_FOR_YAKUMAN - 1, player.getHan()));
            if (han <= 0) {
                return TsumoResult.noWin();
            }
            List<String> yakuNames = player.getNormalYakuList().stream()
                    .map(NormalYaku::name)
                    .toList();
            return new TsumoResult(han, false, yakuNames, List.of());
        } catch (MahjongHandsOverFlowException | MahjongTileOverFlowException | MahjongIllegalMentsuSizeException |
                 RuntimeException ignored) {
            return TsumoResult.noWin();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable unexpected) {
            LOGGER.warn("Yaku generator tsumo evaluation failed; treating as no-win hand", unexpected);
            return TsumoResult.noWin();
        }
    }

    private Reward rewardForHan(int han, boolean yakuman) {
        if (yakuman || han >= HAN_FOR_YAKUMAN) {
            return new Reward(100_000, 20 * 60);
        }
        if (han <= 0) {
            return new Reward(0, 0);
        }
        double progress = (double) (han - 1) / (double) (HAN_FOR_YAKUMAN - 1);
        int rfPerTick = (int) Math.round(10.0 * Math.pow(10_000.0, progress));
        int durationTicks = (int) Math.round(20.0 * (10.0 + (50.0 * progress)));
        return new Reward(rfPerTick, durationTicks);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String shortTileLabel(int code) {
        if (code < 0 || code >= TILE_KIND_COUNT) {
            return "?";
        }
        Tile tile = Tile.valueOf(code);
        TileType type = tile.getType();
        if (type == TileType.MANZU) {
            return tile.getNumber() + "m";
        }
        if (type == TileType.PINZU) {
            return tile.getNumber() + "p";
        }
        if (type == TileType.SOHZU) {
            return tile.getNumber() + "s";
        }
        return switch (tile) {
            case TON -> "East";
            case NAN -> "South";
            case SHA -> "West";
            case PEI -> "North";
            case HAK -> "White";
            case HAT -> "Green";
            case CHN -> "Red";
            default -> "?";
        };
    }

    private record TsumoResult(int han, boolean yakuman, List<String> yakuNames, List<String> yakumanNames) {
        private static TsumoResult noWin() {
            return new TsumoResult(0, false, List.of(), List.of());
        }
    }

    private record Reward(int rfPerTick, int durationTicks) {
    }
}
