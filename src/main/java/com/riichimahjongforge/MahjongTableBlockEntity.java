package com.riichimahjongforge;

import com.mojang.logging.LogUtils;
import com.mahjongcore.MahjongMatchDefinition;
import com.mahjongcore.MahjongGameState;
import com.riichimahjongforge.nbt.MahjongMatchDefinitionNbt;
import com.riichimahjongforge.nbt.MahjongMeldNbt;
import com.riichimahjongforge.player.BotSeatPlayer;
import com.riichimahjongforge.player.HumanSeatPlayer;
import com.riichimahjongforge.player.MahjongSeatPlayer;
import com.riichimahjongforge.nbt.MahjongGameStateNbt;
import com.mahjongcore.MahjongMeld;
import com.riichimahjongforge.client.MahjongTableClientSide;
import com.riichimahjongforge.network.MahjongNetwork;
import com.riichimahjongforge.network.S2CMatchLifecyclePacket;
import com.mahjongcore.rules.ClaimWindowRules;
import com.mahjongcore.rules.ClaimIntentRules;
import com.mahjongcore.rules.ClaimLegalityRules;
import com.mahjongcore.rules.ActionValidationRules;
import com.mahjongcore.rules.HandCodeRules;
import com.mahjongcore.rules.MeldCandidateRules;
import com.mahjongcore.rules.RoundSetupRules;
import com.mahjongcore.rules.SlotIndexRules;
import com.mahjongcore.rules.TurnOrderRules;
import com.mahjongcore.rules.WinRules;
import com.mahjongcore.MahjongPersonalSituation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.Containers;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import com.mahjongcore.hands.Mentsu;
import com.mahjongcore.tile.Tile;
import org.slf4j.Logger;

/**
 * Per-table lobby: four seats (N/E/S/W faces), optional closed sides (stick), persisted in chunk NBT.
 * Match lifecycle (Phase 4): {@link TableMatchPhase} is server-authoritative. Phase 5: wall/deal/draw/discard on
 * server. Clients mirror table state from the block entity update tag, which is intentionally aligned with persisted
 * save data so there is a single source of truth for table sync/load.
 */
public class MahjongTableBlockEntity extends BlockEntity implements Container {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String INVENTORY_ITEMS_INT_TAG = "MtItemsInt";
    private static final double FOV_NORMALIZE_RADIUS_BLOCKS = 3.0;
    private static final int FOV_NORMALIZE_EFFECT_DURATION = 20;
    private static final TagKey<Item> WRENCH_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));

    public static final int SEAT_COUNT = 4;
    public static final int TILES_IN_TABLE_SLOTS = 136;
    public static final int PLAYER_ZONE_SLOTS_PER_SEAT = 14;
    public static final int PLAYER_ZONE_TOTAL_SLOTS = PLAYER_ZONE_SLOTS_PER_SEAT * SEAT_COUNT;
    public static final int OPEN_MELD_SLOTS_PER_SEAT = 16;
    public static final int OPEN_MELD_TOTAL_SLOTS = OPEN_MELD_SLOTS_PER_SEAT * SEAT_COUNT;
    public static final int DISCARDS_SLOTS_PER_SEAT = 24;
    public static final int DISCARDS_TOTAL_SLOTS = DISCARDS_SLOTS_PER_SEAT * SEAT_COUNT;
    public static final int WALL_SLOTS = 136;
    public static final int DEAD_WALL_SLOTS = RoundSetupRules.DEAD_WALL_SIZE;
    public static final int DISCARD_GRID_COLS = 6;
    public static final int DISCARD_GRID_ROWS = 4;
    public static final int INV_TILES_IN_TABLE_START = 0;
    public static final int INV_PLAYER_ZONE_START = INV_TILES_IN_TABLE_START + TILES_IN_TABLE_SLOTS;
    public static final int INV_OPEN_MELD_START = INV_PLAYER_ZONE_START + PLAYER_ZONE_TOTAL_SLOTS;
    public static final int INV_WALL_START = INV_OPEN_MELD_START + OPEN_MELD_TOTAL_SLOTS;
    public static final int INV_DEAD_WALL_START = INV_WALL_START + WALL_SLOTS;
    public static final int INV_DISCARDS_START = INV_DEAD_WALL_START + DEAD_WALL_SLOTS;
    // IMPORTANT: this container is >255 slots, so we must serialize inventory with int slot ids
    // (see saveInventoryWithIntSlots/loadInventoryWithIntSlots), not byte-based slot encoding helpers.
    public static final int INVENTORY_SIZE = INV_DISCARDS_START + DISCARDS_TOTAL_SLOTS;
    static {
        if (DISCARD_GRID_COLS * DISCARD_GRID_ROWS != DISCARDS_SLOTS_PER_SEAT) {
            throw new IllegalStateException("discard grid must match DISCARDS_SLOTS_PER_SEAT");
        }
    }

    /** First absolute index of the per-seat hand / concealed zone (same as inventory menu "Hands" section). */
    public static int playerZoneSectionStart() {
        return INV_PLAYER_ZONE_START;
    }

    /** First absolute index of the discard river section (same as inventory menu "Discards" section). */
    public static int discardSectionStart() {
        return INV_DISCARDS_START;
    }

    /** Absolute container index for {@code physicalSeat}'s player-zone slot {@code slotInSeat} (0..slotsPerSeat-1). */
    public static int playerZoneAbsolute(int physicalSeat, int slotInSeat) {
        return SlotIndexRules.playerZoneAbsolute(
                SEAT_COUNT, INV_PLAYER_ZONE_START, PLAYER_ZONE_SLOTS_PER_SEAT, physicalSeat, slotInSeat);
    }

    /** Base index of {@code physicalSeat}'s contiguous player-zone run (hand slots start here). */
    public static int playerZoneBase(int physicalSeat) {
        return SlotIndexRules.playerZoneBase(SEAT_COUNT, INV_PLAYER_ZONE_START, PLAYER_ZONE_SLOTS_PER_SEAT, physicalSeat);
    }

    /** Absolute container index for {@code physicalSeat}'s discard slot {@code slotInSeat} (0..discardsPerSeat-1). */
    public static int discardAbsolute(int physicalSeat, int slotInSeat) {
        return SlotIndexRules.discardAbsolute(
                SEAT_COUNT, INV_DISCARDS_START, DISCARDS_SLOTS_PER_SEAT, physicalSeat, slotInSeat);
    }

    /** Base index of {@code physicalSeat}'s contiguous discard-slot run. */
    public int lastDiscardSeat() { return lastDiscardSeat; }
    public int lastMeldSeat() { return lastMeldSeat; }
    public int lastMeldClaimedSlotIndex() { return lastMeldClaimedSlotIndex; }
    public int lastDiscardSlotIndex() { return lastDiscardSlotIndex; }

    public static int discardBase(int physicalSeat) {
        return SlotIndexRules.discardBase(SEAT_COUNT, INV_DISCARDS_START, DISCARDS_SLOTS_PER_SEAT, physicalSeat);
    }

    /** Physical seat owning the provided absolute player-zone slot, or {@code -1} if outside the section. */
    public static int physicalSeatFromPlayerZoneAbsolute(int absolute) {
        return SlotIndexRules.physicalSeatFromPlayerZoneAbsolute(
                INV_PLAYER_ZONE_START, PLAYER_ZONE_TOTAL_SLOTS, PLAYER_ZONE_SLOTS_PER_SEAT, absolute);
    }

    /** Slot index within the owning seat for the provided absolute player-zone slot, or {@code -1} if invalid. */
    public static int slotInSeatFromPlayerZoneAbsolute(int absolute) {
        return SlotIndexRules.slotInSeatFromPlayerZoneAbsolute(
                INV_PLAYER_ZONE_START, PLAYER_ZONE_TOTAL_SLOTS, PLAYER_ZONE_SLOTS_PER_SEAT, absolute);
    }

    private MahjongMatchDefinition rules = MahjongMatchDefinition.DEFAULT;

    /** Transient server-side player objects; index-parallel with current rules seats. Never synced to clients. */
    private final MahjongSeatPlayer[] seatPlayers = new MahjongSeatPlayer[SEAT_COUNT];

    /**
     * If true, the table inventory menu allows manual edits even while {@link #matchPhase} is
     * {@link TableMatchPhase#IN_MATCH}. Server gameplay logic may still mutate inventory regardless.
     */
    private boolean allowInventoryEditWhileInMatch = false;
    private boolean allowGameplay = true;
    private boolean allowCustomTilePack = true;
    private boolean normalizeFovInRadius = true;
    private boolean passiveBots = false;

    private TableMatchPhase matchPhase = TableMatchPhase.WAITING;
    /** Hand index within the match (0 after start; incremented on each exhaustive / ryuukyoku reshuffle). */
    private int matchRound;

    @Nullable
    private MahjongGameState gameState;
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    @SuppressWarnings("unchecked")
    private final List<MahjongMeld>[] beMelds = new List[SEAT_COUNT];

    private static final int ROUND_SETUP_WALL_BATCH = 8;
    private static final int ROUND_SETUP_DEAD_WALL_BATCH = 8;
    private static final int ROUND_SETUP_HANDS_PER_TICK = 4;
    private long actionTraceSequence = 0L;

    private enum RoundSetupStage {
        NONE,
        BUILD_WALL,
        BUILD_DEAD_WALL,
        DEAL_HANDS,
        REVEAL_DORA
    }

    private record DeadWallPlacement(int deadWallOffset, ItemStack stack) {}

    private record HandPlacement(int seat, ItemStack stack) {}

    public enum SeatAction {
        RIICHI,
        TSUMO,
        ANKAN,
        KAN_OPTION_1,
        KAN_OPTION_2,
        KAN_OPTION_3,
        CANCEL,
        RON,
        CHANKAN,
        PASS,
        PON,
        DAIMIN_KAN,
        CHI,
        CHI_OPTION_1,
        CHI_OPTION_2
    }

    public record ClientSeatHintState(
            boolean tenpai, boolean riichiAvailable, boolean tsumoActionAvailable, boolean emitParticlesThisTick) {}

    private enum ResultAnimStage {
        NONE,
        SHOW_HEADER,
        SHOW_YAKU_LINES,
        SHOW_FINAL
    }

    private record PendingWinResult(
            int winnerSeat,
            @Nullable UUID winnerUuid,
            String winnerName,
            int han,
            boolean yakuman,
            List<String> yakuNames,
            List<String> yakumanNames,
            String headerText,
            int headerColor,
            List<String> yakuLines,
            List<String> deltaLines,
            String verdictTitle) {}

    private RoundSetupStage roundSetupStage = RoundSetupStage.NONE;
    private final ArrayDeque<ItemStack> roundSetupLiveWallQueue = new ArrayDeque<>();
    private final ArrayDeque<DeadWallPlacement> roundSetupDeadWallQueue = new ArrayDeque<>();
    private final ArrayDeque<HandPlacement> roundSetupHandQueue = new ArrayDeque<>();
    private boolean doraHiddenDuringSetup = false;
    private boolean uraDoraRevealed = false;
    private transient long[] clientSeatHintEvalBucket = new long[SEAT_COUNT];
    private transient boolean[] clientSeatHintTenpai = new boolean[SEAT_COUNT];
    private transient boolean[] clientSeatHintRiichi = new boolean[SEAT_COUNT];
    private transient boolean[] clientSeatHintTsumoAction = new boolean[SEAT_COUNT];
    private transient long[] clientSeatParticleEmitTick = new long[SEAT_COUNT];
    private String handResultRoundTitle = "";
    private String handResultHeadline = "";
    private int handResultHeadlineColor = 0xFFFFFF;
    private String handResultFooter = "";
    private int handResultFooterColor = 0xFFFFFF;
    private final ArrayList<String> handResultYakuLines = new ArrayList<>();
    private final ArrayList<String> handResultDeltaLines = new ArrayList<>();
    private int handResultWinnerSeat = -1;
    private int closedKanSelectionSeat = -1;
    private int chiSelectionSeat = -1;
    private int lastDiscardSeat = -1;
    private int lastDiscardSlotIndex = -1;
    private int lastMeldSeat = -1;
    private int lastMeldClaimedSlotIndex = -1;
    private ResultAnimStage resultAnimStage = ResultAnimStage.NONE;
    private long resultAnimNextTick = Long.MIN_VALUE;
    private int resultAnimYakuIndex = 0;
    @Nullable
    private PendingWinResult pendingWinResult;

    private void logAtomicAction(String action, String details) {
        if (gameState == null) {
            LOGGER.info(
                    "[MahjongTable/trace #{}] table={} action={} {}",
                    ++actionTraceSequence,
                    worldPosition,
                    action,
                    details);
            return;
        }
        LOGGER.info(
                "[MahjongTable/trace #{}] table={} hand={} phase={} turnSeat={} action={} {}",
                ++actionTraceSequence,
                worldPosition,
                gameState.handNumber,
                gameState.phase,
                gameState.currentTurnSeat,
                action,
                details);
    }

    private static String seatLogLabel(int seat) {
        return seat < 0 || seat >= SEAT_COUNT ? "seat?" : ("seat " + (seat + 1));
    }

    private String seatActorDebugLabel(ServerLevel sl, int seat) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return "unknown";
        }
        MahjongSeatPlayer seatPlayer = seatPlayers[seat];
        if (seatPlayer != null) {
            return seatPlayer.actorDebugLabel(sl, seat);
        }
        if (occupantAt(seat) == null) {
            return "empty";
        }
        return "offline";
    }

    private static String tileLabel(int code) {
        if (code < 0 || code > 33) {
            return "none";
        }
        Tile t = Tile.valueOf(code);
        return t.name().toLowerCase(Locale.ROOT);
    }

    private static Component tileDisplayNameComponent(int code) {
        Item item = MahjongTileItems.itemForCode(code);
        if (item != null) {
            return new ItemStack(item).getHoverName();
        }
        return Component.literal(tileLabel(code));
    }

    public MahjongTableBlockEntity(BlockPos pos, BlockState state) {
        super(RiichiMahjongForgeMod.MAHJONG_TABLE_BLOCK_ENTITY.get(), pos, state);
        for (int i = 0; i < SEAT_COUNT; i++) {
            beMelds[i] = new ArrayList<>();
            clientSeatHintEvalBucket[i] = Long.MIN_VALUE;
            clientSeatParticleEmitTick[i] = Long.MIN_VALUE;
        }
    }

    public ClientSeatHintState clientSeatHintState(int seat, long tick) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return new ClientSeatHintState(false, false, false, false);
        }
        if (clientSeatHintEvalBucket == null || clientSeatHintEvalBucket.length != SEAT_COUNT) {
            clientSeatHintEvalBucket = new long[SEAT_COUNT];
            clientSeatHintTenpai = new boolean[SEAT_COUNT];
            clientSeatHintRiichi = new boolean[SEAT_COUNT];
            clientSeatHintTsumoAction = new boolean[SEAT_COUNT];
            clientSeatParticleEmitTick = new long[SEAT_COUNT];
            Arrays.fill(clientSeatHintEvalBucket, Long.MIN_VALUE);
            Arrays.fill(clientSeatParticleEmitTick, Long.MIN_VALUE);
        }
        long bucket = tick / 5L;
        if (clientSeatHintEvalBucket[seat] != bucket) {
            clientSeatHintEvalBucket[seat] = bucket;
            MahjongGameState gs = gameStateOrNull();
            if (!isInMatch() || gs == null || isInHandResultPhase()) {
                clientSeatHintTenpai[seat] = false;
                clientSeatHintRiichi[seat] = false;
                clientSeatHintTsumoAction[seat] = false;
            } else {
                ArrayDeque<Integer> concealed = visibleConcealedHandCodes(seat);
                    ArrayDeque<Integer> concealedForTsumo = concealedForTsumoCheck(seat, null);
                ArrayList<Mentsu> melds = visibleMeldsAsMentsuList(seat);
                    boolean winAction = WinRules.isTsumoActionAvailable(
                                gs.phase == MahjongGameState.TurnPhase.MUST_DISCARD,
                                gs.currentTurnSeat == seat,
                                gs.lastDrawnCode,
                                concealedForTsumo,
                                melds)
                        || WinRules.canRon(
                                gs.isClaimWindowActive(),
                                seat,
                                gs.claimDiscarderSeat,
                                deterministicPlayOrder(),
                                gs.riichiDeclared,
                                gs.ippatsuEligible,
                                gs.claimIsChankanWindow,
                                gs.claimTileCode,
                                concealed,
                                melds,
                                gs.handNumber,
                                gs.scoreAsNotFirstRound,
                                getLiveWallRemainingFromInventory(),
                                getDoraIndicatorCodesForScoring())
                                && !isSeatFuritenForRon(seat, gs.claimTileCode, concealed, melds);
                clientSeatHintTenpai[seat] = WinRules.isTenpai(concealed, melds);
                clientSeatHintRiichi[seat] = seat >= 0
                        && seat < gs.riichiDeclared.length
                        && gs.riichiDeclared[seat];
                clientSeatHintTsumoAction[seat] = winAction;
            }
        }
        boolean emitThisTick = clientSeatParticleEmitTick[seat] != tick;
        if (emitThisTick) {
            clientSeatParticleEmitTick[seat] = tick;
        }
        return new ClientSeatHintState(
                clientSeatHintTenpai[seat],
                clientSeatHintRiichi[seat],
                clientSeatHintTsumoAction[seat],
                emitThisTick);
    }

    public List<SeatAction> visibleSeatActionRow(int seat) {
        MahjongGameState gs = gameStateOrNull();
        boolean riichiPending = gs != null
                && seat >= 0
                && seat < gs.riichiPending.length
                && gs.riichiPending[seat];
        if (seat < 0 || seat >= SEAT_COUNT || !isSeatEnabled(seat)) {
            return List.of();
        }
        if (gs == null || !isInMatch() || isInHandResultPhase() || riichiPending) {
            return List.of();
        }
        ArrayDeque<Integer> concealed = visibleConcealedHandCodes(seat);
        ArrayList<Mentsu> melds = visibleMeldsAsMentsuList(seat);
        if (gs.isClaimWindowActive()) {
            if (!ClaimIntentRules.isSeatEligibleForClaim(deterministicPlayOrder(), gs.claimDiscarderSeat, seat)) {
                return List.of();
            }
            if (gs.claimIsChankanWindow) {
                ArrayList<SeatAction> out = new ArrayList<>(2);
                if (WinRules.canRon(
                        gs.isClaimWindowActive(),
                        seat,
                        gs.claimDiscarderSeat,
                        deterministicPlayOrder(),
                        gs.riichiDeclared,
                        gs.ippatsuEligible,
                        gs.claimIsChankanWindow,
                        gs.claimTileCode,
                        concealed,
                        melds,
                        gs.handNumber,
                        gs.scoreAsNotFirstRound,
                        getLiveWallRemainingFromInventory(),
                        getDoraIndicatorCodesForScoring())
                        && !isSeatFuritenForRon(seat, gs.claimTileCode, concealed, melds)) {
                    out.add(SeatAction.CHANKAN);
                }
                out.add(SeatAction.PASS);
                return out;
            }
            if (chiSelectionSeat == seat) {
                List<ClaimLegalityRules.ChiPair> chiPairs =
                        ClaimLegalityRules.findChiPairs(concealed, gs.claimTileCode);
                ArrayList<SeatAction> out = new ArrayList<>(3);
                for (int i = 0; i < Math.min(2, chiPairs.size()); i++) {
                    out.add(chiOptionActionForIndex(i));
                }
                out.add(SeatAction.CANCEL);
                return out;
            }
            boolean inRiichi = seat >= 0 && seat < gs.riichiDeclared.length && gs.riichiDeclared[seat];
            ArrayList<SeatAction> out = new ArrayList<>(5);
            if (WinRules.canRon(
                    gs.isClaimWindowActive(),
                    seat,
                    gs.claimDiscarderSeat,
                    deterministicPlayOrder(),
                    gs.riichiDeclared,
                    gs.ippatsuEligible,
                    gs.claimIsChankanWindow,
                    gs.claimTileCode,
                    concealed,
                    melds,
                    gs.handNumber,
                    gs.scoreAsNotFirstRound,
                    getLiveWallRemainingFromInventory(),
                    getDoraIndicatorCodesForScoring())
                    && !isSeatFuritenForRon(seat, gs.claimTileCode, concealed, melds)) {
                out.add(SeatAction.RON);
            }
            if (!inRiichi && ClaimLegalityRules.canDaiminKan(concealed, gs.claimTileCode)) {
                out.add(SeatAction.DAIMIN_KAN);
            }
            if (!inRiichi && ClaimLegalityRules.canPon(concealed, gs.claimTileCode)) {
                out.add(SeatAction.PON);
            }
            if (!inRiichi
                    && ClaimIntentRules.isKamicha(deterministicPlayOrder(), gs.claimDiscarderSeat, seat)
                    && !ClaimLegalityRules.findChiPairs(concealed, gs.claimTileCode).isEmpty()) {
                out.add(SeatAction.CHI);
            }
            if (seatHasLegalClaimOnDiscardWindow(seat)) {
                out.add(SeatAction.PASS);
            }
            return out;
        }
        if (seat != gs.currentTurnSeat || gs.phase != MahjongGameState.TurnPhase.MUST_DISCARD) {
            return List.of();
        }
        ArrayDeque<Integer> concealedForTsumo = concealedForTsumoCheck(seat, null);
        List<Integer> kanCandidates = MeldCandidateRules.kanCodes(concealed, openPonTileCodes(beMelds[seat]));
        if (closedKanSelectionSeat == seat && !kanCandidates.isEmpty()) {
            ArrayList<SeatAction> out = new ArrayList<>(Math.min(3, kanCandidates.size()) + 1);
            for (int i = 0; i < Math.min(3, kanCandidates.size()); i++) {
                out.add(kanOptionActionForIndex(i));
            }
            out.add(SeatAction.CANCEL);
            return out;
        }
        ArrayList<SeatAction> out = new ArrayList<>(3);
        boolean alreadyRiichi = seat >= 0
                && seat < gs.riichiDeclared.length
                && gs.riichiDeclared[seat];
        if (!alreadyRiichi
                && WinRules.isRiichiActionAvailable(
                        gs.phase == MahjongGameState.TurnPhase.MUST_DISCARD,
                        gs.currentTurnSeat == seat,
                        gs.lastDrawnCode,
                        concealed,
                        melds)) {
            out.add(SeatAction.RIICHI);
        }
        if (WinRules.isTsumoActionAvailable(
                gs.phase == MahjongGameState.TurnPhase.MUST_DISCARD,
                gs.currentTurnSeat == seat,
                gs.lastDrawnCode,
                concealedForTsumo,
                melds)) {
            out.add(SeatAction.TSUMO);
        }
        if (!kanCandidates.isEmpty()) {
            out.add(SeatAction.ANKAN);
        }
        return out;
    }

    public List<Integer> visibleKanCandidateCodes(int seat) {
        MahjongGameState gs = gameStateOrNull();
        boolean riichiPending = gs != null
                && seat >= 0
                && seat < gs.riichiPending.length
                && gs.riichiPending[seat];
        if (seat < 0 || seat >= SEAT_COUNT || !isSeatEnabled(seat)) {
            return List.of();
        }
        if (gs == null || !isInMatch() || isInHandResultPhase() || riichiPending) {
            return List.of();
        }
        if (seat != gs.currentTurnSeat || gs.phase != MahjongGameState.TurnPhase.MUST_DISCARD) {
            return List.of();
        }
        return MeldCandidateRules.kanCodes(
                visibleConcealedHandCodes(seat), openPonTileCodes(beMelds[seat]));
    }

    public List<ClaimLegalityRules.ChiPair> visibleChiCandidatePairs(int seat) {
        MahjongGameState gs = gameStateOrNull();
        if (seat < 0 || seat >= SEAT_COUNT || !isSeatEnabled(seat)) {
            return List.of();
        }
        if (gs == null || !isInMatch() || isInHandResultPhase() || !gs.isClaimWindowActive()) {
            return List.of();
        }
        if (chiSelectionSeat != seat) {
            return List.of();
        }
        return ClaimLegalityRules.findChiPairs(visibleConcealedHandCodes(seat), gs.claimTileCode);
    }

    private static SeatAction kanOptionActionForIndex(int idx) {
        return switch (idx) {
            case 0 -> SeatAction.KAN_OPTION_1;
            case 1 -> SeatAction.KAN_OPTION_2;
            default -> SeatAction.KAN_OPTION_3;
        };
    }

    private static int kanOptionIndex(SeatAction action) {
        return switch (action) {
            case KAN_OPTION_1 -> 0;
            case KAN_OPTION_2 -> 1;
            case KAN_OPTION_3 -> 2;
            default -> -1;
        };
    }

    private static SeatAction chiOptionActionForIndex(int idx) {
        return idx == 0 ? SeatAction.CHI_OPTION_1 : SeatAction.CHI_OPTION_2;
    }

    private static int chiOptionIndex(SeatAction action) {
        return switch (action) {
            case CHI_OPTION_1 -> 0;
            case CHI_OPTION_2 -> 1;
            default -> -1;
        };
    }

    private static List<Integer> openPonTileCodes(List<MahjongMeld> melds) {
        ArrayList<MeldCandidateRules.MeldView> coreMelds = new ArrayList<>(melds.size());
        for (MahjongMeld meld : melds) {
            coreMelds.add(new MeldCandidateRules.MeldView(toCoreMeldKind(meld.kind()), meld.tileCodes()));
        }
        return MeldCandidateRules.extractOpenPonTileCodes(coreMelds);
    }

    private boolean upgradeOpenPonToKan(int seat, int tileCode) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return false;
        }
        List<MahjongMeld> melds = beMelds[seat];
        ArrayList<MeldCandidateRules.MeldView> coreMelds = new ArrayList<>(melds.size());
        for (MahjongMeld meld : melds) {
            coreMelds.add(new MeldCandidateRules.MeldView(toCoreMeldKind(meld.kind()), meld.tileCodes()));
        }
        int idx = MeldCandidateRules.findUpgradeableOpenPonIndex(coreMelds, tileCode);
        if (idx >= 0 && idx < melds.size()) {
            MahjongMeld old = melds.get(idx);
            mutateSeatMelds(
                    seat,
                    seatMelds -> seatMelds.set(
                            idx,
                            new MahjongMeld(
                                    MahjongMeld.Kind.DAIMIN_KAN,
                                    new int[] {tileCode, tileCode, tileCode, tileCode},
                                    old.fromSeat())));
            return true;
        }
        return false;
    }

    private static MeldCandidateRules.MeldKind toCoreMeldKind(MahjongMeld.Kind kind) {
        return switch (kind) {
            case CHI -> MeldCandidateRules.MeldKind.CHI;
            case PON -> MeldCandidateRules.MeldKind.PON;
            case DAIMIN_KAN -> MeldCandidateRules.MeldKind.DAIMIN_KAN;
            case ANKAN -> MeldCandidateRules.MeldKind.ANKAN;
        };
    }

    public UUID occupantAt(int seat) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return null;
        }
        MahjongMatchDefinition.SeatDefinition[] seats = rules.seats();
        if (seat >= seats.length) {
            return null;
        }
        return seats[seat].occupant();
    }

    @Nullable
    public Integer seatIndexForPlayer(ServerPlayer player) {
        return findSeatIndex(player.getUUID());
    }

    public boolean isSeatEnabled(int seat) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return false;
        }
        MahjongMatchDefinition.SeatDefinition[] seats = rules.seats();
        return seat < seats.length && seats[seat].enabled();
    }

    private void setSeatDefinition(int seat, boolean enabled, @Nullable UUID occupant) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return;
        }
        MahjongMatchDefinition.SeatDefinition[] current = rules.seats();
        MahjongMatchDefinition.SeatDefinition[] updated = Arrays.copyOf(current, SEAT_COUNT);
        for (int i = current.length; i < SEAT_COUNT; i++) {
            updated[i] = new MahjongMatchDefinition.SeatDefinition(false, null);
        }
        updated[seat] = new MahjongMatchDefinition.SeatDefinition(enabled, occupant);
        rules.withSeats(updated);
    }

    private void setSeatOccupant(int seat, @Nullable UUID occupant) {
        setSeatDefinition(seat, isSeatEnabled(seat), occupant);
    }

    private void setSeatEnabledFlag(int seat, boolean enabled) {
        setSeatDefinition(seat, enabled, occupantAt(seat));
    }

    private UUID[] captureSeatOccupants() {
        UUID[] snapshot = new UUID[SEAT_COUNT];
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            snapshot[seat] = occupantAt(seat);
        }
        return snapshot;
    }

    public List<Integer> deterministicPlayOrder() {
        List<Integer> occupied = new ArrayList<>();
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            if (isSeatEnabled(seat) && occupantAt(seat) != null) {
                occupied.add(seat);
            }
        }
        if (occupied.size() < 2) {
            return List.of();
        }
        int dealerSeat = occupied.get(0);
        return TurnOrderRules.counterClockwisePlayOrder(SEAT_COUNT, dealerSeat, occupied);
    }

    private void restoreSeatOccupants(UUID[] snapshot) {
        for (int seat = 0; seat < SEAT_COUNT && seat < snapshot.length; seat++) {
            setSeatOccupant(seat, snapshot[seat]);
        }
    }

    private void clearAllSeatOccupants() {
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            setSeatOccupant(seat, null);
        }
    }

    /**
     * Tile code in the table inventory player zone (synced to chunk watchers). {@code -1} if empty or not a
     * registered mahjong tile item.
     */
    public int visibleHandTileCodeAt(int seat, int slotInSeat) {
        if (seat < 0 || seat >= SEAT_COUNT || slotInSeat < 0 || slotInSeat >= PLAYER_ZONE_SLOTS_PER_SEAT) {
            return -1;
        }
        int invSlot = playerZoneAbsolute(seat, slotInSeat);
        ItemStack st = getItem(invSlot);
        if (st.isEmpty()) {
            return -1;
        }
        Integer code = MahjongTileItems.codeForItem(st.getItem());
        return code == null ? -1 : code;
    }

    /** Kan melds declared by a single seat this hand; each kan holds 4 tiles instead of 3, so the seat's
     * total tile count is 14 + kanMeldCount rather than 14 after drawing. */
    private int kanMeldCountForSeat(int seat) {
        if (seat < 0 || seat >= SEAT_COUNT) return 0;
        int n = 0;
        for (MahjongMeld m : beMelds[seat]) {
            if (m.kind() == MahjongMeld.Kind.DAIMIN_KAN || m.kind() == MahjongMeld.Kind.ANKAN) {
                n++;
            }
        }
        return n;
    }

    /** Open / closed kans declared this hand; used for kan-dora indicator count on the client wall render. */
    public int declaredKanCountFromMelds() {
        int n = 0;
        for (int s = 0; s < SEAT_COUNT; s++) {
            for (MahjongMeld m : beMelds[s]) {
                if (m.kind() == MahjongMeld.Kind.DAIMIN_KAN || m.kind() == MahjongMeld.Kind.ANKAN) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * When true, ura-dora indicator tiles on the dead wall render face-up. Reserved for end-of-hand scoring; always
     * false until that flow exists.
     */
    public boolean shouldShowUraDoraIndicatorsForRender() {
        return uraDoraRevealed;
    }

    public boolean isDoraHiddenDuringSetup() {
        return doraHiddenDuringSetup;
    }

    public MahjongMatchDefinition getRules() {
        return rules;
    }

    public boolean allowInventoryEditWhileInMatch() {
        return allowInventoryEditWhileInMatch;
    }

    public boolean allowGameplay() {
        return allowGameplay;
    }

    public boolean normalizeFovInRadius() {
        return normalizeFovInRadius;
    }

    public boolean allowCustomTilePack() {
        return allowCustomTilePack;
    }

    public boolean passiveBots() {
        return passiveBots;
    }

    // -------------------------------------------------------------------------
    // MahjongPlayer-implementation protocol (called by MahjongSeatPlayer implementations)
    // -------------------------------------------------------------------------

    @Nullable
    public MahjongGameState activeGameState() {
        return gameState;
    }

    public TableMatchPhase currentMatchPhase() {
        return matchPhase;
    }

    public boolean seatHasLegalClaim(int seat) { return seatHasLegalClaimOnDiscardWindow(seat); }

    /**
     * Draws from the wall for a seat using the seat's {@link MahjongSeatPlayer} strategy.
     */
    public void performSeatDraw(ServerLevel sl, int seat) {
        MahjongSeatPlayer seatPlayer =
                (seat >= 0 && seat < SEAT_COUNT) ? seatPlayers[seat] : null;
        if (seatPlayer == null) {
            return;
        }
        tryPerformSeatDraw(sl, seat, seatPlayer);
    }

    public MahjongGameState.ActionResult tryPerformSeatDraw(
            ServerLevel sl, int seat, MahjongSeatPlayer seatPlayer) {
        MahjongGameState gs = gameState;
        boolean seatInRange = seat >= 0 && seat < SEAT_COUNT;
        ActionValidationRules.GateResult gate = ActionValidationRules.validateDrawAction(
                gs != null && matchPhase == TableMatchPhase.IN_MATCH,
                gs != null && gs.phase == MahjongGameState.TurnPhase.MUST_DRAW,
                gs != null && gs.currentTurnSeat == seat,
                seatInRange && isSeatEnabled(seat),
                seatInRange && occupantAt(seat) != null);
        if (gate == ActionValidationRules.GateResult.WRONG_PHASE) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        if (gate == ActionValidationRules.GateResult.NOT_YOUR_TURN) {
            return MahjongGameState.ActionResult.NOT_YOUR_TURN;
        }
        ItemStack drawn = popLiveWallStackFromInventory();
        MahjongGameState.ActionResult r = tryDrawPhysical(sl, seat, seatPlayer, drawn);
        if (r == MahjongGameState.ActionResult.EMPTY_WALL) {
            onExhaustiveDraw(sl);
            return r;
        }
        if (r != MahjongGameState.ActionResult.OK) return r;
        setChanged();
        syncToClients();
        afterSuccessfulDiscard(sl);
        tickActiveTurnSeat(sl);
        return MahjongGameState.ActionResult.OK;
    }

    /**
     * Applies a kan for {@code seat} using the given {@code kanTileCode}.
     * Handles ankan/kakan, chankan window, and rinshan draw. Returns true if applied.
     */
    public boolean applySeatKanForCode(ServerLevel sl, int seat, int kanTileCode) {
        MahjongSeatPlayer seatPlayer = (seat >= 0 && seat < SEAT_COUNT) ? seatPlayers[seat] : null;
        ArrayDeque<Integer> concealed = readConcealedHandFromInventory(seat);
        boolean wasClosedKan = MeldCandidateRules.closedKanCodes(concealed).contains(kanTileCode);
        boolean applied = false;
        if (wasClosedKan) {
            if (removeTilesFromSeatHand(seat, kanTileCode, 4)) {
                addSeatMeld(
                        seat,
                        new MahjongMeld(
                                MahjongMeld.Kind.ANKAN,
                                new int[] {kanTileCode, kanTileCode, kanTileCode, kanTileCode},
                                -1));
                applied = true;
            }
        } else if (MeldCandidateRules.addedKanCodes(concealed, openPonTileCodes(beMelds[seat]))
                .contains(kanTileCode)) {
            if (removeTilesFromSeatHand(seat, kanTileCode, 1)
                    && upgradeOpenPonToKan(seat, kanTileCode)) {
                lastDiscardSeat = -1;
                lastDiscardSlotIndex = -1;
                lastMeldSeat = seat;
                lastMeldClaimedSlotIndex = computeLastMeldClaimedSlotIndex(seat);
                applied = true;
            }
        }
        if (!applied) {
            return false;
        }
        playTilePlaceSound(sl);
        sortSeatHandTilesByTileCode(seat);
        logAtomicAction(
                wasClosedKan ? "ANKAN" : "KAKAN",
                seatLogLabel(seat) + " tile=" + tileLabel(kanTileCode));
        if (!wasClosedKan && hasChankanEligibles(seat, kanTileCode)) {
            openChankanWindow(seat, kanTileCode);
            setChanged();
            syncToClients();
            boolean chankanChanged = autoPassIneligibleClaimants();
            if (chankanChanged) {
                setChanged();
                syncToClients();
            }
            tryResolveClaimWindowIfReady(sl);
            return true;
        }
        applyRinshanDrawAfterKan(sl, seatPlayer, seat);
        setChanged();
        syncToClients();
        return true;
    }

    /** Discards for a seat using the seat's {@link MahjongSeatPlayer} strategy. */
    public void performSeatDiscard(ServerLevel sl, int seat) {
        MahjongSeatPlayer seatPlayer =
                (seat >= 0 && seat < SEAT_COUNT) ? seatPlayers[seat] : null;
        if (seatPlayer == null) {
            return;
        }
        tryPerformSeatDiscard(sl, seat, seatPlayer);
    }

    public MahjongGameState.ActionResult tryPerformSeatDiscard(
            ServerLevel sl, int seat, MahjongSeatPlayer seatPlayer) {
        MahjongGameState gs = gameState;
        ActionValidationRules.GateResult gate = ActionValidationRules.validateDiscardAction(
                gs != null && gs.phase == MahjongGameState.TurnPhase.MUST_DISCARD,
                gs != null && gs.currentTurnSeat == seat);
        if (gate == ActionValidationRules.GateResult.NOT_YOUR_TURN) {
            return MahjongGameState.ActionResult.NOT_YOUR_TURN;
        }
        if (gate == ActionValidationRules.GateResult.WRONG_PHASE) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        MahjongGameState.ActionResult r = tryDiscardPhysical(sl, seat, seatPlayer);
        if (r != MahjongGameState.ActionResult.OK) {
            return r;
        }
        setChanged();
        syncToClients();
        afterSuccessfulDiscard(sl);
        return MahjongGameState.ActionResult.OK;
    }

    /** Passes a seat's claim if it has no legal claim in the current window. */
    public void passIfNoLegalClaim(int seat) {
        if (gameState == null || !gameState.isClaimWindowActive()) return;
        if (gameState.claimIntent[seat] != ClaimWindowRules.ClaimIntent.NONE) return;
        if (!seatHasLegalClaimOnDiscardWindow(seat)) {
            gameState.claimIntent[seat] = ClaimWindowRules.ClaimIntent.PASS;
            logAtomicAction(
                    "CLAIM_AUTO_PASS",
                    seatLogLabel(seat) + " no legal claims on " + tileLabel(gameState.claimTileCode));
        }
    }

    /** Called after a seat player writes its claim decision to game state. Logs and syncs. */
    public void onClaimDecisionApplied(int seat) {
        if (gameState == null) return;
        ClaimWindowRules.ClaimIntent intent = gameState.claimIntent[seat];
        String log = seatLogLabel(seat) + " " + intent.name();
        if (intent == ClaimWindowRules.ClaimIntent.CHI) {
            log += " on " + tileLabel(gameState.claimTileCode)
                    + " with " + tileLabel(gameState.claimChiTileA[seat])
                    + "+" + tileLabel(gameState.claimChiTileB[seat]);
        } else if (intent != ClaimWindowRules.ClaimIntent.PASS) {
            log += " on " + tileLabel(gameState.claimTileCode);
        }
        logAtomicAction("CLAIM_INTENT", log);
        setChanged();
        syncToClients();
    }

    public void setAllowGameplay(boolean allow) {
        if (this.allowGameplay == allow) {
            return;
        }
        if (!allow && level instanceof ServerLevel sl && matchPhase == TableMatchPhase.IN_MATCH) {
            abortMatch(MatchAbortReason.RESET);
        } else if (!allow) {
            clearAllSeatOccupants();
            rebuildSeatPlayers();
            resetSeatPlayerDelays();
        }
        this.allowGameplay = allow;
        setChanged();
        syncToClients();
    }

    public void setAllowInventoryEditWhileInMatch(boolean allow) {
        if (this.allowInventoryEditWhileInMatch == allow) {
            return;
        }
        this.allowInventoryEditWhileInMatch = allow;
        setChanged();
        syncToClients();
    }

    public void setNormalizeFovInRadius(boolean normalize) {
        if (this.normalizeFovInRadius == normalize) {
            return;
        }
        this.normalizeFovInRadius = normalize;
        if (normalize && level instanceof ServerLevel sl && !sl.isClientSide()) {
            tickNormalizeFovInRadius(sl);
        }
        setChanged();
        syncToClients();
    }

    public void setAllowCustomTilePack(boolean allow) {
        if (this.allowCustomTilePack == allow) {
            return;
        }
        this.allowCustomTilePack = allow;
        setChanged();
        syncToClients();
    }

    public void setPassiveBots(boolean passive) {
        if (this.passiveBots == passive) {
            return;
        }
        this.passiveBots = passive;
        setChanged();
        syncToClients();
    }

    public void setRules(MahjongMatchDefinition newRules) {
        this.rules = newRules;
        setChanged();
        syncToClients();
    }

    public void updateRules(java.util.function.UnaryOperator<MahjongMatchDefinition> fn) {
        setRules(fn.apply(rules));
    }

    /**
     * Mahjong seat wind for the given index: East (0, dealer / first), South (1), West (2), North (3).
     * {@link net.minecraft.core.Direction} is only used as a stable key for names (e.g. chat localization), not as a
     * world-space table edge.
     */
    public static net.minecraft.core.Direction faceFromSeat(int seat) {
        return switch (seat) {
            case 0 -> net.minecraft.core.Direction.EAST;
            case 1 -> net.minecraft.core.Direction.SOUTH;
            case 2 -> net.minecraft.core.Direction.WEST;
            case 3 -> net.minecraft.core.Direction.NORTH;
            default -> net.minecraft.core.Direction.EAST;
        };
    }

    /**
     * World-space side of the 3×3 multiblock where this physical seat sits (north rim = seat 0, then clockwise).
     * Use for label position; use {@link #faceFromSeat} for round seat wind / lobby wind names.
     */
    public static net.minecraft.core.Direction tableEdgeFromSeat(int seat) {
        return switch (seat) {
            case 0 -> net.minecraft.core.Direction.NORTH;
            case 1 -> net.minecraft.core.Direction.EAST;
            case 2 -> net.minecraft.core.Direction.SOUTH;
            case 3 -> net.minecraft.core.Direction.WEST;
            default -> net.minecraft.core.Direction.NORTH;
        };
    }

    /**
     * Mahjong turn order runs counter-clockwise around the table (view from above). Physical seat indices 0–3 run
     * clockwise around the multiblock ({@link #tableEdgeFromSeat}), so CCW turn order is {@code N(0)→W(3)→S(2)→E(1)}.
     */
    @Nullable
    private Integer findSeatIndex(UUID id) {
        for (int i = 0; i < SEAT_COUNT; i++) {
            if (id.equals(occupantAt(i))) {
                return i;
            }
        }
        return null;
    }

    private int enabledSeatCount() {
        int n = 0;
        for (int i = 0; i < SEAT_COUNT; i++) {
            if (isSeatEnabled(i)) {
                n++;
            }
        }
        return n;
    }

    private int occupiedEnabledCount() {
        int n = 0;
        for (int i = 0; i < SEAT_COUNT; i++) {
            if (isSeatEnabled(i) && occupantAt(i) != null) {
                n++;
            }
        }
        return n;
    }

    /** Every enabled side has a player (used for auto-start when {@link #canStartMatch()} is true). */
    public boolean isAllEnabledSeatsFilled() {
        if (enabledSeatCount() == 0) {
            return false;
        }
        for (int i = 0; i < SEAT_COUNT; i++) {
            if (isSeatEnabled(i) && occupantAt(i) == null) {
                return false;
            }
        }
        return true;
    }

    /** 2–4 enabled sides, all occupied — valid auto-start gate (Phase 4). */
    public boolean canStartMatch() {
        if (!allowGameplay) {
            return false;
        }
        int n = enabledSeatCount();
        if (n < 2 || n > 4) {
            return false;
        }
        return isAllEnabledSeatsFilled();
    }

    public TableMatchPhase getMatchPhase() {
        return matchPhase;
    }

    public boolean isInMatch() {
        return matchPhase == TableMatchPhase.IN_MATCH || matchPhase == TableMatchPhase.HAND_RESULT;
    }

    public boolean isInHandResultPhase() {
        return matchPhase == TableMatchPhase.HAND_RESULT;
    }

    public String handResultRoundTitle() {
        return handResultRoundTitle;
    }

    public String handResultHeadline() {
        return handResultHeadline;
    }

    public int handResultHeadlineColor() {
        return handResultHeadlineColor;
    }

    public String handResultFooter() {
        return handResultFooter;
    }

    public int handResultFooterColor() {
        return handResultFooterColor;
    }

    public List<String> handResultYakuLines() {
        return List.copyOf(handResultYakuLines);
    }

    public List<String> handResultDeltaLines() {
        return List.copyOf(handResultDeltaLines);
    }

    public int visibleSeatPoints(int seat) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return 0;
        }
        return getRules().seatPoints(seat);
    }

    public int handResultWinnerSeat() {
        return handResultWinnerSeat;
    }

    /** Live match rules state; also populated on logical clients when synced. */
    @Nullable
    public MahjongGameState gameStateOrNull() {
        return gameState;
    }

    private void tryStartMatchIfReady() {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (matchPhase != TableMatchPhase.WAITING || !canStartMatch()) {
            return;
        }
        clearHandResultState();
        initializeSeatPointsForNewMatch();
        matchPhase = TableMatchPhase.IN_MATCH;
        setTableLit(sl, true);
        matchRound = 0;
        gameState = new MahjongGameState();
        gameState.handNumber = 0;
        resetSeatPlayerDelays();
        // Intentionally no sync here: beginNewHand fills wall/dead wall and MtGame.playOrder; sending IN_MATCH
        // before that left clients in a broken render state (empty wall + dealer from stale or empty MtGame).
        if (!beginNewHand(sl)) {
            matchPhase = TableMatchPhase.WAITING;
            setTableLit(sl, false);
            matchRound = 0;
            gameState = null;
            clearRoundSetupState();
            resetSeatPlayerDelays();
            setChanged();
            syncToClients();
            int required = requiredTilesToStartHand(enabledSeatCount());
            notifySeatedHumans(
                    sl,
                    notEnoughTilesComponent(countTileItemsInTableStorage(), required));
        } else {
            S2CMatchLifecyclePacket packet =
                    new S2CMatchLifecyclePacket(
                            sl.dimension(),
                            worldPosition,
                            true,
                            MatchAbortReason.GENERIC,
                            matchRound);
            broadcastLifecycleToSeated(sl, packet);
            sl.playSound(
                    null,
                    worldPosition,
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS,
                    0.9f,
                    1.05f);
        }
    }

    private void clearHandResultState() {
        if (matchPhase == TableMatchPhase.HAND_RESULT) {
            matchPhase = TableMatchPhase.IN_MATCH;
        }
        handResultRoundTitle = "";
        handResultHeadline = "";
        handResultHeadlineColor = 0xFFFFFF;
        handResultFooter = "";
        handResultFooterColor = 0xFFFFFF;
        handResultYakuLines.clear();
        handResultDeltaLines.clear();
        handResultWinnerSeat = -1;
        resultAnimStage = ResultAnimStage.NONE;
        resultAnimNextTick = Long.MIN_VALUE;
        resultAnimYakuIndex = 0;
        pendingWinResult = null;
    }

    private void initializeSeatPointsForNewMatch() {
        rules.withSeatPoints(MahjongMatchDefinition.createDefaultSeatPoints(rules.seats(), rules.startingPoints()));
    }

    private void setHandResultState(
            String roundTitle,
            String headline,
            int headlineColor,
            List<String> yakuLines,
            List<String> deltaLines,
            int winnerSeat) {
        matchPhase = TableMatchPhase.HAND_RESULT;
        handResultRoundTitle = roundTitle == null ? "" : roundTitle;
        handResultHeadline = headline == null ? "" : headline;
        handResultHeadlineColor = headlineColor;
        handResultFooter = "";
        handResultFooterColor = headlineColor;
        handResultYakuLines.clear();
        handResultDeltaLines.clear();
        handResultWinnerSeat =
                winnerSeat >= 0 && winnerSeat < SEAT_COUNT ? winnerSeat : -1;
        if (yakuLines != null) {
            handResultYakuLines.addAll(yakuLines);
        }
        if (deltaLines != null) {
            handResultDeltaLines.addAll(deltaLines);
        }
    }

    private void beginPendingWinResultAnimation(
            ServerLevel sl,
            int winnerSeat,
            @Nullable UUID winnerUuid,
            String winnerName,
            int han,
            boolean yakuman,
            List<String> yakuNames,
            List<String> yakumanNames,
            String headerText,
            int headerColor,
            List<String> yakuLines,
            List<String> deltaLines,
            String verdictTitle) {
        String roundTitle = localizedRoundTitleWithHonba(
                gameState != null ? (gameState.handNumber + 1) : (matchRound + 1),
                gameState != null ? gameState.honba : 0);
        setHandResultState(
                roundTitle,
                " ",
                headerColor,
                List.of(),
                List.of(),
                winnerSeat);
        pendingWinResult = new PendingWinResult(
                winnerSeat,
                winnerUuid,
                winnerName,
                han,
                yakuman,
                yakuNames == null ? List.of() : List.copyOf(yakuNames),
                yakumanNames == null ? List.of() : List.copyOf(yakumanNames),
                headerText,
                headerColor,
                yakuLines == null ? List.of() : List.copyOf(yakuLines),
                deltaLines == null ? List.of() : List.copyOf(deltaLines),
                verdictTitle);
        resultAnimStage = ResultAnimStage.SHOW_HEADER;
        resultAnimYakuIndex = 0;
        resultAnimNextTick = sl.getGameTime();
        setChanged();
        syncToClients();
    }

    private boolean tickPendingWinResultAnimation(ServerLevel sl) {
        if (resultAnimStage == ResultAnimStage.NONE || pendingWinResult == null) {
            return false;
        }
        long now = sl.getGameTime();
        if (now < resultAnimNextTick) {
            return true;
        }
        boolean changed = false;
        switch (resultAnimStage) {
            case SHOW_HEADER -> {
                handResultHeadline = pendingWinResult.headerText();
                handResultHeadlineColor = pendingWinResult.headerColor();
                playResultStepSound(sl);
                resultAnimStage = ResultAnimStage.SHOW_YAKU_LINES;
                resultAnimNextTick = now + 20L;
                changed = true;
            }
            case SHOW_YAKU_LINES -> {
                if (resultAnimYakuIndex < pendingWinResult.yakuLines().size()) {
                    handResultYakuLines.add(pendingWinResult.yakuLines().get(resultAnimYakuIndex));
                    resultAnimYakuIndex++;
                    playResultStepSound(sl);
                    resultAnimNextTick = now + 10L;
                    changed = true;
                } else {
                    resultAnimStage = ResultAnimStage.SHOW_FINAL;
                    resultAnimNextTick = now + 20L;
                }
            }
            case SHOW_FINAL -> {
                handResultFooter = pendingWinResult.verdictTitle();
                handResultFooterColor = pendingWinResult.headerColor();
                handResultDeltaLines.clear();
                handResultDeltaLines.addAll(pendingWinResult.deltaLines());
                if (WinRules.isEpicVerdictNotification(pendingWinResult.han(), pendingWinResult.yakuman())) {
                    announceBigTitleToPlayersInRadius(
                            sl,
                            Component.literal(pendingWinResult.verdictTitle())
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                }
                ServerPlayer winnerPlayer = null;
                if (pendingWinResult.winnerUuid() != null) {
                    winnerPlayer = sl.getServer().getPlayerList().getPlayer(pendingWinResult.winnerUuid());
                }
                MahjongWinEffects.playWinEffects(
                        sl,
                        winnerPlayer,
                        pendingWinResult.winnerName(),
                        pendingWinResult.han(),
                        pendingWinResult.yakuman(),
                        pendingWinResult.yakuNames(),
                        pendingWinResult.yakumanNames());
                playResultStepSound(sl);
                resultAnimStage = ResultAnimStage.NONE;
                pendingWinResult = null;
                changed = true;
            }
            case NONE -> {
            }
        }
        if (changed) {
            setChanged();
            syncToClients();
        }
        return true;
    }

    private static String verdictTitleForWin(int han, boolean yakuman) {
        WinRules.VerdictTier tier = WinRules.verdictTier(han, yakuman);
        return switch (tier) {
            case YAKUMAN -> Component.translatable("riichi_mahjong_forge.result.verdict.yakuman").getString();
            case KAZOE_YAKUMAN -> Component.translatable("riichi_mahjong_forge.result.verdict.kazoe_yakuman").getString();
            case SANBAIMAN -> Component.translatable("riichi_mahjong_forge.result.verdict.sanbaiman").getString();
            case BAIMAN -> Component.translatable("riichi_mahjong_forge.result.verdict.baiman").getString();
            case HANEMAN -> Component.translatable("riichi_mahjong_forge.result.verdict.haneman").getString();
            case MANGAN -> Component.translatable("riichi_mahjong_forge.result.verdict.mangan").getString();
            case HAN -> Component.translatable("riichi_mahjong_forge.result.verdict.han", han).getString();
        };
    }

    private static String localizedRoundTitle(int handNumberOneBased) {
        return Component.translatable("riichi_mahjong_forge.result.round_title", handNumberOneBased).getString();
    }

    private static String localizedRoundTitleWithHonba(int handNumberOneBased, int honba) {
        if (honba <= 0) {
            return localizedRoundTitle(handNumberOneBased);
        }
        return Component.translatable(
                "riichi_mahjong_forge.result.round_title_honba", handNumberOneBased, honba).getString();
    }

    private void playResultStepSound(ServerLevel sl) {
        sl.playSound(
                null,
                worldPosition,
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.BLOCKS,
                0.65f,
                1.0f);
    }

    private void announceOverlayToPlayersInRadius(ServerLevel sl, Component msg) {
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        double r2 = 32.0 * 32.0;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(cx, cy, cz) <= r2) {
                p.displayClientMessage(msg, true);
            }
        }
    }

    private void announceBigTitleToPlayersInRadius(ServerLevel sl, Component msg) {
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        double r2 = 32.0 * 32.0;
        ClientboundSetTitlesAnimationPacket timings = new ClientboundSetTitlesAnimationPacket(8, 48, 14);
        ClientboundSetTitleTextPacket titlePacket = new ClientboundSetTitleTextPacket(msg);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(cx, cy, cz) <= r2) {
                p.connection.send(timings);
                p.connection.send(titlePacket);
            }
        }
    }

    public void tryStartNextHandFromCenter(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (matchPhase != TableMatchPhase.HAND_RESULT || gameState == null) {
            return;
        }
        Integer seat = seatIndexForPlayer(player);
        if (seat == null) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.not_seated"), true);
            return;
        }
        // Keep tabletop state visible in results mode; only collect and reshuffle after explicit center RMB.
        returnAllTilesToTableStorage();
        matchPhase = TableMatchPhase.IN_MATCH;
        clearHandResultState();
        if (!beginNewHand(sl)) {
            abortMatch(MatchAbortReason.GENERIC);
            notifySeatedHumans(
                    sl,
                    notEnoughTilesComponent(
                            countTileItemsInTableStorage(), requiredTilesToStartHand(enabledSeatCount())));
            return;
        }
        setChanged();
        syncToClients();
    }

    private void broadcastLifecycleToSeated(ServerLevel sl, S2CMatchLifecyclePacket packet) {
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            UUID id = occupantAt(seat);
            if (id == null) {
                continue;
            }
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                MahjongNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            }
        }
    }

    /**
     * Aborts an in-progress match stub: clears seats, returns to waiting, notifies seated players.
     * Also used when the table block is broken (waiting or match) so clients are not stuck.
     */
    public void abortMatch(MatchAbortReason reason) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        boolean wasInMatch = matchPhase == TableMatchPhase.IN_MATCH;
        if (wasInMatch) {
            sl.playSound(
                    null,
                    worldPosition,
                    SoundEvents.ANVIL_BREAK,
                    SoundSource.BLOCKS,
                    0.75f,
                    0.95f);
        }
        UUID[] snapshot = captureSeatOccupants();
        matchPhase = TableMatchPhase.WAITING;
        setTableLit(sl, false);
        matchRound = 0;
        gameState = null;
        clearRoundSetupState();
        resetSeatPlayerDelays();
        clearHandResultState();
        clearOpenMeldStorage();
        clearSection(INV_DISCARDS_START, DISCARDS_TOTAL_SLOTS);
        clearAllSeatOccupants();
        rebuildSeatPlayers();
        setChanged();
        syncToClients();
        boolean notifyAbort = wasInMatch || reason == MatchAbortReason.TABLE_BROKEN;
        if (notifyAbort) {
            S2CMatchLifecyclePacket packet =
                    new S2CMatchLifecyclePacket(sl.dimension(), worldPosition, false, reason, 0);
            for (UUID id : snapshot) {
                if (id == null) {
                    continue;
                }
                ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
                if (p != null) {
                    MahjongNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
                }
            }
        }
    }

    /** Called before the block entity is removed because the table block was destroyed. */
    public void onTableBlockRemoved() {
        abortMatch(MatchAbortReason.TABLE_BROKEN);
    }

    private MahjongSeatPlayer makeSeatPlayer(UUID uuid) {
        return BotSeatPlayer.isBotUuid(uuid) ? new BotSeatPlayer(uuid) : new HumanSeatPlayer(uuid);
    }

    private void assignSeat(int idx, @Nullable UUID uuid) {
        if (seatPlayers[idx] != null) seatPlayers[idx].onSeatVacated();
        setSeatOccupant(idx, uuid);
        seatPlayers[idx] = uuid != null ? makeSeatPlayer(uuid) : null;
    }

    private void rebuildSeatPlayers() {
        for (int i = 0; i < SEAT_COUNT; i++) {
            if (seatPlayers[i] != null) seatPlayers[i].onSeatVacated();
            seatPlayers[i] = occupantAt(i) != null ? makeSeatPlayer(occupantAt(i)) : null;
        }
    }

    /** Removes a player from their seat without aborting the whole table (lobby / waiting only). */
    public void clearSeatIfOccupied(UUID playerId) {
        Integer idx = findSeatIndex(playerId);
        if (idx == null) {
            return;
        }
        assignSeat(idx, null);
        setChanged();
        syncToClients();
    }

    /**
     * Top-of-table shortcut: fills every empty enabled seat with placeholder bot UUIDs (see
     * {@link BotSeatPlayer}), seats the clicking player on the first free enabled side if needed, then
     * {@link #tryStartMatchIfReady()}.
     */
    public void quickStartWithBotsFromTableTop(ServerPlayer human) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (!allowGameplay) {
            human.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.lobby.gameplay_disabled"), true);
            return;
        }
        if (matchPhase != TableMatchPhase.WAITING) {
            human.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.network.quick_start_bots_already"), true);
            return;
        }
        if (enabledSeatCount() < 2) {
            human.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.network.quick_start_bots_needs_two_sides"),
                    true);
            return;
        }
        int quickStartPlayers = enabledSeatCount();
        int quickStartRequiredTiles = requiredTilesToStartHand(quickStartPlayers);
        int totalTilesAfterConsolidation = countTileItemsAcrossAllGameplaySections();
        if (totalTilesAfterConsolidation < quickStartRequiredTiles) {
            human.displayClientMessage(
                    notEnoughTilesComponent(totalTilesAfterConsolidation, quickStartRequiredTiles), true);
            return;
        }
        returnAllTilesToTableStorageAndDropOverflow(sl);
        setChanged();
        syncToClients();
        if (!canBeginHandFromTableStorage(quickStartRequiredTiles)) {
            human.displayClientMessage(
                    notEnoughTilesComponent(countTileItemsInTableStorage(), quickStartRequiredTiles), true);
            return;
        }
        UUID[] snapshot = captureSeatOccupants();
        UUID hid = human.getUUID();
        if (findSeatIndex(hid) == null) {
            boolean placed = false;
            for (int i = 0; i < SEAT_COUNT; i++) {
                if (isSeatEnabled(i) && occupantAt(i) == null) {
                    assignSeat(i, hid);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                human.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.network.quick_start_bots_full"), true);
                return;
            }
        }
        for (int i = 0; i < SEAT_COUNT; i++) {
            if (isSeatEnabled(i) && occupantAt(i) == null) {
                assignSeat(i, BotSeatPlayer.uuidForSeat(i));
            }
        }
        setChanged();
        syncToClients();
        tryStartMatchIfReady();
        if (matchPhase == TableMatchPhase.IN_MATCH) {
            human.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.network.quick_start_bots_ok"), true);
        } else if (!canStartMatch()) {
            human.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.network.quick_start_bots_failed"), true);
        } else {
            // Defensive rollback: if match did not start after filling seats, restore pre-quickstart lobby state.
            restoreSeatOccupants(snapshot);
            rebuildSeatPlayers();
            setChanged();
            syncToClients();
        }
    }

    private boolean isTableFullForJoin() {
        return occupiedEnabledCount() >= enabledSeatCount() && enabledSeatCount() > 0;
    }

    /**
     * Stick + use: toggle whether this side participates (must leave ≥1 side open).
     */
    public void toggleSeatOpen(ServerPlayer player, int seatIndex) {
        if (matchPhase == TableMatchPhase.IN_MATCH) {
            abortMatch(MatchAbortReason.SIDE_CLOSED);
            return;
        }
        if (!isSeatEnabled(seatIndex)) {
            setSeatEnabledFlag(seatIndex, true);
            if (level instanceof ServerLevel sl) {
                sl.playSound(
                        null,
                        worldPosition,
                        SoundEvents.WOODEN_DOOR_OPEN,
                        SoundSource.BLOCKS,
                        0.8f,
                        1.0f);
            }
            setChanged();
            syncToClients();
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.lobby.side_opened", faceLabel(seatIndex)), true);
            return;
        }
        if (enabledSeatCount() <= 1) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.lobby.cannot_close_last_side"), true);
            return;
        }
        UUID removed = occupantAt(seatIndex);
        setSeatEnabledFlag(seatIndex, false);
        assignSeat(seatIndex, null);
        if (level instanceof ServerLevel sl) {
            sl.playSound(
                    null,
                    worldPosition,
                    SoundEvents.WOODEN_DOOR_CLOSE,
                    SoundSource.BLOCKS,
                    0.8f,
                    1.0f);
        }
        setChanged();
        syncToClients();
        player.displayClientMessage(
                Component.translatable("riichi_mahjong_forge.chat.lobby.side_closed", faceLabel(seatIndex)), true);
        if (removed != null && level instanceof ServerLevel serverLevel) {
            ServerPlayer displaced = serverLevel.getServer().getPlayerList().getPlayer(removed);
            if (displaced != null) {
                displaced.displayClientMessage(
                        Component.translatable(
                                "riichi_mahjong_forge.chat.lobby.removed_side_closed", faceLabel(seatIndex)),
                        true);
            }
        }
    }

    /**
     * Right-click face: lobby join/leave/switch. During a match, interactions are routed via
     * {@link #handleInMatchUse(ServerPlayer, int)} using the seated player's index (not the clicked rim).
     */
    public void handleFaceUse(ServerPlayer player, int seatIndex) {
        if (!allowGameplay) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.lobby.gameplay_disabled"), true);
            return;
        }
        if (!isSeatEnabled(seatIndex)) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.lobby.side_disabled"), true);
            return;
        }

        UUID pid = player.getUUID();
        Integer current = findSeatIndex(pid);

        if (matchPhase == TableMatchPhase.IN_MATCH) {
            if (current == null) {
                player.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.game.not_seated"), true);
                return;
            }
            handleInMatchUse(player, current);
            return;
        }

        UUID atSeat = occupantAt(seatIndex);

        boolean wasComplete = isAllEnabledSeatsFilled();

        if (current != null && current == seatIndex) {
            assignSeat(seatIndex, null);
            if (level instanceof ServerLevel sl) {
                sl.playSound(
                        null,
                        worldPosition,
                        SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.BLOCKS,
                        0.8f,
                        1.0f);
            }
            setChanged();
            syncToClients();
            player.displayClientMessage(Component.translatable("riichi_mahjong_forge.chat.lobby.left"), true);
            return;
        }

        if (atSeat != null && !atSeat.equals(pid)) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.lobby.seat_taken"), true);
            return;
        }

        if (atSeat == null && isTableFullForJoin()) {
            player.displayClientMessage(
                    Component.translatable(
                            "riichi_mahjong_forge.chat.lobby.table_full",
                            occupiedEnabledCount(),
                            enabledSeatCount()),
                    true);
            return;
        }

        if (current != null) {
            assignSeat(current, null);
            if (level instanceof ServerLevel sl) {
                sl.playSound(
                        null,
                        worldPosition,
                        SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.BLOCKS,
                        0.8f,
                        1.0f);
            }
        }

        assignSeat(seatIndex, pid);
        if (level instanceof ServerLevel sl) {
            sl.playSound(
                    null,
                    worldPosition,
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS,
                    0.7f,
                    1.0f);
        }
        setChanged();
        syncToClients();
        player.displayClientMessage(
                Component.translatable(
                        "riichi_mahjong_forge.chat.lobby.joined",
                        seatLabel(seatIndex),
                        faceLabel(seatIndex)),
                true);

        if (!wasComplete && isAllEnabledSeatsFilled()) {
            tryStartMatchIfReady();
            if (matchPhase == TableMatchPhase.WAITING) {
                notifyAllJoined(player);
            }
        }
    }

    /**
     * In-match use: resolves actions based on the player's seated index (the clicked rim is ignored for gameplay).
     */
    public void handleInMatchUse(ServerPlayer player, int seatIndex) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (!isSeatEnabled(seatIndex) || !Objects.equals(occupantAt(seatIndex), player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.use_own_side"), true);
            return;
        }
        if (gameState == null) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.state_missing"), true);
            return;
        }
        if (isInHandResultPhase()) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.next_hand_center_start"),
                    true);
            return;
        }
        if (gameState.isClaimWindowActive() && seatIndex != gameState.claimDiscarderSeat) {
            handleGameClaim(player, ClaimWindowRules.ClaimIntent.PASS, -1, -1);
            return;
        }
        if (tryHandleGameFaceUse(player, sl, seatIndex)) {
            return;
        }
    }

    public void handleSeatActionButton(ServerPlayer player, SeatAction action) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        Integer seat = findSeatIndex(player.getUUID());
        if (seat == null || !isSeatEnabled(seat) || !Objects.equals(occupantAt(seat), player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.not_seated"), true);
            return;
        }
        if (tryHandleSimpleClaimAction(player, action)) return;
        if (tryHandleChiAction(player, seat, action)) return;
        if (action == SeatAction.CANCEL && chiSelectionSeat == seat) {
            chiSelectionSeat = -1;
            setChanged();
            syncToClients();
            return;
        }
        if (gameState == null || isInHandResultPhase() || gameState.isClaimWindowActive()) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.claim_rejected"), true);
            return;
        }
        if (seat >= 0
                && seat < gameState.riichiPending.length
                && gameState.riichiPending[seat]) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.riichi_discard_first"), true);
            return;
        }
        ArrayDeque<Integer> concealed = readConcealedHandFromInventory(seat);
        MahjongSeatPlayer seatPlayer = (seat >= 0 && seat < SEAT_COUNT) ? seatPlayers[seat] : null;
        ArrayDeque<Integer> concealedForTsumo = concealedForTsumoCheck(seat, seatPlayer);
        ArrayList<Mentsu> melds = meldsAsMentsuList(seat);
        List<Integer> kanCandidates = MeldCandidateRules.kanCodes(concealed, openPonTileCodes(beMelds[seat]));
        int kanOptionIndex = kanOptionIndex(action);
        if (action == SeatAction.CANCEL) {
            if (closedKanSelectionSeat == seat) {
                closedKanSelectionSeat = -1;
                setChanged();
                syncToClients();
                return;
            }
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.claim_rejected"), true);
            return;
        }
        if (kanOptionIndex >= 0) {
            if (closedKanSelectionSeat != seat || kanOptionIndex >= kanCandidates.size()) {
                closedKanSelectionSeat = -1;
                player.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.game.claim_rejected"), true);
                setChanged();
                syncToClients();
                return;
            }
            int kanTileCode = kanCandidates.get(kanOptionIndex);
            closedKanSelectionSeat = -1;
            if (!applySeatKanForCode(sl, seat, kanTileCode)) {
                player.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.game.claim_rejected"), true);
                setChanged();
                syncToClients();
            }
            return;
        }
        if (action == SeatAction.TSUMO) {
            closedKanSelectionSeat = -1;
            MahjongGameState.ActionResult normalizeResult =
                    normalizeCurrentDrawnTileIntoHandZoneForHumanTurn(seat, seatPlayer);
            if (normalizeResult != MahjongGameState.ActionResult.OK) {
                player.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.game.claim_rejected"), true);
                return;
            }
            concealedForTsumo = concealedForTsumoCheck(seat, seatPlayer);
            if (!WinRules.isTsumoActionAvailable(
                    gameState.phase == MahjongGameState.TurnPhase.MUST_DISCARD,
                    gameState.currentTurnSeat == seat,
                    gameState.lastDrawnCode,
                    concealedForTsumo,
                    melds)) {
                player.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.game.claim_rejected"), true);
                return;
            }
            onTsumoResolved(sl, seat);
            return;
        }
        if (action == SeatAction.RIICHI || action == SeatAction.ANKAN) {
            if (action == SeatAction.RIICHI) {
                closedKanSelectionSeat = -1;
                if (seat >= 0
                        && seat < gameState.riichiDeclared.length
                        && gameState.riichiDeclared[seat]) {
                    player.displayClientMessage(
                            Component.translatable("riichi_mahjong_forge.chat.game.riichi_already_declared"), true);
                    return;
                }
                if (!WinRules.isRiichiActionAvailable(
                        gameState.phase == MahjongGameState.TurnPhase.MUST_DISCARD,
                        gameState.currentTurnSeat == seat,
                        gameState.lastDrawnCode,
                        concealed,
                        melds)) {
                    player.displayClientMessage(
                            Component.translatable("riichi_mahjong_forge.chat.game.claim_rejected"), true);
                    return;
                }
                gameState.riichiPending[seat] = true;
                logAtomicAction("RIICHI", seatLogLabel(seat) + " pending_discard");
                setChanged();
                syncToClients();
                player.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.game.riichi_choose_discard"), true);
                return;
            }
            if (kanCandidates.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.game.claim_rejected"), true);
                return;
            }
            closedKanSelectionSeat = seat;
            logAtomicAction("KAN_PICK", seatLogLabel(seat) + " options=" + kanCandidates.size());
            setChanged();
            syncToClients();
            return;
        }
    }

    private boolean tryHandleSimpleClaimAction(ServerPlayer player, SeatAction action) {
        ClaimWindowRules.ClaimIntent intent = switch (action) {
            case RON -> ClaimWindowRules.ClaimIntent.RON;
            case CHANKAN -> ClaimWindowRules.ClaimIntent.CHANKAN;
            case PASS -> ClaimWindowRules.ClaimIntent.PASS;
            case PON -> ClaimWindowRules.ClaimIntent.PON;
            case DAIMIN_KAN -> ClaimWindowRules.ClaimIntent.DAIMIN_KAN;
            default -> null;
        };
        if (intent == null) {
            return false;
        }
        closedKanSelectionSeat = -1;
        chiSelectionSeat = -1;
        handleGameClaim(player, intent, -1, -1);
        return true;
    }

    private boolean tryHandleChiAction(ServerPlayer player, int seat, SeatAction action) {
        if (action == SeatAction.CHI) {
            if (gameState == null || !gameState.isClaimWindowActive()) {
                rejectClaim(player);
                return true;
            }
            List<ClaimLegalityRules.ChiPair> chiPairs =
                    ClaimLegalityRules.findChiPairs(
                            readConcealedHandFromInventory(seat), gameState.claimTileCode);
            if (chiPairs.isEmpty()) {
                rejectClaim(player);
                return true;
            }
            if (chiPairs.size() == 1) {
                closedKanSelectionSeat = -1;
                chiSelectionSeat = -1;
                ClaimLegalityRules.ChiPair pair = chiPairs.get(0);
                handleGameClaim(player, ClaimWindowRules.ClaimIntent.CHI, pair.tileA(), pair.tileB());
                return true;
            }
            chiSelectionSeat = seat;
            setChanged();
            syncToClients();
            return true;
        }
        if (action != SeatAction.CHI_OPTION_1 && action != SeatAction.CHI_OPTION_2) {
            return false;
        }
        int chiIdx = chiOptionIndex(action);
        if (gameState == null || !gameState.isClaimWindowActive() || chiSelectionSeat != seat) {
            chiSelectionSeat = -1;
            rejectClaimAndSync(player);
            return true;
        }
        List<ClaimLegalityRules.ChiPair> chiPairs =
                ClaimLegalityRules.findChiPairs(
                        readConcealedHandFromInventory(seat), gameState.claimTileCode);
        if (chiIdx >= chiPairs.size()) {
            chiSelectionSeat = -1;
            rejectClaimAndSync(player);
            return true;
        }
        ClaimLegalityRules.ChiPair pair = chiPairs.get(chiIdx);
        chiSelectionSeat = -1;
        closedKanSelectionSeat = -1;
        handleGameClaim(player, ClaimWindowRules.ClaimIntent.CHI, pair.tileA(), pair.tileB());
        return true;
    }

    private void rejectClaim(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("riichi_mahjong_forge.chat.game.claim_rejected"), true);
    }

    private void rejectClaimAndSync(ServerPlayer player) {
        rejectClaim(player);
        setChanged();
        syncToClients();
    }

    private boolean isRiichiDiscardLegal(
            int seat, MahjongSeatPlayer seatPlayer, ServerLevel sl, int discardCode) {
        if (gameState == null || seat < 0 || seat >= SEAT_COUNT) {
            return false;
        }
        if (seat < gameState.riichiDeclared.length && gameState.riichiDeclared[seat]) {
            return discardCode >= 0 && discardCode <= 33 && discardCode == gameState.lastDrawnCode;
        }
        if (seat >= gameState.riichiPending.length || !gameState.riichiPending[seat]) {
            return true;
        }
        if (discardCode < 0 || discardCode > 33) {
            return false;
        }
        ArrayDeque<Integer> concealed = readConcealedHandFromInventory(seat);
        if (gameState.lastDrawnCode >= 0 && seatPlayer.hasTileOffTable(sl, gameState.lastDrawnCode)) {
            concealed.addLast(gameState.lastDrawnCode);
        }
        if (!concealed.removeFirstOccurrence(discardCode)) {
            return false;
        }
        return WinRules.isTenpai(concealed, meldsAsMentsuList(seat));
    }

    private ArrayDeque<Integer> concealedForTsumoCheck(int seat, @Nullable MahjongSeatPlayer seatPlayer) {
        ArrayDeque<Integer> concealed = readConcealedHandFromInventory(seat);
        if (gameState == null || seat < 0 || seat >= SEAT_COUNT) {
            return concealed;
        }
        if (gameState.phase != MahjongGameState.TurnPhase.MUST_DISCARD
                || gameState.currentTurnSeat != seat
                || gameState.lastDrawnCode < 0
                || gameState.lastDrawnCode > 33) {
            return concealed;
        }
        // Expected concealed count in MUST_DISCARD is derived from meld count as mentsu:
        // total hand structure is always 14 tiles = concealed + (3 per meld).
        int meldCount = meldsAsMentsuList(seat).size();
        int expectedConcealedWithDraw = Math.max(0, 14 - (meldCount * 3));
        if (concealed.size() >= expectedConcealedWithDraw) {
            return concealed;
        }
        if (seatPlayer == null) {
            concealed.addLast(gameState.lastDrawnCode);
            return concealed;
        }
        if (level instanceof ServerLevel sl && seatPlayer.hasTileOffTable(sl, gameState.lastDrawnCode)) {
            concealed.addLast(gameState.lastDrawnCode);
        }
        return concealed;
    }

    private MahjongGameState.ActionResult normalizeCurrentDrawnTileIntoHandZoneForHumanTurn(
            int seat, @Nullable MahjongSeatPlayer seatPlayer) {
        if (gameState == null || seat < 0 || seat >= SEAT_COUNT) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        if (seat != gameState.currentTurnSeat || gameState.phase != MahjongGameState.TurnPhase.MUST_DISCARD) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        if (gameState.lastDrawnCode < 0 || gameState.lastDrawnCode > 33) {
            return MahjongGameState.ActionResult.MISSING_DRAWN_TILE;
        }
        int meldCount = meldsAsMentsuList(seat).size();
        int expectedConcealedWithDraw = Math.max(0, 14 - (meldCount * 3));
        ArrayDeque<Integer> concealed = readConcealedHandFromInventory(seat);
        int concealedCount = concealed.size();
        if (!(level instanceof ServerLevel sl) || seatPlayer == null) {
            return concealedCount >= expectedConcealedWithDraw
                    ? MahjongGameState.ActionResult.OK
                    : MahjongGameState.ActionResult.WRONG_PHASE;
        }
        if (concealedCount >= expectedConcealedWithDraw) {
            // Human has a complete concealed zone already; any extra copy of the current draw in inventory means
            // we cannot prove "this draw" is represented in the authoritative hand slots.
            return seatPlayer.hasTileOffTable(sl, gameState.lastDrawnCode)
                    ? MahjongGameState.ActionResult.MISSING_DRAWN_TILE
                    : MahjongGameState.ActionResult.OK;
        }
        ItemStack moved = seatPlayer.takeTileOffTableForDiscard(sl, gameState.lastDrawnCode);
        if (moved == null || moved.isEmpty()) {
            return MahjongGameState.ActionResult.MISSING_DRAWN_TILE;
        }
        if (!addDrawnStackToHandHeldSlot(seat, moved)) {
            seatPlayer.tryStoreDrawnTileOffTable(sl, moved);
            return MahjongGameState.ActionResult.ILLEGAL_DISCARD;
        }
        sortSeatHandTilesByTileCode(seat);
        setChanged();
        syncToClients();
        return MahjongGameState.ActionResult.OK;
    }

    private boolean beginNewHand(ServerLevel sl) {
        if (gameState == null) {
            gameState = new MahjongGameState();
        }
        closedKanSelectionSeat = -1;
        chiSelectionSeat = -1;
        lastDiscardSeat = -1;
        lastDiscardSlotIndex = -1;
        lastMeldSeat = -1;
        lastMeldClaimedSlotIndex = -1;
        clearRoundSetupState();
        List<Integer> occupied = new ArrayList<>();
        for (int s = 0; s < SEAT_COUNT; s++) {
            if (isSeatEnabled(s) && occupantAt(s) != null) {
                occupied.add(s);
            }
        }
        if (occupied.size() < 2) {
            abortMatch(MatchAbortReason.GENERIC);
            return false;
        }
        int dealerSeat = occupied.get(0);
        List<Integer> order = TurnOrderRules.counterClockwisePlayOrder(SEAT_COUNT, dealerSeat, occupied);
        Random rng =
                new Random(
                        sl.getSeed()
                                ^ worldPosition.asLong()
                                ^ ((long) gameState.handNumber << 32)
                                ^ 0xC0FFEE92DEF00D15L);
        int dealNeeded = RoundSetupRules.requiredDealtTilesForPlayers(order.size());
        int requiredTiles = requiredTilesToStartHand(order.size());
        ArrayList<ItemStack> tileStacks = extractTileStacksFromTableStorage(requiredTiles);
        if (tileStacks == null) {
            logAtomicAction(
                    "HAND_START_FAIL",
                    "players=" + order.size() + " required=" + requiredTiles + " have=" + countTileItemsInTableStorage());
            return false;
        }
        Collections.shuffle(tileStacks, rng);
        PreparedWall prepared = prepareFromShuffledWallStacks(tileStacks, order);
        if (prepared == null) {
            return false;
        }
        gameState.beginHandFromPrepared(dealerSeat);
        logAtomicAction(
                "HAND_START",
                "players="
                        + order.size()
                        + " dealt="
                        + dealNeeded
                        + " tail="
                        + Math.max(0, tileStacks.size() - dealNeeded));
        beginRoundSetupAnimation(prepared);
        setChanged();
        syncToClients();
        return true;
    }

    private void beginRoundSetupAnimation(PreparedWall prepared) {
        clearSection(INV_PLAYER_ZONE_START, PLAYER_ZONE_TOTAL_SLOTS);
        clearSection(INV_WALL_START, WALL_SLOTS);
        clearSection(INV_DEAD_WALL_START, DEAD_WALL_SLOTS);
        clearOpenMeldStorage();
        clearSection(INV_DISCARDS_START, DISCARDS_TOTAL_SLOTS);

        roundSetupLiveWallQueue.clear();
        roundSetupLiveWallQueue.addAll(prepared.liveWall);

        roundSetupDeadWallQueue.clear();
        roundSetupHandQueue.clear();
        doraHiddenDuringSetup = true;
        uraDoraRevealed = false;

        for (int i = 0; i < Math.min(DEAD_WALL_SLOTS, prepared.deadWallStacks.length); i++) {
            ItemStack st = prepared.deadWallStacks[i];
            if (st.isEmpty()) {
                continue;
            }
            roundSetupDeadWallQueue.addLast(new DeadWallPlacement(i, st));
        }

        if (gameState != null) {
            for (int handIdx = 0; handIdx < 13; handIdx += 1) {
                for (int seat : deterministicPlayOrder()) {
                    if (seat < 0 || seat >= SEAT_COUNT) {
                        continue;
                    }
                    List<ItemStack> seatHand = prepared.dealtHandStacks[seat];
                    if (handIdx < seatHand.size()) {
                        roundSetupHandQueue.addLast(new HandPlacement(seat, seatHand.get(handIdx)));
                    }
                }
            }
        }

        roundSetupStage = RoundSetupStage.BUILD_WALL;
    }

    private void clearRoundSetupState() {
        roundSetupStage = RoundSetupStage.NONE;
        roundSetupLiveWallQueue.clear();
        roundSetupDeadWallQueue.clear();
        roundSetupHandQueue.clear();
    }

    private boolean isRoundSetupInProgress() {
        return roundSetupStage != RoundSetupStage.NONE;
    }

    private int firstEmptySlotInRange(int start, int count) {
        int end = Math.min(inventory.size(), start + count);
        for (int slot = start; slot < end; slot++) {
            if (inventory.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private boolean tickRoundSetup(ServerLevel sl) {
        if (!isRoundSetupInProgress()) {
            return false;
        }
        boolean changed = false;
        switch (roundSetupStage) {
            case BUILD_WALL -> {
                int placed = 0;
                while (placed < ROUND_SETUP_WALL_BATCH && !roundSetupLiveWallQueue.isEmpty()) {
                    int slot = firstEmptySlotInRange(INV_WALL_START, WALL_SLOTS);
                    if (slot < 0) {
                        // No empty slot — wall is already full; discard remaining queue items to avoid a permanent stuck state.
                        roundSetupLiveWallQueue.clear();
                        break;
                    }
                    inventory.set(slot, roundSetupLiveWallQueue.removeFirst());
                    placed++;
                    changed = true;
                }
                if (roundSetupLiveWallQueue.isEmpty()) {
                    roundSetupStage = RoundSetupStage.BUILD_DEAD_WALL;
                }
            }
            case BUILD_DEAD_WALL -> {
                int placed = 0;
                while (placed < ROUND_SETUP_DEAD_WALL_BATCH && !roundSetupDeadWallQueue.isEmpty()) {
                    DeadWallPlacement p = roundSetupDeadWallQueue.removeFirst();
                    int slot = INV_DEAD_WALL_START + p.deadWallOffset();
                    if (slot >= INV_DEAD_WALL_START && slot < INV_DEAD_WALL_START + DEAD_WALL_SLOTS) {
                        inventory.set(slot, p.stack());
                        placed++;
                        changed = true;
                    }
                }
                if (roundSetupDeadWallQueue.isEmpty()) {
                    roundSetupStage = RoundSetupStage.DEAL_HANDS;
                }
            }
            case DEAL_HANDS -> {
                int dealt = 0;
                while (dealt < ROUND_SETUP_HANDS_PER_TICK && !roundSetupHandQueue.isEmpty()) {
                    HandPlacement hp = roundSetupHandQueue.removeFirst();
                    if (addStackToHandInventory(hp.seat(), hp.stack())) {
                        dealt++;
                        changed = true;
                    }
                }
                if (roundSetupHandQueue.isEmpty()) {
                    roundSetupStage = RoundSetupStage.REVEAL_DORA;
                }
            }
            case REVEAL_DORA -> {
                if (doraHiddenDuringSetup) {
                    doraHiddenDuringSetup = false;
                    changed = true;
                }
                if (sortAllSeatHandsByTileCode()) {
                    changed = true;
                }
                clearRoundSetupState();
                tickActiveTurnSeat(sl);
            }
            case NONE -> {
            }
        }
        if (changed) {
            playTilePlaceSound(sl);
            setChanged();
            syncToClients();
        }
        return true;
    }

    private void markRoundResolvedForAltar(ServerLevel sl, int han) {
        MinecraftForge.EVENT_BUS.post(new MahjongRoundResolvedEvent(sl, worldPosition, han));
    }

    private void onExhaustiveDraw(ServerLevel sl) {
        if (gameState == null) {
            return;
        }
        markRoundResolvedForAltar(sl, 0);
        logAtomicAction("HAND_END", "RYUUKYOKU_EXHAUST");
        sl.playSound(
                null,
                worldPosition,
                SoundEvents.AMETHYST_BLOCK_PLACE,
                SoundSource.BLOCKS,
                0.85f,
                1.0f);
        int drawHonba = gameState.honba;
        gameState.honba++;
        gameState.handNumber++;
        matchRound = gameState.handNumber;
        String roundTitle = localizedRoundTitleWithHonba(matchRound, drawHonba);
        setHandResultState(
                roundTitle,
                Component.translatable("riichi_mahjong_forge.result.header.ryuukyoku", roundTitle).getString(),
                0xE0E0E0,
                List.of(Component.translatable("riichi_mahjong_forge.result.draw.no_winner").getString()),
                List.of(Component.translatable("riichi_mahjong_forge.result.draw.all_zero").getString()),
                -1);
        announceBigTitleToPlayersInRadius(
                sl,
                Component.translatable("riichi_mahjong_forge.chat.game.overlay.exhaustive_draw"));
        setChanged();
        syncToClients();
    }

    private void writeSectionsForNewHand(
            List<ItemStack>[] dealtHandStacks, ArrayDeque<ItemStack> liveWall, ItemStack[] deadWallStacks) {
        clearSection(INV_PLAYER_ZONE_START, PLAYER_ZONE_TOTAL_SLOTS);
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            int base = playerZoneBase(seat);
            int k = 0;
            for (ItemStack st : dealtHandStacks[seat]) {
                if (k >= PLAYER_ZONE_SLOTS_PER_SEAT) {
                    break;
                }
                inventory.set(base + k, st);
                k++;
            }
        }
        clearSection(INV_WALL_START, WALL_SLOTS);
        int wi = 0;
        for (ItemStack st : liveWall) {
            if (wi >= WALL_SLOTS) {
                break;
            }
            inventory.set(INV_WALL_START + wi, st);
            wi++;
        }
        clearSection(INV_DEAD_WALL_START, DEAD_WALL_SLOTS);
        for (int i = 0; i < Math.min(DEAD_WALL_SLOTS, deadWallStacks.length); i++) {
            inventory.set(INV_DEAD_WALL_START + i, deadWallStacks[i]);
        }
        clearOpenMeldStorage();
        clearSection(INV_DISCARDS_START, DISCARDS_TOTAL_SLOTS);
    }

    private void notifySeatedHumans(ServerLevel sl, Component msg) {
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            UUID id = occupantAt(seat);
            if (id == null) continue;
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.displayClientMessage(msg, true);
            }
        }
    }

    private int requiredTilesToStartHand(int playerCount) {
        if (!allowCustomTilePack) {
            return RoundSetupRules.FULL_TILE_SET_SIZE;
        }
        return RoundSetupRules.requiredDealtTilesForPlayers(playerCount);
    }

    /** Needs {@code requiredTiles} in storage; hand/wall/discard zones empty (junk in storage ignored). */
    private boolean canBeginHandFromTableStorage(int requiredTiles) {
        if (!RoundSetupRules.isDealNeededValid(requiredTiles)) {
            return false;
        }
        int have = 0;
        for (int i = 0; i < TILES_IN_TABLE_SLOTS; i++) {
            ItemStack st = inventory.get(INV_TILES_IN_TABLE_START + i);
            if (!st.isEmpty() && MahjongTileItems.codeForItem(st.getItem()) != null) {
                have++;
            }
        }
        if (have < requiredTiles) {
            return false;
        }
        if (!isSectionEmpty(INV_PLAYER_ZONE_START, PLAYER_ZONE_TOTAL_SLOTS)) {
            return false;
        }
        if (!isSectionEmpty(INV_WALL_START, WALL_SLOTS)) {
            return false;
        }
        if (!isSectionEmpty(INV_DEAD_WALL_START, DEAD_WALL_SLOTS)) {
            return false;
        }
        if (!isSectionEmpty(INV_DISCARDS_START, DISCARDS_TOTAL_SLOTS)) {
            return false;
        }
        return true;
    }

    private boolean isSectionEmpty(int start, int count) {
        for (int i = 0; i < count; i++) {
            if (!inventory.get(start + i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private ArrayList<ItemStack> extractTileStacksFromTableStorage(int requiredTiles) {
        if (!canBeginHandFromTableStorage(requiredTiles)) {
            return null;
        }
        ArrayList<ItemStack> out = new ArrayList<>(TILES_IN_TABLE_SLOTS);
        // Extract only recognized tile items; leave junk items in storage untouched.
        for (int i = 0; i < TILES_IN_TABLE_SLOTS; i++) {
            int slot = INV_TILES_IN_TABLE_START + i;
            ItemStack st = inventory.get(slot);
            if (st.isEmpty()) {
                continue;
            }
            if (MahjongTileItems.codeForItem(st.getItem()) == null) {
                continue;
            }
            out.add(st);
            inventory.set(slot, ItemStack.EMPTY);
        }
        return out;
    }

    private static final class PreparedWall {
        final List<ItemStack>[] dealtHandStacks;
        final ArrayDeque<ItemStack> liveWall;
        final ItemStack[] deadWallStacks;

        private PreparedWall(List<ItemStack>[] dealtHandStacks, ArrayDeque<ItemStack> liveWall, ItemStack[] deadWallStacks) {
            this.dealtHandStacks = dealtHandStacks;
            this.liveWall = liveWall;
            this.deadWallStacks = deadWallStacks;
        }
    }

    /**
     * Deals {@code 13} per seat from the first {@code 13N+1} shuffled stacks; the extra stack is the dealer's first
     * wall draw (queued right after the dead-wall prefix). No items created/destroyed.
     */
    @Nullable
    private static PreparedWall prepareFromShuffledWallStacks(List<ItemStack> tiles, List<Integer> playOrder) {
        int n = playOrder.size();
        if (!RoundSetupRules.isValidPlayOrder(playOrder, SEAT_COUNT)) {
            return null;
        }
        RoundSetupRules.WallPartitionPlan partition =
                RoundSetupRules.wallPartitionPlan(TILES_IN_TABLE_SLOTS, n, DEAD_WALL_SLOTS);
        int dealNeeded = partition.dealNeeded();
        if (tiles.size() < dealNeeded) {
            return null;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<ItemStack>[] dealt = new List[SEAT_COUNT];
        for (int s = 0; s < SEAT_COUNT; s++) {
            dealt[s] = new ArrayList<>();
        }
        int liveTailCount = partition.liveTailCount();
        int deadWallCount = partition.deadWallCount();
        ItemStack[] deadWallStacks = new ItemStack[RoundSetupRules.DEAD_WALL_SIZE];
        Arrays.fill(deadWallStacks, ItemStack.EMPTY);

        ArrayDeque<ItemStack> build = new ArrayDeque<>(dealNeeded);
        for (int i = 0; i < dealNeeded; i++) {
            build.addLast(tiles.get(i));
        }
        List<Integer> dealSeatSequence = RoundSetupRules.initialDealSeatSequence(playOrder);
        if (dealSeatSequence.isEmpty()) {
            return null;
        }
        for (int seat : dealSeatSequence) {
            dealt[seat].add(build.removeFirst());
        }
        if (build.size() != 1) {
            return null;
        }
        ItemStack dealerFirstLiveDraw = build.removeFirst();

        List<ItemStack> tail = tiles.subList(dealNeeded, tiles.size());
        ArrayDeque<ItemStack> live = new ArrayDeque<>(liveTailCount + 1);
        // First draw after initial hands is always dealer's draw tile.
        live.addLast(dealerFirstLiveDraw);
        for (int i = 0; i < liveTailCount; i++) {
            live.addLast(tail.get(i));
        }
        for (int i = 0; i < deadWallCount; i++) {
            deadWallStacks[i] = tail.get(liveTailCount + i);
        }
        return new PreparedWall(dealt, live, deadWallStacks);
    }

    private ArrayDeque<Integer> readConcealedHandFromInventory(int seat) {
        ArrayDeque<Integer> out = new ArrayDeque<>();
        if (seat < 0 || seat >= SEAT_COUNT) {
            return out;
        }
        int base = playerZoneBase(seat);
        for (int i = 0; i < PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
            ItemStack st = inventory.get(base + i);
            if (st.isEmpty()) {
                continue;
            }
            Integer code = MahjongTileItems.codeForItem(st.getItem());
            if (code != null) {
                out.addLast(code);
            }
        }
        return out;
    }

    /** Visible concealed-zone tile codes for the seat (synced to clients). */
    public ArrayDeque<Integer> visibleConcealedHandCodes(int seat) {
        return readConcealedHandFromInventory(seat);
    }

    private boolean sortSeatHandTilesByTileCode(int seat) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return false;
        }
        int base = playerZoneBase(seat);
        ArrayList<Integer> tileCodes = new ArrayList<>(PLAYER_ZONE_SLOTS_PER_SEAT);
        ArrayList<ItemStack> nonTileStacks = new ArrayList<>(PLAYER_ZONE_SLOTS_PER_SEAT);
        for (int i = 0; i < PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
            ItemStack st = inventory.get(base + i);
            if (st.isEmpty()) {
                continue;
            }
            Integer code = MahjongTileItems.codeForItem(st.getItem());
            if (code != null) {
                tileCodes.add(code);
            } else {
                nonTileStacks.add(st.copy());
            }
        }
        HandCodeRules.sortInPlace(tileCodes);
        ArrayList<ItemStack> sorted = new ArrayList<>(PLAYER_ZONE_SLOTS_PER_SEAT);
        for (int code : tileCodes) {
            sorted.add(stackForCode(code));
        }
        sorted.addAll(nonTileStacks);
        while (sorted.size() < PLAYER_ZONE_SLOTS_PER_SEAT) {
            sorted.add(ItemStack.EMPTY);
        }
        boolean changed = false;
        for (int i = 0; i < PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
            int slot = base + i;
            ItemStack oldStack = inventory.get(slot);
            ItemStack newStack = sorted.get(i);
            if (oldStack.getCount() != newStack.getCount() || !ItemStack.isSameItemSameTags(oldStack, newStack)) {
                changed = true;
            }
            inventory.set(slot, newStack);
        }
        return changed;
    }

    private boolean sortAllSeatHandsByTileCode() {
        boolean changed = false;
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            if (sortSeatHandTilesByTileCode(seat)) {
                changed = true;
            }
        }
        return changed;
    }

    private ArrayDeque<Integer> readDiscardsFromInventory(int seat) {
        ArrayDeque<Integer> out = new ArrayDeque<>();
        if (seat < 0 || seat >= SEAT_COUNT) {
            return out;
        }
        int base = discardBase(seat);
        for (int i = 0; i < DISCARDS_SLOTS_PER_SEAT; i++) {
            ItemStack st = inventory.get(base + i);
            if (st.isEmpty()) {
                continue;
            }
            Integer code = MahjongTileItems.codeForItem(st.getItem());
            if (code != null) {
                out.addLast(code);
            }
        }
        return out;
    }

    private boolean appendDiscardStackToInventory(int seat, ItemStack st) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return false;
        }
        if (st.isEmpty()) {
            return false;
        }
        int base = discardBase(seat);
        for (int i = 0; i < DISCARDS_SLOTS_PER_SEAT; i++) {
            int slot = base + i;
            if (inventory.get(slot).isEmpty()) {
                inventory.set(slot, st);
                lastDiscardSeat = seat;
                lastDiscardSlotIndex = i;
                lastMeldSeat = -1;
                lastMeldClaimedSlotIndex = -1;
                if (level instanceof ServerLevel sl && !level.isClientSide()) {
                    playTilePlaceSound(sl);
                }
                return true;
            }
        }
        return false;
    }

    private void removeLastDiscardIfMatches(int seat, int tileCode) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return;
        }
        int base = discardBase(seat);
        for (int i = DISCARDS_SLOTS_PER_SEAT - 1; i >= 0; i--) {
            int slot = base + i;
            ItemStack st = inventory.get(slot);
            if (st.isEmpty()) {
                continue;
            }
            Integer code = MahjongTileItems.codeForItem(st.getItem());
            if (code != null && code == tileCode) {
                inventory.set(slot, ItemStack.EMPTY);
            }
            return;
        }
    }

    /** Removes up to {@code tilesToRemove} matching stacks from the seat's hand zone on the table. */
    private boolean removeTilesFromSeatHand(int seat, int tileCode, int tilesToRemove) {
        if (tilesToRemove <= 0) {
            return true;
        }
        if (seat < 0 || seat >= SEAT_COUNT) {
            return false;
        }
        int removed = 0;
        int base = playerZoneBase(seat);
        for (int i = 0; i < PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
            int slot = base + i;
            ItemStack st = inventory.get(slot);
            if (st.isEmpty()) {
                continue;
            }
            Integer code = MahjongTileItems.codeForItem(st.getItem());
            if (code != null && code == tileCode) {
                inventory.set(slot, ItemStack.EMPTY);
                removed++;
                if (removed >= tilesToRemove) {
                    return true;
                }
            }
        }
        return false;
    }

    private void clearOpenMeldStorage() {
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            beMelds[seat].clear();
        }
        clearSection(INV_OPEN_MELD_START, OPEN_MELD_TOTAL_SLOTS);
    }

    private void syncMeldTilesFromMetadataToInventory() {
        clearSection(INV_OPEN_MELD_START, OPEN_MELD_TOTAL_SLOTS);
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            int slot = INV_OPEN_MELD_START + seat * OPEN_MELD_SLOTS_PER_SEAT;
            for (MahjongMeld meld : beMelds[seat]) {
                for (int code : meld.tileCodes()) {
                    if (slot >= INV_OPEN_MELD_START + (seat + 1) * OPEN_MELD_SLOTS_PER_SEAT) {
                        break;
                    }
                    inventory.set(slot++, stackForCode(code));
                }
            }
        }
    }

    private void mutateSeatMelds(int seat, Consumer<List<MahjongMeld>> mutation) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return;
        }
        mutation.accept(beMelds[seat]);
        syncMeldTilesFromMetadataToInventory();
    }

    private void addSeatMeld(int seat, MahjongMeld meld) {
        mutateSeatMelds(seat, seatMelds -> seatMelds.add(meld));
    }

    /** Returns the inventory slot offset (relative to seat's meld base) of the last tile in the last meld. */
    private int computeLastMeldClaimedSlotIndex(int seat) {
        int offset = 0;
        for (MahjongMeld m : beMelds[seat]) {
            offset += m.tileCodes().length;
        }
        return offset - 1;
    }

    private void clearSeatMelds(int seat) {
        mutateSeatMelds(seat, List::clear);
    }

    private int totalTilesHeldFromInventoryAndMelds(int seat) {
        int n = 0;
        int base = playerZoneBase(seat);
        for (int i = 0; i < PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
            ItemStack st = inventory.get(base + i);
            if (!st.isEmpty() && MahjongTileItems.codeForItem(st.getItem()) != null) {
                n++;
            }
        }
        for (MahjongMeld m : beMelds[seat]) {
            n += m.tileCodes().length;
        }
        return n;
    }

    private boolean addStackToHandInventory(int seat, ItemStack st) {
        int base = playerZoneBase(seat);
        for (int i = 0; i < PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
            int slot = base + i;
            if (inventory.get(slot).isEmpty()) {
                inventory.set(slot, st);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private ItemStack removeOneStackFromHandInventoryByCode(int seat, int tileCode) {
        int base = playerZoneBase(seat);
        for (int i = 0; i < PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
            int slot = base + i;
            ItemStack st = inventory.get(slot);
            if (st.isEmpty()) {
                continue;
            }
            Integer code = MahjongTileItems.codeForItem(st.getItem());
            if (code != null && code == tileCode) {
                inventory.set(slot, ItemStack.EMPTY);
                return st;
            }
        }
        return null;
    }

    @Nullable
    private ItemStack removeLastStackFromHandInventory(int seat) {
        int base = playerZoneBase(seat);
        for (int i = PLAYER_ZONE_SLOTS_PER_SEAT - 1; i >= 0; i--) {
            int slot = base + i;
            ItemStack st = inventory.get(slot);
            if (st.isEmpty()) {
                continue;
            }
            inventory.set(slot, ItemStack.EMPTY);
            return st;
        }
        return null;
    }


    private ItemStack popLiveWallStackFromInventory() {
        for (int i = 0; i < WALL_SLOTS; i++) {
            int slot = INV_WALL_START + i;
            ItemStack st = inventory.get(slot);
            if (!st.isEmpty()) {
                inventory.set(slot, ItemStack.EMPTY);
                Integer code = MahjongTileItems.codeForItem(st.getItem());
                logAtomicAction(
                        "DRAW_FROM_WALL",
                        "slot=" + i + " tile=" + tileLabel(code == null ? -1 : code));
                return st;
            }
        }
        logAtomicAction("DRAW_FROM_WALL", "slot=none tile=empty_wall");
        return ItemStack.EMPTY;
    }

    private ItemStack popDeadWallStackFromInventory() {
        for (int i = 0; i < DEAD_WALL_SLOTS; i++) {
            int slot = INV_DEAD_WALL_START + i;
            ItemStack st = inventory.get(slot);
            if (!st.isEmpty()) {
                inventory.set(slot, ItemStack.EMPTY);
                Integer code = MahjongTileItems.codeForItem(st.getItem());
                logAtomicAction(
                        "DRAW_FROM_DEAD_WALL",
                        "slot=" + i + " tile=" + tileLabel(code == null ? -1 : code));
                return st;
            }
        }
        logAtomicAction("DRAW_FROM_DEAD_WALL", "slot=none tile=empty_dead_wall");
        return ItemStack.EMPTY;
    }

    private boolean applyRinshanDrawAfterKan(ServerLevel sl, @Nullable MahjongSeatPlayer seatPlayer, int seat) {
        if (gameState == null) {
            return false;
        }
        ItemStack drawn = popDeadWallStackFromInventory();
        if (drawn.isEmpty()) {
            return false;
        }
        Integer codeObj = MahjongTileItems.codeForItem(drawn.getItem());
        if (codeObj == null) {
            return false;
        }
        int code = codeObj;
        boolean placedSomewhere = seatPlayer != null && seatPlayer.tryStoreDrawnTileOffTable(sl, drawn);
        if (!placedSomewhere) {
            if (!addDrawnStackToHandHeldSlot(seat, drawn)) {
                return false;
            }
        }
        gameState.lastDrawnCode = code;
        gameState.lastDrawWasRinshan = true;
        gameState.phase = MahjongGameState.TurnPhase.MUST_DISCARD;
        playTilePlaceSound(sl);
        logAtomicAction("RINSHAN_DRAW", seatLogLabel(seat) + " tile=" + tileLabel(code));
        return true;
    }

    public int getLiveWallRemainingFromInventory() {
        int n = 0;
        for (int i = 0; i < WALL_SLOTS; i++) {
            ItemStack st = inventory.get(INV_WALL_START + i);
            if (!st.isEmpty() && MahjongTileItems.codeForItem(st.getItem()) != null) {
                n++;
            }
        }
        return n;
    }

    public int getDoraIndicatorCodeFromInventory() {
        int slot = INV_DEAD_WALL_START + RoundSetupRules.DORA_INDICATOR_DEAD_WALL_INDEX;
        if (slot < INV_DEAD_WALL_START || slot >= INV_DEAD_WALL_START + DEAD_WALL_SLOTS) {
            return -1;
        }
        ItemStack st = inventory.get(slot);
        if (st.isEmpty()) {
            return -1;
        }
        Integer code = MahjongTileItems.codeForItem(st.getItem());
        return code == null ? -1 : code;
    }

    public List<Integer> getDoraIndicatorCodesForScoring() {
        int kanCount = Math.max(0, declaredKanCountFromMelds());
        int indicatorCount = Math.min(1 + kanCount, 5);
        ArrayList<Integer> out = new ArrayList<>(indicatorCount);
        for (int i = 0; i < indicatorCount; i++) {
            int localDeadWallSlot = RoundSetupRules.DORA_INDICATOR_DEAD_WALL_INDEX + (i * 2);
            int slot = INV_DEAD_WALL_START + localDeadWallSlot;
            if (slot < INV_DEAD_WALL_START || slot >= INV_DEAD_WALL_START + DEAD_WALL_SLOTS) {
                break;
            }
            ItemStack st = inventory.get(slot);
            if (st.isEmpty()) {
                continue;
            }
            Integer code = MahjongTileItems.codeForItem(st.getItem());
            if (code != null) {
                out.add(code);
            }
        }
        return out;
    }

    private static ItemStack stackForCode(int code) {
        var item = MahjongTileItems.itemForCode(code);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private void clearSection(int start, int count) {
        for (int i = 0; i < count; i++) {
            inventory.set(start + i, ItemStack.EMPTY);
        }
    }

    private void tickActiveTurnSeat(ServerLevel sl) {
        if (gameState == null) return;
        int seat = gameState.currentTurnSeat;
        if (seat >= 0 && seat < SEAT_COUNT && isSeatEnabled(seat) && seatPlayers[seat] != null) {
            seatPlayers[seat].tick(sl, this, seat);
        }
    }

    public boolean tryAutoDiscardForRiichi(ServerLevel sl, int seat) {
        if (gameState == null || gameState.phase != MahjongGameState.TurnPhase.MUST_DISCARD) {
            return false;
        }
        if (seat < 0 || seat >= gameState.riichiDeclared.length || !gameState.riichiDeclared[seat]) {
            return false;
        }
        MahjongSeatPlayer seatPlayer =
                (seat >= 0 && seat < SEAT_COUNT) ? seatPlayers[seat] : null;
        if (seatPlayer == null) {
            return false;
        }
        ArrayDeque<Integer> concealed = readConcealedHandFromInventory(seat);
        if (WinRules.isTsumoActionAvailable(
                gameState.phase == MahjongGameState.TurnPhase.MUST_DISCARD,
                gameState.currentTurnSeat == seat,
                gameState.lastDrawnCode,
                concealedForTsumoCheck(seat, seatPlayer),
                meldsAsMentsuList(seat))) {
            return false;
        }
        if (!MeldCandidateRules.kanCodes(concealed, openPonTileCodes(beMelds[seat])).isEmpty()) {
            return false;
        }
        MahjongGameState.ActionResult r = tryPerformSeatDiscard(sl, seat, seatPlayer);
        if (r != MahjongGameState.ActionResult.OK) {
            return false;
        }
        logAtomicAction("AUTO_DISCARD_RIICHI", seatLogLabel(seat));
        tickActiveTurnSeat(sl);
        return true;
    }

    private MahjongGameState.ActionResult tryDrawPhysical(
            ServerLevel sl, int actorPhysicalSeat, MahjongSeatPlayer seatPlayer, ItemStack drawn) {
        if (gameState == null) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        if (actorPhysicalSeat != gameState.currentTurnSeat) {
            return MahjongGameState.ActionResult.NOT_YOUR_TURN;
        }
        if (gameState.phase == MahjongGameState.TurnPhase.CLAIM_WINDOW) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        if (gameState.phase != MahjongGameState.TurnPhase.MUST_DRAW) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        if (drawn.isEmpty()) {
            logAtomicAction("DRAW_FAIL", seatLogLabel(actorPhysicalSeat) + " reason=empty_wall");
            return MahjongGameState.ActionResult.EMPTY_WALL;
        }
        Integer codeObj = MahjongTileItems.codeForItem(drawn.getItem());
        if (codeObj == null) {
            logAtomicAction("DRAW_FAIL", seatLogLabel(actorPhysicalSeat) + " reason=non_tile");
            return MahjongGameState.ActionResult.EMPTY_WALL;
        }
        int code = codeObj;
        if (totalTilesHeldFromInventoryAndMelds(gameState.currentTurnSeat) != 13 + kanMeldCountForSeat(gameState.currentTurnSeat)) {
            logAtomicAction(
                    "DRAW_FAIL",
                    seatLogLabel(actorPhysicalSeat)
                            + " reason=bad_hand_count count="
                            + totalTilesHeldFromInventoryAndMelds(gameState.currentTurnSeat));
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        boolean placedOffTable = seatPlayer.tryStoreDrawnTileOffTable(sl, drawn);
        boolean placedSomewhere = placedOffTable;
        if (!placedSomewhere) {
            if (!addDrawnStackToHandHeldSlot(gameState.currentTurnSeat, drawn)) {
                logAtomicAction("DRAW_FAIL", seatLogLabel(actorPhysicalSeat) + " reason=no_hand_slot");
                return MahjongGameState.ActionResult.WRONG_PHASE;
            }
        }
        gameState.lastDrawnCode = code;
        gameState.lastDrawWasRinshan = false;
        gameState.phase = MahjongGameState.TurnPhase.MUST_DISCARD;
        logAtomicAction(
                "DRAW_OK",
                seatLogLabel(actorPhysicalSeat)
                        + " tile="
                        + tileLabel(code)
                        + (placedOffTable ? " target=off_table" : " target=table_hand"));
        playTilePlaceSound(sl);
        return MahjongGameState.ActionResult.OK;
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

    private MahjongGameState.ActionResult tryDiscardPhysical(
            ServerLevel sl, int actorPhysicalSeat, MahjongSeatPlayer seatPlayer) {
        if (gameState == null) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        if (actorPhysicalSeat != gameState.currentTurnSeat) {
            return MahjongGameState.ActionResult.NOT_YOUR_TURN;
        }
        if (gameState.phase != MahjongGameState.TurnPhase.MUST_DISCARD) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        ItemStack discarded;
        Integer discardCode;
        if (gameState.lastDrawnCode >= 0) {
            int held = totalTilesHeldFromInventoryAndMelds(gameState.currentTurnSeat);
            boolean drawnOffTable = seatPlayer.hasTileOffTable(sl, gameState.lastDrawnCode);
            int kanBonus = kanMeldCountForSeat(gameState.currentTurnSeat);
            if (drawnOffTable && held != 13 + kanBonus && held != 14 + kanBonus) {
                return MahjongGameState.ActionResult.WRONG_PHASE;
            }
            if (!drawnOffTable && held != 14 + kanBonus) {
                return MahjongGameState.ActionResult.WRONG_PHASE;
            }
            discardCode = gameState.lastDrawnCode;
            if (!isRiichiDiscardLegal(gameState.currentTurnSeat, seatPlayer, sl, discardCode)) {
                return MahjongGameState.ActionResult.ILLEGAL_DISCARD;
            }
            // If drawn tile currently lives off-table and concealed count is 13, discard must consume that draw.
            if (drawnOffTable && held == 13 + kanBonus) {
                discarded = seatPlayer.takeTileOffTableForDiscard(sl, gameState.lastDrawnCode);
                if (discarded == null) {
                    return MahjongGameState.ActionResult.MISSING_DRAWN_TILE;
                }
                int discarderSeat = gameState.currentTurnSeat;
                logAtomicAction(
                        "DISCARD_ATTEMPT",
                        seatLogLabel(discarderSeat) + " tile=" + tileLabel(discardCode));
                return commitDiscardAfterRemovingStack(discarderSeat, discarded, discardCode);
            }
            discarded = seatPlayer.takeTileOffTableForDiscard(sl, gameState.lastDrawnCode);
            if (discarded == null) {
                discarded = removeOneStackFromHandInventoryByCode(gameState.currentTurnSeat, gameState.lastDrawnCode);
            }
            if (discarded == null) {
                return MahjongGameState.ActionResult.ILLEGAL_DISCARD;
            }
        } else {
            if (totalTilesHeldFromInventoryAndMelds(gameState.currentTurnSeat) != 14 + kanMeldCountForSeat(gameState.currentTurnSeat)) {
                return MahjongGameState.ActionResult.WRONG_PHASE;
            }
            discarded = removeLastStackFromHandInventory(gameState.currentTurnSeat);
            if (discarded == null) {
                return MahjongGameState.ActionResult.ILLEGAL_DISCARD;
            }
            Integer code = MahjongTileItems.codeForItem(discarded.getItem());
            discardCode = code == null ? -1 : code;
            if (!isRiichiDiscardLegal(gameState.currentTurnSeat, seatPlayer, sl, discardCode)) {
                return MahjongGameState.ActionResult.ILLEGAL_DISCARD;
            }
        }
        int discarderSeat = gameState.currentTurnSeat;
        logAtomicAction(
                "DISCARD_ATTEMPT",
                seatLogLabel(discarderSeat) + " tile=" + tileLabel(discardCode));
        return commitDiscardAfterRemovingStack(discarderSeat, discarded, discardCode);
    }

    private MahjongGameState.ActionResult commitDiscardAfterRemovingStack(
            int discarderSeat, ItemStack discarded, int discardCode) {
        if (gameState == null) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        closedKanSelectionSeat = -1;
        chiSelectionSeat = -1;
        appendDiscardStackToInventory(discarderSeat, discarded);
        if (discarderSeat >= 0
                && discarderSeat < gameState.riichiPending.length
                && gameState.riichiPending[discarderSeat]) {
            gameState.riichiPending[discarderSeat] = false;
            if (discarderSeat < gameState.riichiDeclared.length) {
                gameState.riichiDeclared[discarderSeat] = true;
            }
            if (discarderSeat < gameState.ippatsuEligible.length) {
                gameState.ippatsuEligible[discarderSeat] = true;
            }
            if (discarderSeat < SEAT_COUNT) {
                rules.addSeatPoints(discarderSeat, -1000);
                gameState.riichiPot += 1000;
            }
            logAtomicAction("RIICHI", seatLogLabel(discarderSeat) + " declared_after_discard");
        }
        // Any completed discard cycle ends ippatsu for all seats except the current discarder
        // (who may have just acquired it). Meld calls will separately clear the discarder's flag too.
        for (int s = 0; s < gameState.ippatsuEligible.length; s++) {
            if (s != discarderSeat) {
                gameState.ippatsuEligible[s] = false;
            }
        }
        if (discarderSeat >= 0 && discarderSeat < gameState.temporaryFuriten.length) {
            gameState.temporaryFuriten[discarderSeat] = false;
        }
        if (discardCode >= 0 && discardCode < 34
                && discarderSeat >= 0
                && discarderSeat < gameState.seenDiscardsBySeatAndTile.length) {
            gameState.seenDiscardsBySeatAndTile[discarderSeat][discardCode] = true;
        }
        gameState.lastDrawnCode = -1;
        gameState.lastDrawWasRinshan = false;
        List<Integer> playOrder = deterministicPlayOrder();
        if (playOrder.size() < 2 || !playOrder.contains(discarderSeat)) {
            logAtomicAction(
                    "DISCARD_FAIL",
                    seatLogLabel(discarderSeat) + " reason=invalid_play_order size=" + playOrder.size());
            abortMatch(MatchAbortReason.GENERIC);
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        int idx = playOrder.indexOf(discarderSeat);
        if (idx < 0) {
            idx = 0;
        }
        int n = playOrder.size();
        int nextDrawerSeat = playOrder.get((idx + 1) % n);
        if (!hasAnyLegalClaimOnDiscardWindow(discarderSeat, discardCode)) {
            gameState.claimDiscarderSeat = -1;
            gameState.claimTileCode = -1;
            gameState.claimNextDrawerSeat = -1;
            gameState.resetClaimIntentsForNewWindow();
            gameState.currentTurnSeat = nextDrawerSeat;
            gameState.phase = MahjongGameState.TurnPhase.MUST_DRAW;
            sortSeatHandTilesByTileCode(discarderSeat);
            logAtomicAction(
                    "DISCARD_OK",
                    seatLogLabel(discarderSeat)
                            + " tile="
                            + tileLabel(discardCode)
                            + " -> no_claims next_draw="
                            + seatLogLabel(nextDrawerSeat));
            return MahjongGameState.ActionResult.OK;
        }
        gameState.claimNextDrawerSeat = nextDrawerSeat;
        gameState.claimDiscarderSeat = discarderSeat;
        gameState.claimTileCode = discardCode;
        gameState.phase = MahjongGameState.TurnPhase.CLAIM_WINDOW;
        gameState.currentTurnSeat = gameState.claimNextDrawerSeat;
        gameState.resetClaimIntentsForNewWindow();
        sortSeatHandTilesByTileCode(discarderSeat);
        logAtomicAction(
                "DISCARD_OK",
                seatLogLabel(discarderSeat)
                        + " tile="
                        + tileLabel(discardCode)
                        + " -> claim_window next="
                        + seatLogLabel(gameState.claimNextDrawerSeat));
        return MahjongGameState.ActionResult.OK;
    }

    // TODO: Move this whole claim-probe logic into pure mahjongcore. To do that cleanly,
    // table wrappers are needed first (for example getLiveWallTiles/getDoraIndicators/
    // per-seat hand+meld+furiten snapshots) so core receives only plain tile/rule data.
    private boolean hasAnyLegalClaimOnDiscardWindow(int discarderSeat, int discardCode) {
        if (gameState == null || !deterministicPlayOrder().contains(discarderSeat) || discardCode < 0 || discardCode > 33) {
            return false;
        }
        int liveWallRemaining = getLiveWallRemainingFromInventory();
        List<Integer> doraIndicatorCodes = getDoraIndicatorCodesForScoring();
        final boolean claimWindowActiveForProbe = true;
        final boolean claimIsChankanWindowForProbe = false;
        for (int seat : ClaimWindowRules.claimPriorityOrder(deterministicPlayOrder(), discarderSeat)) {
            if (seat < 0 || seat >= SEAT_COUNT) {
                continue;
            }
            ArrayDeque<Integer> hand = readConcealedHandFromInventory(seat);
            ArrayList<Mentsu> melds = meldsAsMentsuList(seat);
            boolean seatInRiichi = gameState.riichiDeclared[seat];
            boolean legalRon = WinRules.canRon(
                    claimWindowActiveForProbe,
                    seat,
                    discarderSeat,
                    deterministicPlayOrder(),
                    gameState.riichiDeclared,
                    gameState.ippatsuEligible,
                    claimIsChankanWindowForProbe,
                    discardCode,
                    hand,
                    melds,
                    gameState.handNumber,
                    gameState.scoreAsNotFirstRound,
                    liveWallRemaining,
                    doraIndicatorCodes)
                    && !isSeatFuritenForRon(seat, discardCode, hand, melds);
            boolean legalDaiminKan = ClaimLegalityRules.canDaiminKan(hand, discardCode);
            boolean legalPon = ClaimLegalityRules.canPon(hand, discardCode);
            boolean legalChiFromKamicha = ClaimIntentRules.isKamicha(deterministicPlayOrder(), discarderSeat, seat)
                    && !ClaimLegalityRules.findChiPairs(hand, discardCode).isEmpty();
            if (ClaimIntentRules.hasAnyLegalClaim(
                    false,
                    legalRon,
                    seatInRiichi,
                    legalDaiminKan,
                    legalPon,
                    legalChiFromKamicha)) {
                return true;
            }
        }
        return false;
    }

    private MahjongGameState.ActionResult tryDiscardPhysicalFromHandSlot(
            int actorPhysicalSeat, int slotIndex0) {
        if (gameState == null) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        MahjongSeatPlayer seatPlayer =
                (actorPhysicalSeat >= 0 && actorPhysicalSeat < SEAT_COUNT) ? seatPlayers[actorPhysicalSeat] : null;
        if (seatPlayer == null) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        if (actorPhysicalSeat != gameState.currentTurnSeat) {
            return MahjongGameState.ActionResult.NOT_YOUR_TURN;
        }
        if (gameState.phase != MahjongGameState.TurnPhase.MUST_DISCARD) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        if (slotIndex0 < 0 || slotIndex0 >= PLAYER_ZONE_SLOTS_PER_SEAT) {
            return MahjongGameState.ActionResult.ILLEGAL_DISCARD;
        }
        int invSlot = playerZoneAbsolute(actorPhysicalSeat, slotIndex0);
        ItemStack st = inventory.get(invSlot);
        Integer codeObj = st.isEmpty() ? null : MahjongTileItems.codeForItem(st.getItem());
        int tileCode = codeObj == null ? -1 : codeObj;
        if (!(level instanceof ServerLevel sl)) {
            return MahjongGameState.ActionResult.WRONG_PHASE;
        }
        int heldBeforeDiscard = -1;
        boolean moveCurrentDrawnFromOffTable = false;
        if (gameState.lastDrawnCode >= 0) {
            heldBeforeDiscard = totalTilesHeldFromInventoryAndMelds(actorPhysicalSeat);
            boolean drawnOffTable = seatPlayer.hasTileOffTable(sl, gameState.lastDrawnCode);
            int kanBonus = kanMeldCountForSeat(actorPhysicalSeat);
            if (drawnOffTable && heldBeforeDiscard != 13 + kanBonus && heldBeforeDiscard != 14 + kanBonus) {
                return MahjongGameState.ActionResult.WRONG_PHASE;
            }
            if (!drawnOffTable && heldBeforeDiscard != 14 + kanBonus) {
                return MahjongGameState.ActionResult.WRONG_PHASE;
            }
            // If drawn tile currently lives off-table and concealed hand is 13 tiles, refill clicked slot from
            // off-table storage after removing discard so authoritative hand-zone stays at 13.
            moveCurrentDrawnFromOffTable = drawnOffTable && heldBeforeDiscard == 13 + kanBonus;
        } else {
            heldBeforeDiscard = totalTilesHeldFromInventoryAndMelds(actorPhysicalSeat);
            if (heldBeforeDiscard != 14 + kanMeldCountForSeat(actorPhysicalSeat)) {
                return MahjongGameState.ActionResult.WRONG_PHASE;
            }
        }
        if (tileCode < 0) {
            return MahjongGameState.ActionResult.ILLEGAL_DISCARD;
        }
        if (!isRiichiDiscardLegal(actorPhysicalSeat, seatPlayer, sl, tileCode)) {
            return MahjongGameState.ActionResult.ILLEGAL_DISCARD;
        }
        ItemStack drawnToHandSlot = ItemStack.EMPTY;
        if (moveCurrentDrawnFromOffTable) {
            ItemStack moved = seatPlayer.takeTileOffTableForDiscard(sl, gameState.lastDrawnCode);
            if (moved == null || moved.isEmpty()) {
                    return MahjongGameState.ActionResult.MISSING_DRAWN_TILE;
            }
            drawnToHandSlot = moved;
        }
        ItemStack discarded = st.split(1);
        if (!drawnToHandSlot.isEmpty()) {
            inventory.set(invSlot, drawnToHandSlot);
        } else if (!st.isEmpty()) {
            inventory.set(invSlot, st);
        } else {
            inventory.set(invSlot, ItemStack.EMPTY);
        }
        if (discarded.isEmpty()) {
            return MahjongGameState.ActionResult.ILLEGAL_DISCARD;
        }
        int discarderSeat = gameState.currentTurnSeat;
        return commitDiscardAfterRemovingStack(discarderSeat, discarded, tileCode);
    }

    /**
     * Seat-local hand slot interaction (server-only). Discards the tile in that slot when legal
     * ({@link MahjongGameState.TurnPhase#MUST_DISCARD}).
     */
    public void onHandSlotInteraction(ServerPlayer player, int seat, int slotIndex0) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (matchPhase != TableMatchPhase.IN_MATCH || gameState == null) {
            return;
        }
        if (isRoundSetupInProgress()) {
            return;
        }
        if (seat < 0 || seat >= SEAT_COUNT || !isSeatEnabled(seat)) {
            return;
        }
        if (slotIndex0 < 0 || slotIndex0 >= PLAYER_ZONE_SLOTS_PER_SEAT) {
            return;
        }
        if (!Objects.equals(occupantAt(seat), player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.use_own_side"), true);
            return;
        }
        if (seat != gameState.currentTurnSeat) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.not_turn"), true);
            return;
        }
        if (gameState.phase != MahjongGameState.TurnPhase.MUST_DISCARD) {
            return;
        }
        if (closedKanSelectionSeat == seat) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.ankan_pick_or_cancel"), true);
            return;
        }
        MahjongGameState.ActionResult r = tryDiscardPhysicalFromHandSlot(seat, slotIndex0);
        if (r != MahjongGameState.ActionResult.OK) {
            int expectedDrawCode = gameState != null ? gameState.lastDrawnCode : -1;
            player.displayClientMessage(discardFailMessageForResult(r, expectedDrawCode), true);
            return;
        }
        setChanged();
        syncToClients();
        afterSuccessfulDiscard(sl);
        tickActiveTurnSeat(sl);
    }

    /**
     * In-world hand slots: take (empty hand), place (empty slot), or swap. Allowed while waiting or when
     * {@link #allowInventoryEditWhileInMatch} is on; server-only.
     */
    public void onHandSlotFreeEdit(ServerPlayer player, InteractionHand interactionHand, int seat, int slotIndex0) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (seat < 0 || seat >= SEAT_COUNT || !isSeatEnabled(seat)) {
            return;
        }
        if (slotIndex0 < 0 || slotIndex0 >= PLAYER_ZONE_SLOTS_PER_SEAT) {
            return;
        }
        if (isInMatch() && !allowInventoryEditWhileInMatch) {
            return;
        }
        int invSlot = playerZoneAbsolute(seat, slotIndex0);
        onInventorySlotFreeEdit(player, interactionHand, invSlot, sl);
    }

    /**
     * In-world discard (river) slots: same take / place / swap rules as hands. Allowed while waiting or when
     * {@link #allowInventoryEditWhileInMatch} is on; server-only.
     */
    public void onDiscardSlotFreeEdit(ServerPlayer player, InteractionHand interactionHand, int seat, int slotIndex0) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (seat < 0 || seat >= SEAT_COUNT || !isSeatEnabled(seat)) {
            return;
        }
        if (slotIndex0 < 0 || slotIndex0 >= DISCARDS_SLOTS_PER_SEAT) {
            return;
        }
        if (isInMatch() && !allowInventoryEditWhileInMatch) {
            return;
        }
        int invSlot = discardAbsolute(seat, slotIndex0);
        onInventorySlotFreeEdit(player, interactionHand, invSlot, sl);
    }

    /**
     * In-world open-meld display slots: same take / place / swap rules as hands/discards.
     * Allowed while waiting or when {@link #allowInventoryEditWhileInMatch} is on; server-only.
     */
    public void onOpenMeldSlotFreeEdit(ServerPlayer player, InteractionHand interactionHand, int seat, int slotIndex0) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (seat < 0 || seat >= SEAT_COUNT || !isSeatEnabled(seat)) {
            return;
        }
        if (slotIndex0 < 0 || slotIndex0 >= OPEN_MELD_SLOTS_PER_SEAT) {
            return;
        }
        if (isInMatch() && !allowInventoryEditWhileInMatch) {
            return;
        }
        int invSlot = INV_OPEN_MELD_START + seat * OPEN_MELD_SLOTS_PER_SEAT + slotIndex0;
        onInventorySlotFreeEdit(player, interactionHand, invSlot, sl);
    }

    /**
     * In-world wall/dead-wall stack interaction: take/place/swap the current top tile of the stack.
     * Allowed while waiting or when {@link #allowInventoryEditWhileInMatch} is on; server-only.
     */
    public void onWallStackFreeEdit(
            ServerPlayer player, InteractionHand interactionHand, boolean deadWall, int stackIndex) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (isInMatch() && !allowInventoryEditWhileInMatch) {
            return;
        }
        int invSlot = resolveWallInteractionSlot(deadWall, stackIndex, player.getItemInHand(interactionHand).isEmpty());
        if (invSlot < 0 || invSlot >= INVENTORY_SIZE) {
            return;
        }
        onInventorySlotFreeEdit(player, interactionHand, invSlot, sl);
    }

    /**
     * Lobby-only center-surface convenience: put one held mahjong tile into table storage.
     *
     * @return true when the click should be consumed (placed successfully or storage full), false otherwise.
     */
    public boolean tryPlaceHeldTileIntoTableStorage(ServerPlayer player, InteractionHand interactionHand) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return false;
        }
        if (isInMatch()) {
            return false;
        }
        ItemStack held = player.getItemInHand(interactionHand);
        if (held.isEmpty() || MahjongTileItems.codeForItem(held.getItem()) == null) {
            return false;
        }
        int target = firstEmptySlotInRange(INV_TILES_IN_TABLE_START, TILES_IN_TABLE_SLOTS);
        if (target < 0) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.lobby.table_storage_full"),
                    true);
            return true;
        }
        ItemStack one = held.split(1);
        if (one.isEmpty()) {
            return true;
        }
        inventory.set(target, one);
        sl.playSound(
                null,
                worldPosition,
                SoundEvents.BARREL_OPEN,
                SoundSource.BLOCKS,
                0.55f,
                1.30f);
        playTilePlaceSound(sl);
        setChanged();
        syncToClients();
        return true;
    }

    public boolean writeStateIntoRecord(ServerPlayer player, ItemStack recordStack) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return false;
        }
        if (!isInMatch() || allowInventoryEditWhileInMatch || gameState == null) {
            return false;
        }
        if (!(recordStack.getItem() instanceof MahjongTableRecordItem recordItem)) {
            return false;
        }

        CompoundTag gameTag = new CompoundTag();
        MahjongGameStateNbt.save(gameState, gameTag);
        ListTag inventoryEntries = saveInventoryEntries();
        CompoundTag snapshot = recordItem.createRecordedTableState(rules, gameTag, inventoryEntries);
        MahjongTableRecordItem.writeRecordedTableState(recordStack, snapshot);

        clearNonTableSlots();
        endMatchToWaitingKeepTilesInternal(sl);

        setChanged();
        syncToClients();
        sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        return true;
    }

    public boolean readStateFromRecord(ServerPlayer player, ItemStack recordStack, CompoundTag tableState) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return false;
        }
        if (!tableState.contains(INVENTORY_ITEMS_INT_TAG, Tag.TAG_LIST)
                || !tableState.contains("MatchRules", Tag.TAG_COMPOUND)
                || !tableState.contains("MtGame", Tag.TAG_COMPOUND)
                || !MahjongMatchDefinitionNbt.hasSeatDefinitions(tableState)) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.table_record.invalid_state"),
                    true);
            return true;
        }

        dropNonTableSlots(sl);
        rules = MahjongMatchDefinitionNbt.load(tableState, MahjongMatchDefinition.DEFAULT);
        loadInventoryWithIntSlots(tableState, inventory);
        rebuildSeatPlayers();
        matchPhase = TableMatchPhase.IN_MATCH;
        matchRound = 0;
        gameState = MahjongGameStateNbt.load(tableState.getCompound("MtGame"));

        clearRoundSetupState();
        clearHandResultState();
        resetSeatPlayerDelays();

        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            beMelds[seat].clear();
        }
        MahjongTableRecordItem.clearRecordedTableState(recordStack);
        setChanged();
        syncToClients();
        sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private ListTag saveInventoryEntries() {
        CompoundTag inventoryTag = new CompoundTag();
        saveInventoryWithIntSlots(inventoryTag, inventory);
        return inventoryTag.getList(INVENTORY_ITEMS_INT_TAG, Tag.TAG_COMPOUND).copy();
    }

    private void clearNonTableSlots() {
        clearSection(INV_PLAYER_ZONE_START, PLAYER_ZONE_TOTAL_SLOTS);
        clearSection(INV_OPEN_MELD_START, OPEN_MELD_TOTAL_SLOTS);
        clearSection(INV_WALL_START, WALL_SLOTS);
        clearSection(INV_DEAD_WALL_START, DEAD_WALL_SLOTS);
        clearSection(INV_DISCARDS_START, DISCARDS_TOTAL_SLOTS);
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            beMelds[seat].clear();
        }
    }

    private void dropNonTableSlots(ServerLevel sl) {
        dropSlotsInRange(sl, INV_PLAYER_ZONE_START, PLAYER_ZONE_TOTAL_SLOTS);
        dropSlotsInRange(sl, INV_OPEN_MELD_START, OPEN_MELD_TOTAL_SLOTS);
        dropSlotsInRange(sl, INV_WALL_START, WALL_SLOTS);
        dropSlotsInRange(sl, INV_DEAD_WALL_START, DEAD_WALL_SLOTS);
        dropSlotsInRange(sl, INV_DISCARDS_START, DISCARDS_TOTAL_SLOTS);
    }

    private void dropSlotsInRange(ServerLevel sl, int start, int count) {
        int end = Math.min(inventory.size(), start + count);
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.0;
        double z = worldPosition.getZ() + 0.5;
        for (int slot = start; slot < end; slot++) {
            ItemStack st = inventory.get(slot);
            if (st.isEmpty()) {
                continue;
            }
            Containers.dropItemStack(sl, x, y, z, st.copy());
            inventory.set(slot, ItemStack.EMPTY);
        }
    }

    private int resolveWallInteractionSlot(boolean deadWall, int stackIndex, boolean takingFromStack) {
        int start = deadWall ? INV_DEAD_WALL_START : INV_WALL_START;
        int slotCount = deadWall ? DEAD_WALL_SLOTS : WALL_SLOTS;
        int stacks = slotCount / 2;
        if (stackIndex < 0 || stackIndex >= stacks) {
            return -1;
        }
        int top = start + stackIndex * 2;
        int bottom = top + 1;
        boolean topOccupied = !inventory.get(top).isEmpty();
        boolean bottomOccupied = bottom < start + slotCount && !inventory.get(bottom).isEmpty();
        if (takingFromStack) {
            if (topOccupied) {
                return top;
            }
            if (bottomOccupied) {
                return bottom;
            }
            return top;
        }
        // Place/swap targets:
        // - empty stack -> bottom (base layer first)
        // - one tile in bottom -> top (stack upward)
        // - one tile in top (legacy/malformed) -> bottom
        // - two tiles -> top
        if (!topOccupied && !bottomOccupied) {
            return bottom;
        }
        if (!topOccupied && bottomOccupied) {
            return top;
        }
        if (topOccupied && !bottomOccupied) {
            return bottom;
        }
        return top;
    }

    private void onInventorySlotFreeEdit(
            ServerPlayer player, InteractionHand interactionHand, int invSlot, ServerLevel sl) {
        ItemStack slotStack = getItem(invSlot);
        ItemStack handStack = player.getItemInHand(interactionHand);

        if (handStack.isEmpty() && slotStack.isEmpty()) {
            return;
        }
        if (!handStack.isEmpty() && isWrenchItem(handStack)) {
            return;
        }

        if (handStack.isEmpty()) {
            ItemStack taken = removeItemNoUpdate(invSlot);
            if (taken.isEmpty()) {
                return;
            }
            player.setItemInHand(interactionHand, taken);
            playTilePlaceSound(sl);
            return;
        }

        if (slotStack.isEmpty()) {
            if (!canPlaceItem(invSlot, handStack)) {
                player.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.game.hand_edit_cannot_place"), true);
                return;
            }
            ItemStack one = handStack.split(1);
            if (one.isEmpty()) {
                return;
            }
            setItem(invSlot, one);
            playTilePlaceSound(sl);
            return;
        }

        if (!canPlaceItem(invSlot, handStack)) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.hand_edit_cannot_swap"), true);
            return;
        }
        ItemStack wasSlot = removeItemNoUpdate(invSlot);
        ItemStack wasHand = player.getItemInHand(interactionHand).copy();
        player.setItemInHand(interactionHand, wasSlot);
        setItem(invSlot, wasHand);
        playTilePlaceSound(sl);
    }

    private boolean addDrawnStackToHandHeldSlot(int seat, ItemStack st) {
        int base = playerZoneBase(seat);
        int concealedCount = 0;
        for (int i = 0; i < PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
            ItemStack slotStack = inventory.get(base + i);
            if (slotStack.isEmpty()) {
                continue;
            }
            if (MahjongTileItems.codeForItem(slotStack.getItem()) != null) {
                concealedCount++;
            }
        }
        int preferredOffset = Math.min(concealedCount, PLAYER_ZONE_SLOTS_PER_SEAT - 1);
        int preferredSlot = base + preferredOffset;
        if (inventory.get(preferredSlot).isEmpty()) {
            inventory.set(preferredSlot, st);
            return true;
        }
        for (int i = preferredOffset + 1; i < PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
            int slot = base + i;
            if (inventory.get(slot).isEmpty()) {
                inventory.set(slot, st);
                return true;
            }
        }
        for (int i = preferredOffset - 1; i >= 0; i--) {
            int slot = base + i;
            if (inventory.get(slot).isEmpty()) {
                inventory.set(slot, st);
                return true;
            }
        }
        return addStackToHandInventory(seat, st);
    }

    private boolean tryHandleGameFaceUse(ServerPlayer player, ServerLevel sl, int seatIndex) {
        if (gameState == null) {
            return false;
        }
        if (isRoundSetupInProgress()) {
            return false;
        }
        if (!Objects.equals(occupantAt(seatIndex), player.getUUID())) {
            return false;
        }
        if (seatIndex != gameState.currentTurnSeat) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.not_turn"), true);
            return true;
        }
        if (closedKanSelectionSeat == seatIndex) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.ankan_pick_or_cancel"), true);
            return true;
        }
        MahjongSeatPlayer seatPlayer = seatPlayers[seatIndex];
        if (seatPlayer == null) {
            return false;
        }
        MahjongGameState.ActionResult r;
        if (gameState.phase == MahjongGameState.TurnPhase.MUST_DRAW) {
            r = tryPerformSeatDraw(sl, seatIndex, seatPlayer);
            if (r == MahjongGameState.ActionResult.EMPTY_WALL) {
                return true;
            }
            if (r != MahjongGameState.ActionResult.OK) {
                player.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.game.draw_fail"), true);
                return true;
            }
        } else {
            r = tryPerformSeatDiscard(sl, seatIndex, seatPlayer);
            if (r != MahjongGameState.ActionResult.OK) {
                int expectedDrawCode = gameState != null ? gameState.lastDrawnCode : -1;
                player.displayClientMessage(discardFailMessageForResult(r, expectedDrawCode), true);
                return true;
            }
            // Keep UI interactions responsive when a manual discard resolved outside serverTick loop.
            tickActiveTurnSeat(sl);
        }
        return true;
    }

    private void afterSuccessfulDiscard(ServerLevel sl) {
        if (gameState == null || !gameState.isClaimWindowActive()) {
            return;
        }
        logClaimWindowOpen(sl);
        boolean changed = autoPassIneligibleClaimants();
        if (changed) {
            setChanged();
            syncToClients();
        }
        tryResolveClaimWindowIfReady(sl);
    }

    /** Auto-passes any seat with no legal claim. Returns true if any intent changed. */
    private boolean autoPassIneligibleClaimants() {
        if (gameState == null || !gameState.isClaimWindowActive()) return false;
        boolean changed = false;
        for (int s : ClaimWindowRules.eligibleClaimants(deterministicPlayOrder(), gameState.claimDiscarderSeat)) {
            if (gameState.claimIntent[s] == ClaimWindowRules.ClaimIntent.NONE
                    && !seatHasLegalClaimOnDiscardWindow(s)) {
                gameState.claimIntent[s] = ClaimWindowRules.ClaimIntent.PASS;
                logAtomicAction(
                        "CLAIM_AUTO_PASS",
                        seatLogLabel(s) + " no legal claims on " + tileLabel(gameState.claimTileCode));
                changed = true;
            }
        }
        return changed;
    }

    private void logClaimWindowOpen(ServerLevel sl) {
        if (gameState == null) return;
        int t = gameState.claimTileCode;
        StringBuilder sb = new StringBuilder("tile=").append(tileLabel(t))
                .append(" discarder=").append(seatLogLabel(gameState.claimDiscarderSeat))
                .append(" claimants=[");
        List<Integer> claimants = ClaimWindowRules.eligibleClaimants(deterministicPlayOrder(), gameState.claimDiscarderSeat);
        for (int i = 0; i < claimants.size(); i++) {
            int seat = claimants.get(i);
            sb.append(seatLogLabel(seat)).append('(').append(seatActorDebugLabel(sl, seat)).append(')');
            sb.append(':').append(describeLegalClaims(seat));
            if (i < claimants.size() - 1) sb.append(", ");
        }
        sb.append(']');
        logAtomicAction("CLAIM_WINDOW_OPEN", sb.toString());
    }

    private String describeLegalClaims(int seat) {
        if (gameState == null) return "none";
        int t = gameState.claimTileCode;
        ArrayDeque<Integer> hand = readConcealedHandFromInventory(seat);
        ArrayList<Mentsu> melds = meldsAsMentsuList(seat);
        StringBuilder sb = new StringBuilder();
        if (WinRules.canRon(
                gameState.isClaimWindowActive(),
                seat,
                gameState.claimDiscarderSeat,
                deterministicPlayOrder(),
                gameState.riichiDeclared,
                gameState.ippatsuEligible,
                gameState.claimIsChankanWindow,
                t,
                hand,
                melds,
                gameState.handNumber,
                gameState.scoreAsNotFirstRound,
                getLiveWallRemainingFromInventory(),
                getDoraIndicatorCodesForScoring())
                && !isSeatFuritenForRon(seat, t, hand, melds)) {
            sb.append("RON");
        }
        boolean inRiichi = seat >= 0 && seat < gameState.riichiDeclared.length && gameState.riichiDeclared[seat];
        if (!inRiichi) {
            if (ClaimLegalityRules.canDaiminKan(hand, t)) {
                if (sb.length() > 0) sb.append(',');
                sb.append("DAIMIN_KAN");
            }
            if (ClaimLegalityRules.canPon(hand, t)) {
                if (sb.length() > 0) sb.append(',');
                sb.append("PON");
            }
            if (ClaimIntentRules.isKamicha(deterministicPlayOrder(), gameState.claimDiscarderSeat, seat)
                    && !ClaimLegalityRules.findChiPairs(hand, t).isEmpty()) {
                if (sb.length() > 0) sb.append(',');
                sb.append("CHI");
            }
        }
        return sb.length() > 0 ? sb.toString() : "none";
    }

    private static Component discardFailMessageForResult(MahjongGameState.ActionResult r, int expectedDrawCode) {
        return switch (r) {
            case NOT_YOUR_TURN -> Component.translatable("riichi_mahjong_forge.chat.game.discard_not_your_turn");
            case WRONG_PHASE -> Component.translatable("riichi_mahjong_forge.chat.game.discard_wrong_phase");
            case ILLEGAL_DISCARD -> Component.translatable("riichi_mahjong_forge.chat.game.discard_illegal_choice");
            case MISSING_DRAWN_TILE -> Component.translatable(
                    "riichi_mahjong_forge.chat.game.discard_missing_drawn_tile",
                    tileDisplayNameComponent(expectedDrawCode));
            case EMPTY_WALL -> Component.translatable("riichi_mahjong_forge.chat.game.discard_wall_empty");
            default -> Component.translatable("riichi_mahjong_forge.chat.game.discard_failed_unknown");
        };
    }

    private ArrayList<Mentsu> meldsAsMentsuList(int seat) {
        ArrayList<Mentsu> m = new ArrayList<>();
        for (MahjongMeld meld : beMelds[seat]) {
            m.add(meld.toSingleMentsu());
        }
        return m;
    }

    /** Visible melds converted to mahjong4j mentsu for the seat (synced to clients). */
    public ArrayList<Mentsu> visibleMeldsAsMentsuList(int seat) {
        return meldsAsMentsuList(seat);
    }

    private boolean isSeatFuritenForRon(int seat, int claimTileCode, ArrayDeque<Integer> concealed, List<Mentsu> melds) {
        if (gameState == null || seat < 0 || seat >= SEAT_COUNT) {
            return false;
        }
        if (claimTileCode < 0 || claimTileCode > 33) {
            return false;
        }
        int[] concealedCounts = ClaimLegalityRules.concealedCounts(concealed);
        boolean[] waits = new boolean[34];
        for (int code : WinRules.winningShapeWaitCodes(concealedCounts, melds)) {
            if (code >= 0 && code < waits.length) {
                waits[code] = true;
            }
        }
        boolean temporary = seat < gameState.temporaryFuriten.length && gameState.temporaryFuriten[seat];
        boolean riichiPermanent =
                seat < gameState.riichiPermanentFuriten.length && gameState.riichiPermanentFuriten[seat];
        boolean[] seen = seat < gameState.seenDiscardsBySeatAndTile.length
                ? gameState.seenDiscardsBySeatAndTile[seat]
                : null;
        return WinRules.evaluateFuritenForRon(temporary, riichiPermanent, seen, waits).furitenForRon();
    }

    private void markMissedWinningShapeFuriten(
            List<Integer> claimOrder, ClaimWindowRules.ClaimIntent winningIntent, boolean[] winningShapeBySeat) {
        if (gameState == null) {
            return;
        }
        for (int seat : claimOrder) {
            if (seat < 0 || seat >= SEAT_COUNT) {
                continue;
            }
            if (!winningShapeBySeat[seat]) {
                continue;
            }
            if (seat < gameState.riichiPermanentFuriten.length && gameState.riichiPermanentFuriten[seat]) {
                continue;
            }
            if (gameState.claimIntent[seat] == winningIntent) {
                continue;
            }
            boolean riichi = seat < gameState.riichiDeclared.length && gameState.riichiDeclared[seat];
            if (riichi) {
                gameState.riichiPermanentFuriten[seat] = true;
            } else if (seat < gameState.temporaryFuriten.length) {
                gameState.temporaryFuriten[seat] = true;
            }
        }
    }

    private boolean seatHasLegalClaimOnDiscardWindow(int seat) {
        if (gameState == null) {
            return false;
        }
        int t = gameState.claimTileCode;
        ArrayDeque<Integer> hand = readConcealedHandFromInventory(seat);
        ArrayList<Mentsu> melds = meldsAsMentsuList(seat);
        boolean legalRon = WinRules.canRon(
                gameState.isClaimWindowActive(),
                seat,
                gameState.claimDiscarderSeat,
                deterministicPlayOrder(),
                gameState.riichiDeclared,
                gameState.ippatsuEligible,
                gameState.claimIsChankanWindow,
                t,
                hand,
                melds,
                gameState.handNumber,
                gameState.scoreAsNotFirstRound,
                getLiveWallRemainingFromInventory(),
                getDoraIndicatorCodesForScoring())
                && !isSeatFuritenForRon(seat, t, hand, melds);
        boolean inRiichi = seat >= 0 && seat < gameState.riichiDeclared.length && gameState.riichiDeclared[seat];
        boolean legalDaiminKan = ClaimLegalityRules.canDaiminKan(hand, t);
        boolean legalPon = ClaimLegalityRules.canPon(hand, t);
        boolean legalChiFromKamicha = ClaimIntentRules.isKamicha(deterministicPlayOrder(), gameState.claimDiscarderSeat, seat)
                && !ClaimLegalityRules.findChiPairs(hand, t).isEmpty();
        return ClaimIntentRules.hasAnyLegalClaim(
                gameState.claimIsChankanWindow,
                legalRon,
                inRiichi,
                legalDaiminKan,
                legalPon,
                legalChiFromKamicha);
    }

    public void handleGameClaim(
            ServerPlayer player, ClaimWindowRules.ClaimIntent intent, int chiTileA, int chiTileB) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (gameState == null || !gameState.isClaimWindowActive()) {
            return;
        }
        Integer seat = findSeatIndex(player.getUUID());
        if (seat == null) {
            return;
        }
        if (!ClaimIntentRules.isSeatEligibleForClaim(deterministicPlayOrder(), gameState.claimDiscarderSeat, seat)) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.claim_not_eligible"), true);
            return;
        }
        int t = gameState.claimTileCode;
        ArrayDeque<Integer> hand = readConcealedHandFromInventory(seat);
        ArrayList<Mentsu> melds = meldsAsMentsuList(seat);
        List<ClaimLegalityRules.ChiPair> chiPairsCore = ClaimLegalityRules.findChiPairs(hand, t);
        boolean legalRon = WinRules.canRon(
                gameState.isClaimWindowActive(),
                seat,
                gameState.claimDiscarderSeat,
                deterministicPlayOrder(),
                gameState.riichiDeclared,
                gameState.ippatsuEligible,
                gameState.claimIsChankanWindow,
                t,
                hand,
                melds,
                gameState.handNumber,
                gameState.scoreAsNotFirstRound,
                getLiveWallRemainingFromInventory(),
                getDoraIndicatorCodesForScoring())
                && !isSeatFuritenForRon(seat, t, hand, melds);
        boolean inRiichi = seat >= 0 && seat < gameState.riichiDeclared.length && gameState.riichiDeclared[seat];
        ClaimIntentRules.ClaimIntentValidation validation = ClaimIntentRules.validate(
                gameState.isClaimWindowActive(),
                gameState.claimIsChankanWindow,
                ClaimIntentRules.isSeatEligibleForClaim(deterministicPlayOrder(), gameState.claimDiscarderSeat, seat),
                inRiichi,
                intent,
                legalRon,
                ClaimLegalityRules.canPon(hand, t),
                ClaimLegalityRules.canDaiminKan(hand, t),
                !chiPairsCore.isEmpty(),
                ClaimIntentRules.isKamicha(deterministicPlayOrder(), gameState.claimDiscarderSeat, seat),
                chiTileA,
                chiTileB,
                chiPairsCore);
        if (!validation.accepted()) {
            player.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.claim_rejected"), true);
            return;
        }
        chiTileA = validation.resolvedChiTileA();
        chiTileB = validation.resolvedChiTileB();
        gameState.claimIntent[seat] = intent;
        gameState.claimChiTileA[seat] = chiTileA;
        gameState.claimChiTileB[seat] = chiTileB;
        logAtomicAction(
                "HUMAN_CLAIM",
                seatLogLabel(seat)
                        + " intent="
                        + intent
                        + " on "
                        + tileLabel(t)
                        + (intent == ClaimWindowRules.ClaimIntent.CHI
                                ? (" with " + tileLabel(chiTileA) + "+" + tileLabel(chiTileB))
                                : ""));
        setChanged();
        syncToClients();
        boolean changed = autoPassIneligibleClaimants();
        if (changed) {
            setChanged();
            syncToClients();
        }
        tryResolveClaimWindowIfReady(sl);
        // Ensure immediate progression to next draw/turn after manual claim input,
        // even when resolution happened inside this handler rather than tick loop.
        tickActiveTurnSeat(sl);
    }

    private void tryResolveClaimWindowIfReady(ServerLevel sl) {
        if (gameState == null || !gameState.isClaimWindowActive()) {
            return;
        }
        if (!ClaimWindowRules.isReadyToResolve(
                gameState.isClaimWindowActive(),
                deterministicPlayOrder(),
                gameState.claimDiscarderSeat,
                Arrays.asList(gameState.claimIntent))) {
            return;
        }
        chiSelectionSeat = -1;
        int discarder = gameState.claimDiscarderSeat;
        int tile = gameState.claimTileCode;
        List<Integer> order = ClaimWindowRules.claimPriorityOrder(deterministicPlayOrder(), discarder);
        int liveWallRemaining = getLiveWallRemainingFromInventory();
        List<Integer> doraIndicatorCodes = getDoraIndicatorCodesForScoring();
        boolean isChankanWindow = gameState.claimIsChankanWindow;
        boolean[] legalRonBySeat = new boolean[SEAT_COUNT];
        boolean[] legalDaiminKanBySeat = new boolean[SEAT_COUNT];
        boolean[] legalPonBySeat = new boolean[SEAT_COUNT];
        boolean[] legalChiBySeat = new boolean[SEAT_COUNT];
        boolean[] winningShapeBySeat = new boolean[SEAT_COUNT];
        ArrayList<ClaimWindowRules.SeatClaimView> seatViewsBySeat = new ArrayList<>(SEAT_COUNT);
        for (int s = 0; s < SEAT_COUNT; s++) {
            seatViewsBySeat.add(new ClaimWindowRules.SeatClaimView(
                    ClaimWindowRules.ClaimIntent.NONE, false, false, false, false));
        }
        for (int seat : order) {
            ArrayDeque<Integer> hand = readConcealedHandFromInventory(seat);
            ArrayList<Mentsu> melds = meldsAsMentsuList(seat);
            winningShapeBySeat[seat] = WinRules.canCompleteWinningShape(
                    ClaimLegalityRules.concealedCounts(hand), melds, tile);
            legalRonBySeat[seat] = WinRules.canRon(
                    gameState.isClaimWindowActive(),
                    seat,
                    gameState.claimDiscarderSeat,
                    deterministicPlayOrder(),
                    gameState.riichiDeclared,
                    gameState.ippatsuEligible,
                    gameState.claimIsChankanWindow,
                    tile,
                    hand,
                    melds,
                    gameState.handNumber,
                    gameState.scoreAsNotFirstRound,
                    liveWallRemaining,
                    doraIndicatorCodes)
                    && !isSeatFuritenForRon(seat, tile, hand, melds);
            if (isChankanWindow) {
                seatViewsBySeat.set(
                        seat,
                        new ClaimWindowRules.SeatClaimView(
                                gameState.claimIntent[seat],
                                legalRonBySeat[seat],
                                false,
                                false,
                                false));
                continue;
            }
            legalDaiminKanBySeat[seat] = ClaimLegalityRules.canDaiminKan(hand, tile);
            legalPonBySeat[seat] = ClaimLegalityRules.canPon(hand, tile);
            if (ClaimIntentRules.isKamicha(deterministicPlayOrder(), discarder, seat)) {
                int a = gameState.claimChiTileA[seat];
                int b = gameState.claimChiTileB[seat];
                for (ClaimLegalityRules.ChiPair p : ClaimLegalityRules.findChiPairs(hand, tile)) {
                    if ((p.tileA() == a && p.tileB() == b) || (p.tileA() == b && p.tileB() == a)) {
                        legalChiBySeat[seat] = true;
                        break;
                    }
                }
            }
            seatViewsBySeat.set(
                    seat,
                    new ClaimWindowRules.SeatClaimView(
                            gameState.claimIntent[seat],
                            legalRonBySeat[seat],
                            legalDaiminKanBySeat[seat],
                            legalPonBySeat[seat],
                            legalChiBySeat[seat]));
        }
        ClaimWindowRules.ClaimResolution resolution =
                ClaimWindowRules.resolve(
                        new ClaimWindowRules.ClaimWindowSnapshot(order, isChankanWindow, seatViewsBySeat));
        ClaimWindowRules.ClaimIntent winningIntent =
                isChankanWindow ? ClaimWindowRules.ClaimIntent.CHANKAN : ClaimWindowRules.ClaimIntent.RON;
        markMissedWinningShapeFuriten(order, winningIntent, winningShapeBySeat);
        ClaimWindowRules.OutcomeKind kind = resolution.kind();
        int winner = resolution.winnerSeat();

        switch (kind) {
            case PASS_ALL -> {
                logAtomicAction("CLAIM_RESOLVE", isChankanWindow ? "CHANKAN_PASS_ALL" : "PASS_ALL");
                int kakanSeat = gameState.claimDiscarderSeat;
                gameState.finishClaimWindowWithAllPass();
                if (isChankanWindow) {
                    gameState.currentTurnSeat = kakanSeat;
                    ServerPlayer kakanPlayer = (kakanSeat >= 0 && kakanSeat < SEAT_COUNT && occupantAt(kakanSeat) != null)
                            ? sl.getServer().getPlayerList().getPlayer(occupantAt(kakanSeat)) : null;
                    MahjongSeatPlayer kakanSeatPlayer =
                            (kakanSeat >= 0 && kakanSeat < SEAT_COUNT) ? seatPlayers[kakanSeat] : null;
                    if (!applyRinshanDrawAfterKan(sl, kakanSeatPlayer, kakanSeat)) {
                        logAtomicAction("CHANKAN_PASS_ALL", "rinshan draw failed for seat " + seatLogLabel(kakanSeat));
                    }
                }
            }
            case CHANKAN -> {
                logAtomicAction("CLAIM_RESOLVE", "CHANKAN winner=" + seatLogLabel(winner) + " tile=" + tileLabel(tile));
                onRonResolved(sl, winner);
                return;
            }
            case RON -> {
                logAtomicAction("CLAIM_RESOLVE", "RON winner=" + seatLogLabel(winner) + " tile=" + tileLabel(tile));
                onRonResolved(sl, winner);
                return;
            }
            case PON -> {
                removeLastDiscardIfMatches(discarder, tile);
                removeTilesFromSeatHand(winner, tile, 2);
                addSeatMeld(winner, new MahjongMeld(MahjongMeld.Kind.PON, new int[] {tile, tile, tile}, discarder));
                lastDiscardSeat = -1;
                lastDiscardSlotIndex = -1;
                lastMeldSeat = winner;
                lastMeldClaimedSlotIndex = computeLastMeldClaimedSlotIndex(winner);
                gameState.finishClaimWithMeld(winner);
                Arrays.fill(gameState.ippatsuEligible, false);
                playTilePlaceSound(sl);
                logAtomicAction(
                        "CLAIM_RESOLVE",
                        "PON winner=" + seatLogLabel(winner) + " from=" + seatLogLabel(discarder) + " tile=" + tileLabel(tile));
            }
            case DAIMIN_KAN -> {
                removeLastDiscardIfMatches(discarder, tile);
                removeTilesFromSeatHand(winner, tile, 3);
                addSeatMeld(
                        winner,
                        new MahjongMeld(MahjongMeld.Kind.DAIMIN_KAN, new int[] {tile, tile, tile, tile}, discarder));
                lastDiscardSeat = -1;
                lastDiscardSlotIndex = -1;
                lastMeldSeat = winner;
                lastMeldClaimedSlotIndex = computeLastMeldClaimedSlotIndex(winner);
                gameState.finishClaimWithMeld(winner);
                Arrays.fill(gameState.ippatsuEligible, false);
                playTilePlaceSound(sl);
                logAtomicAction(
                        "CLAIM_RESOLVE",
                        "DAIMIN_KAN winner=" + seatLogLabel(winner) + " from=" + seatLogLabel(discarder) + " tile=" + tileLabel(tile));
            }
            case CHI -> {
                removeLastDiscardIfMatches(discarder, tile);
                int a = gameState.claimChiTileA[winner];
                int b = gameState.claimChiTileB[winner];
                int[] tri = new int[] {tile, a, b};
                Arrays.sort(tri);
                int chiClaimedIdx = 0;
                for (int ci = 0; ci < tri.length; ci++) { if (tri[ci] == tile) { chiClaimedIdx = ci; break; } }
                removeTilesFromSeatHand(winner, a, 1);
                removeTilesFromSeatHand(winner, b, 1);
                addSeatMeld(winner, new MahjongMeld(MahjongMeld.Kind.CHI, tri, discarder));
                lastDiscardSeat = -1;
                lastDiscardSlotIndex = -1;
                lastMeldSeat = winner;
                lastMeldClaimedSlotIndex = computeLastMeldClaimedSlotIndex(winner) - (tri.length - 1 - chiClaimedIdx);
                gameState.finishClaimWithMeld(winner);
                Arrays.fill(gameState.ippatsuEligible, false);
                playTilePlaceSound(sl);
                logAtomicAction(
                        "CLAIM_RESOLVE",
                        "CHI winner="
                                + seatLogLabel(winner)
                                + " from="
                                + seatLogLabel(discarder)
                                + " tile="
                                + tileLabel(tile)
                                + " with "
                                + tileLabel(a)
                                + "+"
                                + tileLabel(b));
            }
        }
        sortAllSeatHandsByTileCode();
        setChanged();
        syncToClients();
        tickActiveTurnSeat(sl);
    }

    private void onRonResolved(ServerLevel sl, int winnerSeat) {
        if (gameState == null) {
            return;
        }
        boolean ronRiichi = winnerSeat >= 0
                && winnerSeat < gameState.riichiDeclared.length
                && gameState.riichiDeclared[winnerSeat];
        boolean ronIppatsu = winnerSeat >= 0
                && winnerSeat < gameState.ippatsuEligible.length
                && gameState.ippatsuEligible[winnerSeat];
        MahjongPersonalSituation ronSituation = new MahjongPersonalSituation(
                false,
                ronIppatsu,
                ronRiichi,
                false,
                gameState.claimIsChankanWindow,
                false,
                TurnOrderRules.jikazeForSeat(deterministicPlayOrder(), winnerSeat));
        WinRules.RonEvaluation ronEvaluation = WinRules.evaluateRonWinningHand(
                winnerSeat,
                gameState.claimTileCode,
                readConcealedHandFromInventory(winnerSeat),
                meldsAsMentsuList(winnerSeat),
                gameState.handNumber,
                gameState.scoreAsNotFirstRound,
                deterministicPlayOrder(),
                getLiveWallRemainingFromInventory(),
                getDoraIndicatorCodesForScoring(),
                ronSituation);
        UUID winnerId = occupantAt(winnerSeat);
        String winnerName = resolveSeatDisplayName(sl, winnerSeat);
        markRoundResolvedForAltar(sl, ronEvaluation.han());
        logAtomicAction(
                "HAND_END",
                "RON winner="
                        + seatLogLabel(winnerSeat)
                        + " tile="
                        + tileLabel(gameState.claimTileCode)
                        + " han="
                        + ronEvaluation.han()
                        + (ronEvaluation.yakuman() ? " yakuman" : ""));
        Tile winTile = Tile.valueOf(gameState.claimTileCode);
        Component msg =
                Component.translatable(
                        "riichi_mahjong_forge.chat.game.ron_win",
                        seatLabel(winnerSeat),
                        winTile.name());
        displayMessageToOccupied(sl, msg);
        int discarderSeat = gameState.claimDiscarderSeat;
        String discarderName = resolveSeatDisplayName(sl, discarderSeat);
        ArrayList<String> yakuLines = buildYakuLines(ronEvaluation);
        int honbaBonus = gameState.honba * 300;
        int ronPoints = ronEvaluation.score().getRon() + honbaBonus;
        String roundTitle = localizedRoundTitleWithHonba(gameState.handNumber + 1, gameState.honba);
        ArrayList<String> deltaLines = new ArrayList<>();
        deltaLines.add(winnerName + " +" + ronPoints);
        deltaLines.add(discarderName + " -" + ronPoints);
        if (honbaBonus > 0) {
            deltaLines.add(Component.translatable(
                    "riichi_mahjong_forge.result.delta_honba_bonus", honbaBonus).getString());
        }
        if (winnerSeat >= 0 && winnerSeat < SEAT_COUNT) {
            rules.addSeatPoints(winnerSeat, ronPoints);
        }
        if (discarderSeat >= 0 && discarderSeat < SEAT_COUNT) {
            rules.addSeatPoints(discarderSeat, -ronPoints);
        }
        applyRiichiPotToWinnerIfAny(winnerSeat, deltaLines);
        markUraDoraRevealedIfWinnerRiichi(winnerSeat);
        beginPendingWinResultAnimation(
                sl,
                winnerSeat,
                winnerId,
                winnerName,
                ronEvaluation.han(),
                ronEvaluation.yakuman(),
                uniqueYakuNames(ronEvaluation),
                enumNames(ronEvaluation.yakumanNames()),
                Component.translatable("riichi_mahjong_forge.result.header.ron", roundTitle).getString(),
                0xFFD85A,
                yakuLines,
                deltaLines,
                verdictTitleForWin(ronEvaluation.han(), ronEvaluation.yakuman()));
        announceOverlayToPlayersInRadius(
                sl,
                Component.translatable("riichi_mahjong_forge.chat.game.overlay.ron")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        advanceHandAfterWin(winnerSeat);
    }

    private void onTsumoResolved(ServerLevel sl, int winnerSeat) {
        if (gameState == null || winnerSeat < 0 || winnerSeat >= SEAT_COUNT) {
            return;
        }
        int winTileCode = gameState.lastDrawnCode;
        if (winTileCode < 0 || winTileCode > 33) {
            return;
        }
        MahjongSeatPlayer winnerSeatPlayer =
                (winnerSeat >= 0 && winnerSeat < SEAT_COUNT) ? seatPlayers[winnerSeat] : null;
        ServerPlayer winnerPlayer = winnerSeatPlayer == null ? null : winnerSeatPlayer.onlinePlayer(sl);
        ArrayDeque<Integer> concealedForScore = concealedForTsumoCheck(winnerSeat, winnerSeatPlayer);
        boolean tsumoRiichi = winnerSeat >= 0
                && winnerSeat < gameState.riichiDeclared.length
                && gameState.riichiDeclared[winnerSeat];
        boolean tsumoIppatsu = winnerSeat >= 0
                && winnerSeat < gameState.ippatsuEligible.length
                && gameState.ippatsuEligible[winnerSeat];
        MahjongPersonalSituation tsumoSituation = new MahjongPersonalSituation(
                true,
                tsumoIppatsu,
                tsumoRiichi,
                false,
                false,
                gameState.lastDrawWasRinshan,
                TurnOrderRules.jikazeForSeat(deterministicPlayOrder(), winnerSeat));
        WinRules.RonEvaluation eval = WinRules.evaluateRonWinningHand(
                winnerSeat,
                winTileCode,
                concealedForScore,
                meldsAsMentsuList(winnerSeat),
                gameState.handNumber,
                gameState.scoreAsNotFirstRound,
                deterministicPlayOrder(),
                getLiveWallRemainingFromInventory(),
                getDoraIndicatorCodesForScoring(),
                tsumoSituation);
        UUID winnerId = occupantAt(winnerSeat);
        String winnerName = resolveSeatDisplayName(sl, winnerSeat);
        markRoundResolvedForAltar(sl, eval.han());
        logAtomicAction(
                "HAND_END",
                "TSUMO winner="
                        + seatLogLabel(winnerSeat)
                        + " tile="
                        + tileLabel(winTileCode)
                        + " han="
                        + eval.han()
                        + (eval.yakuman() ? " yakuman" : ""));
        ArrayList<String> yakuLines = buildYakuLines(eval);
        WinRules.TsumoSplit split = WinRules.resolveCompactTsumoSplit(
                eval.winnerIsParent(),
                eval.score().getParentTsumo(),
                eval.score().getParent(),
                eval.score().getChild());
        int honbaPerPayer = gameState.honba * 100;
        int dealerSeat = deterministicPlayOrder().isEmpty() ? -1 : deterministicPlayOrder().get(0);
        ArrayList<String> finalDelta = new ArrayList<>();
        int winnerGain = split.winnerGain(deterministicPlayOrder().size());
        if (winnerSeat >= 0 && winnerSeat < SEAT_COUNT) {
            rules.addSeatPoints(winnerSeat, winnerGain);
        }
        finalDelta.add(Component.translatable(
                        "riichi_mahjong_forge.chat.game.result_delta_gain",
                        winnerName,
                        Integer.toString(winnerGain))
                .getString());
        if (split.winnerIsParent()) {
            finalDelta.add(Component.translatable(
                            "riichi_mahjong_forge.chat.game.result_split_all_each",
                            Integer.toString(split.childPay()))
                    .getString());
        } else {
            int childPayers = Math.max(0, deterministicPlayOrder().size() - 2);
            String dealerName = resolveSeatDisplayName(sl, dealerSeat);
            if (childPayers <= 0) {
                finalDelta.add(Component.translatable(
                                "riichi_mahjong_forge.chat.game.result_split_single",
                                dealerName,
                                Integer.toString(split.dealerPay()))
                        .getString());
            } else {
                finalDelta.add(Component.translatable(
                                "riichi_mahjong_forge.chat.game.result_split_dealer_others",
                                dealerName,
                                Integer.toString(split.dealerPay()),
                                Integer.toString(childPayers),
                                Integer.toString(split.childPay()))
                        .getString());
            }
        }
        for (int seat : deterministicPlayOrder()) {
            if (seat == winnerSeat || seat < 0 || seat >= SEAT_COUNT) {
                continue;
            }
            int pay = split.winnerIsParent()
                    ? split.childPay()
                    : (seat == dealerSeat ? split.dealerPay() : split.childPay());
            rules.addSeatPoints(seat, -pay);
        }
        int numTsumoPayers = Math.max(0, deterministicPlayOrder().size() - 1);
        if (honbaPerPayer > 0 && winnerSeat >= 0 && winnerSeat < SEAT_COUNT) {
            int totalHonbaGain = honbaPerPayer * numTsumoPayers;
            rules.addSeatPoints(winnerSeat, totalHonbaGain);
            for (int seat : deterministicPlayOrder()) {
                if (seat == winnerSeat || seat < 0 || seat >= SEAT_COUNT) continue;
                rules.addSeatPoints(seat, -honbaPerPayer);
            }
            finalDelta.add(Component.translatable(
                    "riichi_mahjong_forge.result.delta_honba_bonus", totalHonbaGain).getString());
        }
        applyRiichiPotToWinnerIfAny(winnerSeat, finalDelta);
        markUraDoraRevealedIfWinnerRiichi(winnerSeat);
        String roundTitle = localizedRoundTitleWithHonba(gameState.handNumber + 1, gameState.honba);
        beginPendingWinResultAnimation(
                sl,
                winnerSeat,
                winnerId,
                winnerName,
                eval.han(),
                eval.yakuman(),
                uniqueYakuNames(eval),
                enumNames(eval.yakumanNames()),
                Component.translatable("riichi_mahjong_forge.result.header.tsumo", roundTitle).getString(),
                0xFF42D8A4,
                yakuLines,
                finalDelta,
                verdictTitleForWin(eval.han(), eval.yakuman()));
        announceOverlayToPlayersInRadius(
                sl,
                Component.translatable("riichi_mahjong_forge.chat.game.overlay.tsumo")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        displayMessageToOccupied(
                sl,
                Component.translatable(
                        "riichi_mahjong_forge.chat.game.tsumo_win",
                        seatLabel(winnerSeat),
                        Tile.valueOf(winTileCode).name()));
        advanceHandAfterWin(winnerSeat);
    }

    private String resolveSeatDisplayName(ServerLevel sl, int seat) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            return seatLogLabel(seat);
        }
        UUID id = occupantAt(seat);
        if (id != null) {
            ServerPlayer player = sl.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                return player.getName().getString();
            }
        }
        MahjongSeatPlayer seatPlayer = seatPlayers[seat];
        if (seatPlayer != null) {
            return seatPlayer.actorDebugLabel(sl, seat);
        }
        return seatLogLabel(seat);
    }

    private void displayMessageToOccupied(ServerLevel sl, Component msg) {
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            UUID id = occupantAt(seat);
            if (id == null) {
                continue;
            }
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.displayClientMessage(msg, true);
            }
        }
    }

    private static List<String> enumNames(List<? extends Enum<?>> values) {
        ArrayList<String> out = new ArrayList<>();
        if (values == null) {
            return out;
        }
        for (Enum<?> value : values) {
            if (value != null) {
                out.add(value.name());
            }
        }
        return out;
    }

    private static List<String> uniqueYakuNames(WinRules.RonEvaluation eval) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (eval == null) {
            return List.of();
        }
        for (Enum<?> yakuman : eval.yakumanNames()) {
            if (yakuman != null) {
                out.add(yakuman.name());
            }
        }
        for (Enum<?> yaku : eval.yakuLines()) {
            if (yaku != null) {
                out.add(yaku.name());
            }
        }
        return List.copyOf(out);
    }

    private ArrayList<String> buildYakuLines(WinRules.RonEvaluation eval) {
        ArrayList<String> yakuLines = new ArrayList<>();
        java.util.LinkedHashMap<String, Integer> hanByYakuName = new java.util.LinkedHashMap<>();
        for (var yakuman : eval.yakumanNames()) {
            String nm = MahjongWinEffects.humanizeYakuName(yakuman.name());
            hanByYakuName.merge(nm, 13, Integer::sum);
        }
        for (var yaku : eval.yakuLines()) {
            String nm = MahjongWinEffects.humanizeYakuName(yaku.name());
            hanByYakuName.merge(nm, 1, Integer::sum);
        }
        for (var entry : hanByYakuName.entrySet()) {
            yakuLines.add(entry.getKey() + " - " + entry.getValue());
        }
        return yakuLines;
    }

    private void applyRiichiPotToWinnerIfAny(int winnerSeat, ArrayList<String> deltaLines) {
        if (gameState == null || winnerSeat < 0 || winnerSeat >= SEAT_COUNT) {
            return;
        }
        int pot = gameState.riichiPot;
        if (pot <= 0) {
            return;
        }
        rules.addSeatPoints(winnerSeat, pot);
        deltaLines.add(Component.translatable(
                "riichi_mahjong_forge.result.delta_riichi_pot", pot).getString());
        gameState.riichiPot = 0;
    }

    private void markUraDoraRevealedIfWinnerRiichi(int winnerSeat) {
        if (gameState == null || winnerSeat < 0 || winnerSeat >= SEAT_COUNT) {
            return;
        }
        if (winnerSeat < gameState.riichiDeclared.length && gameState.riichiDeclared[winnerSeat]) {
            uraDoraRevealed = true;
        }
    }

    private void advanceHandAfterWin(int winnerSeat) {
        if (gameState == null) {
            return;
        }
        boolean dealerWon = !deterministicPlayOrder().isEmpty() && deterministicPlayOrder().get(0) == winnerSeat;
        gameState.honba = dealerWon ? gameState.honba + 1 : 0;
        gameState.handNumber++;
        matchRound = gameState.handNumber;
        matchPhase = TableMatchPhase.HAND_RESULT;
    }

    public void serverTick(ServerLevel sl) {
        tickNormalizeFovInRadius(sl);
        if (!isInMatch() || gameState == null) {
            return;
        }
        if (tickPendingWinResultAnimation(sl)) {
            return;
        }
        if (isInHandResultPhase()) {
            return;
        }
        if (tickRoundSetup(sl)) {
            return;
        }
        if (gameState.isClaimWindowActive()) {
            for (int s : ClaimWindowRules.eligibleClaimants(deterministicPlayOrder(), gameState.claimDiscarderSeat)) {
                if (seatPlayers[s] != null) seatPlayers[s].tick(sl, this, s);
            }
        }
        tryResolveClaimWindowIfReady(sl);
        tickActiveTurnSeat(sl);
    }

    private void tickNormalizeFovInRadius(ServerLevel sl) {
        if (!normalizeFovInRadius) {
            return;
        }
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        double r2 = FOV_NORMALIZE_RADIUS_BLOCKS * FOV_NORMALIZE_RADIUS_BLOCKS;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(cx, cy, cz) <= r2) {
                applyNormalizeFovEffect(p);
            }
        }
    }

    private static void applyNormalizeFovEffect(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                RiichiMahjongForgeMod.NORMALIZE_FOV_EFFECT.get(),
                FOV_NORMALIZE_EFFECT_DURATION,
                0,
                true,
                false,
                false));
    }

    private void resetSeatPlayerDelays() {
        for (MahjongSeatPlayer sp : seatPlayers) {
            if (sp != null) sp.resetDelay();
        }
    }

    private void notifyAllJoined(ServerPlayer anyAtTable) {
        if (anyAtTable.getServer() == null) {
            return;
        }
        Component msg = Component.translatable("riichi_mahjong_forge.chat.lobby.all_joined", enabledSeatCount());
        for (int i = 0; i < SEAT_COUNT; i++) {
            UUID id = occupantAt(i);
            if (id == null) {
                continue;
            }
            ServerPlayer p = anyAtTable.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.displayClientMessage(msg, true);
            }
        }
    }

    public void resetAll(ServerPlayer actor) {
        if (isInMatch()) {
            abortMatch(MatchAbortReason.RESET);
        }
        // Reset lobby should keep the physical tiles: move everything back into table storage.
        returnAllTilesToTableStorage();
        clearAllSeatOccupants();
        rebuildSeatPlayers();
        MahjongMatchDefinition.SeatDefinition[] seats = new MahjongMatchDefinition.SeatDefinition[SEAT_COUNT];
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            seats[seat] = new MahjongMatchDefinition.SeatDefinition(true, null);
        }
        rules.withSeats(seats);
        matchPhase = TableMatchPhase.WAITING;
        matchRound = 0;
        clearRoundSetupState();
        resetSeatPlayerDelays();
        clearHandResultState();
        if (level instanceof ServerLevel sl && !sl.isClientSide()) {
            setTableLit(sl, false);
        }
        setChanged();
        syncToClients();
        actor.displayClientMessage(Component.translatable("riichi_mahjong_forge.chat.lobby.reset"), true);
    }

    /**
     * Ends the active match and returns to waiting state without moving/clearing table tiles or seats.
     * This is an admin-like stop action from settings to immediately unlock free tabletop edits.
     */
    public void endMatchToWaitingKeepTiles(ServerPlayer actor) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (!isInMatch()) {
            actor.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.end_match_not_in_match"), true);
            return;
        }
        endMatchToWaitingKeepTilesInternal(sl);
        setChanged();
        syncToClients();
        actor.displayClientMessage(
                Component.translatable("riichi_mahjong_forge.chat.game.end_match_to_waiting"), true);
    }

    private void endMatchToWaitingKeepTilesInternal(ServerLevel sl) {
        matchPhase = TableMatchPhase.WAITING;
        setTableLit(sl, false);
        matchRound = 0;
        gameState = null;
        clearRoundSetupState();
        resetSeatPlayerDelays();
        clearHandResultState();
    }

    private void returnAllTilesToTableStorage() {
        moveAllTilesToTableStorageAndMaybeDropOverflow(null);
    }

    private void returnAllTilesToTableStorageAndDropOverflow(ServerLevel sl) {
        moveAllTilesToTableStorageAndMaybeDropOverflow(sl);
    }

    private void moveAllTilesToTableStorageAndMaybeDropOverflow(@Nullable ServerLevel dropLevel) {
        ArrayList<ItemStack> tiles = new ArrayList<>(TILES_IN_TABLE_SLOTS);
        // Pull from existing table storage first so order is stable-ish.
        collectTileStacksFromRange(tiles, INV_TILES_IN_TABLE_START, TILES_IN_TABLE_SLOTS, false);
        // Pull from all other physical sections (hands/wall/dead wall/discards/open-meld display slots).
        collectTileStacksFromRange(tiles, INV_PLAYER_ZONE_START, PLAYER_ZONE_TOTAL_SLOTS, true);
        collectTileStacksFromRange(tiles, INV_OPEN_MELD_START, OPEN_MELD_TOTAL_SLOTS, true);
        collectTileStacksFromRange(tiles, INV_WALL_START, WALL_SLOTS, true);
        collectTileStacksFromRange(tiles, INV_DEAD_WALL_START, DEAD_WALL_SLOTS, true);
        collectTileStacksFromRange(tiles, INV_DISCARDS_START, DISCARDS_TOTAL_SLOTS, true);

        clearSection(INV_TILES_IN_TABLE_START, TILES_IN_TABLE_SLOTS);
        int kept = Math.min(TILES_IN_TABLE_SLOTS, tiles.size());
        for (int i = 0; i < kept; i++) {
            inventory.set(INV_TILES_IN_TABLE_START + i, tiles.get(i));
        }
        if (dropLevel != null) {
            double x = worldPosition.getX() + 0.5;
            double y = worldPosition.getY() + 1.0;
            double z = worldPosition.getZ() + 0.5;
            for (int i = kept; i < tiles.size(); i++) {
                Containers.dropItemStack(dropLevel, x, y, z, tiles.get(i));
            }
        }
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            beMelds[seat].clear();
        }
    }

    private void collectTileStacksFromRange(
            ArrayList<ItemStack> out, int start, int count, boolean clearSlots) {
        int end = Math.min(inventory.size(), start + count);
        for (int slot = start; slot < end; slot++) {
            ItemStack st = inventory.get(slot);
            if (st.isEmpty()) {
                continue;
            }
            Integer code = MahjongTileItems.codeForItem(st.getItem());
            if (code == null) {
                continue;
            }
            // Tile items are non-stackable in this mod, but be robust anyway.
            if (st.getCount() <= 1) {
                out.add(st);
                if (clearSlots) {
                    inventory.set(slot, ItemStack.EMPTY);
                }
            } else {
                int n = st.getCount();
                for (int i = 0; i < n; i++) {
                    out.add(new ItemStack(st.getItem()));
                }
                if (clearSlots) {
                    inventory.set(slot, ItemStack.EMPTY);
                } else {
                    st.setCount(1);
                    out.add(st);
                }
            }
        }
        if (clearSlots) {
            // Also clear any leftover non-tile items in these ranges; they don't belong in gameplay sections.
            for (int slot = start; slot < end; slot++) {
                ItemStack st = inventory.get(slot);
                if (!st.isEmpty() && MahjongTileItems.codeForItem(st.getItem()) == null) {
                    inventory.set(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    private int countTileItemsInRange(int start, int count) {
        int total = 0;
        int end = Math.min(inventory.size(), start + count);
        for (int slot = start; slot < end; slot++) {
            ItemStack st = inventory.get(slot);
            if (st.isEmpty()) {
                continue;
            }
            if (MahjongTileItems.codeForItem(st.getItem()) == null) {
                continue;
            }
            total += Math.max(1, st.getCount());
        }
        return total;
    }

    private int countTileItemsAcrossAllGameplaySections() {
        int total = 0;
        total += countTileItemsInRange(INV_TILES_IN_TABLE_START, TILES_IN_TABLE_SLOTS);
        total += countTileItemsInRange(INV_PLAYER_ZONE_START, PLAYER_ZONE_TOTAL_SLOTS);
        total += countTileItemsInRange(INV_OPEN_MELD_START, OPEN_MELD_TOTAL_SLOTS);
        total += countTileItemsInRange(INV_WALL_START, WALL_SLOTS);
        total += countTileItemsInRange(INV_DEAD_WALL_START, DEAD_WALL_SLOTS);
        total += countTileItemsInRange(INV_DISCARDS_START, DISCARDS_TOTAL_SLOTS);
        return total;
    }

    private int countTileItemsInTableStorage() {
        return countTileItemsInRange(INV_TILES_IN_TABLE_START, TILES_IN_TABLE_SLOTS);
    }

    private static Component notEnoughTilesComponent(int haveTiles, int requiredTiles) {
        return Component.translatable(
                "riichi_mahjong_forge.chat.lobby.need_tile_set_in_table_storage",
                Math.max(0, haveTiles),
                Math.max(0, requiredTiles));
    }

    public void fillTableWithFullTileSet() {
        if (!(level instanceof ServerLevel) || level.isClientSide()) {
            return;
        }
        clearSection(INV_TILES_IN_TABLE_START, TILES_IN_TABLE_SLOTS);
        ArrayList<Integer> codes = RoundSetupRules.sortedWallCodes();
        for (int i = 0; i < Math.min(TILES_IN_TABLE_SLOTS, codes.size()); i++) {
            inventory.set(INV_TILES_IN_TABLE_START + i, stackForCode(codes.get(i)));
        }
        setChanged();
        syncToClients();
    }

    public void fillTableWithFullTileSetCreativeOnly(ServerPlayer actor) {
        if (!(level instanceof ServerLevel) || level.isClientSide()) {
            return;
        }
        if (!actor.getAbilities().instabuild) {
            return;
        }
        // Only the creative fill action is allowed to generate new tile item stacks.
        fillTableWithFullTileSet();
        actor.displayClientMessage(
                Component.translatable("riichi_mahjong_forge.chat.lobby.filled_table_tiles"), true);
    }

    public void fillTableWith53RandomTilesCreativeOnly(ServerPlayer actor) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (!actor.getAbilities().instabuild) {
            return;
        }
        // Only the creative fill action is allowed to generate new tile item stacks.
        clearSection(INV_TILES_IN_TABLE_START, TILES_IN_TABLE_SLOTS);
        ArrayList<Integer> codes = RoundSetupRules.sortedWallCodes();
        if (codes.isEmpty()) {
            return;
        }
        Random rng = new Random(
                sl.getGameTime() ^ getBlockPos().asLong() ^ actor.getUUID().getLeastSignificantBits() ^ 0x53L);
        Collections.shuffle(codes, rng);
        int limit = Math.min(53, Math.min(TILES_IN_TABLE_SLOTS, codes.size()));
        for (int i = 0; i < limit; i++) {
            inventory.set(INV_TILES_IN_TABLE_START + i, stackForCode(codes.get(i)));
        }
        setChanged();
        syncToClients();
        actor.displayClientMessage(
                Component.translatable("riichi_mahjong_forge.chat.lobby.filled_table_tiles_53"), true);
    }

    public void fillNonTableSlotsWithRandomTilesCreativeOnly(ServerPlayer actor) {
        if (!(level instanceof ServerLevel sl) || level.isClientSide()) {
            return;
        }
        if (!actor.getAbilities().instabuild) {
            return;
        }
        ArrayList<Integer> codes = RoundSetupRules.sortedWallCodes();
        if (codes.isEmpty()) {
            return;
        }
        Random rng = new Random(
                sl.getGameTime() ^ getBlockPos().asLong() ^ actor.getUUID().getLeastSignificantBits());

        clearSection(INV_PLAYER_ZONE_START, PLAYER_ZONE_TOTAL_SLOTS);
        for (int i = 0; i < PLAYER_ZONE_TOTAL_SLOTS; i++) {
            int code = codes.get(rng.nextInt(codes.size()));
            inventory.set(INV_PLAYER_ZONE_START + i, stackForCode(code));
        }

        clearSection(INV_OPEN_MELD_START, OPEN_MELD_TOTAL_SLOTS);
        for (int i = 0; i < OPEN_MELD_TOTAL_SLOTS; i++) {
            int code = codes.get(rng.nextInt(codes.size()));
            inventory.set(INV_OPEN_MELD_START + i, stackForCode(code));
        }

        clearSection(INV_WALL_START, WALL_SLOTS);
        for (int i = 0; i < WALL_SLOTS; i++) {
            int code = codes.get(rng.nextInt(codes.size()));
            inventory.set(INV_WALL_START + i, stackForCode(code));
        }

        clearSection(INV_DEAD_WALL_START, DEAD_WALL_SLOTS);
        for (int i = 0; i < DEAD_WALL_SLOTS; i++) {
            int code = codes.get(rng.nextInt(codes.size()));
            inventory.set(INV_DEAD_WALL_START + i, stackForCode(code));
        }

        clearSection(INV_DISCARDS_START, DISCARDS_TOTAL_SLOTS);
        for (int i = 0; i < DISCARDS_TOTAL_SLOTS; i++) {
            int code = codes.get(rng.nextInt(codes.size()));
            inventory.set(INV_DISCARDS_START + i, stackForCode(code));
        }

        setChanged();
        syncToClients();
        actor.displayClientMessage(
                Component.translatable("riichi_mahjong_forge.chat.lobby.filled_non_table_tiles"), true);
    }


    public void cheatRinshanCreativeOnly(ServerPlayer actor) {
        if (!(level instanceof ServerLevel) || level.isClientSide()) {
            return;
        }
        if (!actor.getAbilities().instabuild) {
            return;
        }
        if (matchPhase != TableMatchPhase.IN_MATCH || gameState == null) {
            actor.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.cheat_rinshan_not_in_match"), true);
            return;
        }
        Integer seat = findSeatIndex(actor.getUUID());
        if (seat == null || !isSeatEnabled(seat) || !Objects.equals(occupantAt(seat), actor.getUUID())) {
            actor.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.not_seated"), true);
            return;
        }
        // Hand: ankan on 1-man (code 0), concealed 234p 567p 89p east-east, draw east (27).
        // Rinshan draw tile 7p (code 15) is at dead wall slot 0.
        int[] cheatRinshanHandCodes = {0, 0, 0, 0, 10, 11, 12, 13, 14, 15, 16, 17, 27, 27};
        int cheatRinshanKanTileCode = 0;
        int cheatRinshanDeadWallDrawCode = 15;
        setSeatCheatHandCodes(seat, cheatRinshanHandCodes);
        gameState.currentTurnSeat = seat;
        gameState.phase = MahjongGameState.TurnPhase.MUST_DISCARD;
        gameState.lastDrawnCode = cheatRinshanHandCodes[cheatRinshanHandCodes.length - 1];
        gameState.lastDrawWasRinshan = true;
        gameState.scoreAsNotFirstRound = true;
        int deadWallSlot = INV_DEAD_WALL_START;
        if (deadWallSlot >= 0 && deadWallSlot < inventory.size()) {
            inventory.set(deadWallSlot, stackForCode(cheatRinshanDeadWallDrawCode));
        }
        sortSeatHandTilesByTileCode(seat);
        closedKanSelectionSeat = -1;
        chiSelectionSeat = -1;
        setChanged();
        syncToClients();
        actor.displayClientMessage(
                Component.translatable(
                        "riichi_mahjong_forge.chat.game.cheat_rinshan_ready",
                        tileDisplayNameComponent(cheatRinshanKanTileCode),
                        tileDisplayNameComponent(cheatRinshanDeadWallDrawCode)),
                true);
    }


    public void cheatChankanCreativeOnly(ServerPlayer actor) {
        if (!(level instanceof ServerLevel) || level.isClientSide()) {
            return;
        }
        if (!actor.getAbilities().instabuild) {
            return;
        }
        if (matchPhase != TableMatchPhase.IN_MATCH || gameState == null) {
            actor.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.cheat_chankan_not_in_match"), true);
            return;
        }
        Integer seat = findSeatIndex(actor.getUUID());
        if (seat == null || !isSeatEnabled(seat) || !Objects.equals(occupantAt(seat), actor.getUUID())) {
            actor.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.not_seated"), true);
            return;
        }
        int idx = deterministicPlayOrder().indexOf(seat);
        if (idx < 0) {
            actor.displayClientMessage(
                    Component.translatable("riichi_mahjong_forge.chat.game.not_seated"), true);
            return;
        }
        int n = deterministicPlayOrder().size();
        int kakanSeat = deterministicPlayOrder().get((idx + 1) % n);

        // Set player's hand to tenpai waiting on 8-pin (code 16), with riichi already declared.
        // 234m + 567m + 234p + 567p + lone 8p = tenpai on 8p pair.
        setSeatCheatHandCodes(seat, new int[] {1, 2, 3, 4, 5, 6, 10, 11, 12, 13, 14, 15, 16});
        if (seat < gameState.riichiDeclared.length) {
            gameState.riichiDeclared[seat] = true;
            gameState.riichiPending[seat] = false;
        }
        Arrays.fill(gameState.ippatsuEligible, false);

        // Give the kakan seat a concealed hand (123s+456s+789s) + DAIMIN_KAN meld of 8-pin (code 16).
        setSeatCheatHandCodes(kakanSeat, new int[] {18, 19, 20, 21, 22, 23, 24, 25, 26});
        addSeatMeld(
                kakanSeat,
                new MahjongMeld(
                        MahjongMeld.Kind.DAIMIN_KAN,
                        new int[] {16, 16, 16, 16},
                        seat));

        // Open the chankan window directly — player will see the red CHANKAN button.
        openChankanWindow(kakanSeat, 16);

        // Pre-fill PASS for every other seat so the chankan window is already decided for them.
        for (int s : deterministicPlayOrder()) {
            if (s == seat || s == kakanSeat) {
                continue;
            }
            gameState.claimIntent[s] = ClaimWindowRules.ClaimIntent.PASS;
        }

        closedKanSelectionSeat = -1;
        chiSelectionSeat = -1;
        sortSeatHandTilesByTileCode(seat);
        logAtomicAction("CHEAT", "CHANKAN setup seat=" + seatLogLabel(seat) + " kakan=" + seatLogLabel(kakanSeat));
        setChanged();
        syncToClients();
        actor.displayClientMessage(
                Component.translatable(
                        "riichi_mahjong_forge.chat.game.cheat_chankan_ready",
                        tileDisplayNameComponent(16)),
                true);
    }

    private boolean hasChankanEligibles(int kakanSeat, int kanTileCode) {
        if (gameState == null) {
            return false;
        }
        int liveWall = getLiveWallRemainingFromInventory();
        List<Integer> doras = getDoraIndicatorCodesForScoring();
        boolean[] legalRonBySeat = new boolean[SEAT_COUNT];
        for (int s : deterministicPlayOrder()) {
            if (s == kakanSeat) {
                continue;
            }
            ArrayDeque<Integer> hand = readConcealedHandFromInventory(s);
            ArrayList<Mentsu> melds = meldsAsMentsuList(s);
            legalRonBySeat[s] = WinRules.canRon(
                    true,
                    s,
                    kakanSeat,
                    deterministicPlayOrder(),
                    gameState.riichiDeclared,
                    gameState.ippatsuEligible,
                    true,
                    kanTileCode,
                    hand,
                    melds,
                    gameState.handNumber,
                    gameState.scoreAsNotFirstRound,
                    liveWall,
                    doras);
        }
        return ClaimWindowRules.hasAnyChankanEligible(deterministicPlayOrder(), kakanSeat, legalRonBySeat);
    }

    private void openChankanWindow(int kakanSeat, int kanTileCode) {
        gameState.claimDiscarderSeat = kakanSeat;
        gameState.claimTileCode = kanTileCode;
        gameState.claimNextDrawerSeat = kakanSeat;
        gameState.claimIsChankanWindow = true;
        gameState.phase = MahjongGameState.TurnPhase.CLAIM_WINDOW;
        gameState.currentTurnSeat = kakanSeat;
        gameState.resetClaimIntentsForNewWindow();
    }

    private void setSeatCheatHandCodes(int seat, int[] handCodes) {
        int base = playerZoneBase(seat);
        clearSection(base, PLAYER_ZONE_SLOTS_PER_SEAT);
        clearSeatMelds(seat);
        for (int i = 0; i < handCodes.length && i < PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
            inventory.set(base + i, stackForCode(handCodes[i]));
        }
    }

    private static Component seatLabel(int seatIndex) {
        return Component.literal(Integer.toString(seatIndex + 1));
    }

    private static Component faceLabel(int seatIndex) {
        return Component.translatable(
                "riichi_mahjong_forge.chat.lobby.face." + faceFromSeat(seatIndex).getName());
    }

    @Override
    public int getContainerSize() {
        return INVENTORY_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack st : inventory) {
            if (!st.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < inventory.size() ? inventory.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack out = ContainerHelper.removeItem(inventory, slot, amount);
        if (!out.isEmpty()) {
            setChanged();
            syncToClients();
        }
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack out = ContainerHelper.takeItem(inventory, slot);
        if (!out.isEmpty()) {
            setChanged();
            syncToClients();
        }
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= inventory.size()) {
            return;
        }
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
        syncToClients();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (matchPhase == TableMatchPhase.IN_MATCH && !allowInventoryEditWhileInMatch) {
            return false;
        }
        if (isTileSlot(slot) && isWrenchItem(stack)) {
            return false;
        }
        return Container.super.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        if (matchPhase == TableMatchPhase.IN_MATCH && !allowInventoryEditWhileInMatch) {
            return false;
        }
        return Container.super.canTakeItem(target, slot, stack);
    }

    private static boolean isTileSlot(int slot) {
        return slot >= INV_TILES_IN_TABLE_START && slot < INVENTORY_SIZE;
    }

    private static boolean isWrenchItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(WRENCH_TAG);
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5)
                <= 8.0 * 8.0;
    }

    @Override
    public void clearContent() {
        clearSection(0, INVENTORY_SIZE);
        setChanged();
        syncToClients();
    }

    void syncToClients() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putByte("MtPh", (byte) matchPhase.ordinal());
        tag.putInt("MtRnd", matchRound);
        tag.putBoolean("MtAllowEditInMatch", allowInventoryEditWhileInMatch);
        tag.putBoolean("MtAllowGameplay", allowGameplay);
        tag.putBoolean("MtAllowCustomTilePack", allowCustomTilePack);
        tag.putBoolean("MtNormalizeFovInRadius", normalizeFovInRadius);
        tag.putBoolean("MtPassiveBots", passiveBots);
        tag.putInt("MtClosedKanSeat", closedKanSelectionSeat);
        tag.putInt("MtChiSelSeat", chiSelectionSeat);
        tag.putInt("MtLastDiscardSeat", lastDiscardSeat);
        tag.putBoolean("MtDoraHidden", doraHiddenDuringSetup);
        tag.putBoolean("MtUraRevealed", uraDoraRevealed);
        tag.putInt("MtLastDiscardSlot", lastDiscardSlotIndex);
        tag.putInt("MtLastMeldSeat", lastMeldSeat);
        tag.putInt("MtLastMeldClaimedSlot", lastMeldClaimedSlotIndex);
        tag.putString("MtResRound", handResultRoundTitle);
        tag.putString("MtResHead", handResultHeadline);
        tag.putInt("MtResColor", handResultHeadlineColor);
        tag.putString("MtResFooter", handResultFooter);
        tag.putInt("MtResFooterColor", handResultFooterColor);
        tag.putInt("MtResWinner", handResultWinnerSeat);
        ListTag yakuLines = new ListTag();
        for (String s : handResultYakuLines) {
            yakuLines.add(StringTag.valueOf(s));
        }
        tag.put("MtResYaku", yakuLines);
        ListTag deltaLines = new ListTag();
        for (String s : handResultDeltaLines) {
            deltaLines.add(StringTag.valueOf(s));
        }
        tag.put("MtResDelta", deltaLines);
        MahjongMatchDefinition.SeatDefinition[] seats = new MahjongMatchDefinition.SeatDefinition[SEAT_COUNT];
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            seats[seat] = new MahjongMatchDefinition.SeatDefinition(isSeatEnabled(seat), occupantAt(seat));
        }
        MahjongMatchDefinitionNbt.save(tag, rules.withSeats(seats));
        saveInventoryWithIntSlots(tag, inventory);
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            MahjongMeldNbt.saveMeldsList(tag, "MtMelds" + seat, beMelds[seat]);
        }
        if (gameState != null && matchPhase == TableMatchPhase.IN_MATCH) {
            CompoundTag g = new CompoundTag();
            MahjongGameStateNbt.save(gameState, g);
            tag.put("MtGame", g);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        try {
            // Never clear inventory or apply match state from a fragment tag: some loads merge minimal
            // compounds; returning after clearSection() used to leave IN_MATCH + MtGame while wiping the wall.
            if (!tag.contains("MatchRules", Tag.TAG_COMPOUND)) {
                return;
            }
            clearAllSeatOccupants();
            MahjongMatchDefinition.SeatDefinition[] defaultSeats = new MahjongMatchDefinition.SeatDefinition[SEAT_COUNT];
            for (int seat = 0; seat < SEAT_COUNT; seat++) {
                defaultSeats[seat] = new MahjongMatchDefinition.SeatDefinition(true, null);
            }
            rules.withSeats(defaultSeats);
            clearRoundSetupState();
            rules = MahjongMatchDefinitionNbt.load(tag, MahjongMatchDefinition.DEFAULT);
            allowInventoryEditWhileInMatch = tag.getBoolean("MtAllowEditInMatch");
            allowGameplay = !tag.contains("MtAllowGameplay") || tag.getBoolean("MtAllowGameplay");
            allowCustomTilePack =
                    !tag.contains("MtAllowCustomTilePack") || tag.getBoolean("MtAllowCustomTilePack");
            normalizeFovInRadius =
                    tag.contains("MtNormalizeFovInRadius") && tag.getBoolean("MtNormalizeFovInRadius");
            passiveBots = tag.contains("MtPassiveBots") && tag.getBoolean("MtPassiveBots");
            closedKanSelectionSeat =
                    tag.contains("MtClosedKanSeat", Tag.TAG_INT) ? tag.getInt("MtClosedKanSeat") : -1;
            chiSelectionSeat =
                    tag.contains("MtChiSelSeat", Tag.TAG_INT) ? tag.getInt("MtChiSelSeat") : -1;
            lastDiscardSeat =
                    tag.contains("MtLastDiscardSeat", Tag.TAG_INT) ? tag.getInt("MtLastDiscardSeat") : -1;
            doraHiddenDuringSetup = tag.contains("MtDoraHidden", Tag.TAG_BYTE) && tag.getBoolean("MtDoraHidden");
            uraDoraRevealed = tag.contains("MtUraRevealed", Tag.TAG_BYTE) && tag.getBoolean("MtUraRevealed");
            lastDiscardSlotIndex =
                    tag.contains("MtLastDiscardSlot", Tag.TAG_INT) ? tag.getInt("MtLastDiscardSlot") : -1;
            lastMeldSeat =
                    tag.contains("MtLastMeldSeat", Tag.TAG_INT) ? tag.getInt("MtLastMeldSeat") : -1;
            lastMeldClaimedSlotIndex =
                    tag.contains("MtLastMeldClaimedSlot", Tag.TAG_INT) ? tag.getInt("MtLastMeldClaimedSlot") : -1;
            handResultRoundTitle = tag.contains("MtResRound", Tag.TAG_STRING) ? tag.getString("MtResRound") : "";
            handResultHeadline = tag.contains("MtResHead", Tag.TAG_STRING) ? tag.getString("MtResHead") : "";
            handResultHeadlineColor = tag.contains("MtResColor", Tag.TAG_INT) ? tag.getInt("MtResColor") : 0xFFFFFF;
            handResultFooter = tag.contains("MtResFooter", Tag.TAG_STRING) ? tag.getString("MtResFooter") : "";
            handResultFooterColor =
                    tag.contains("MtResFooterColor", Tag.TAG_INT) ? tag.getInt("MtResFooterColor") : 0xFFFFFF;
            handResultWinnerSeat =
                    tag.contains("MtResWinner", Tag.TAG_INT) ? tag.getInt("MtResWinner") : -1;
            handResultYakuLines.clear();
            handResultDeltaLines.clear();
            if (tag.contains("MtResYaku", Tag.TAG_LIST)) {
                ListTag yl = tag.getList("MtResYaku", Tag.TAG_STRING);
                for (int i = 0; i < yl.size(); i++) {
                    handResultYakuLines.add(yl.getString(i));
                }
            }
            if (tag.contains("MtResDelta", Tag.TAG_LIST)) {
                ListTag dl = tag.getList("MtResDelta", Tag.TAG_STRING);
                for (int i = 0; i < dl.size(); i++) {
                    handResultDeltaLines.add(dl.getString(i));
                }
            }
            clearSection(0, INVENTORY_SIZE);
            if (tag.contains(INVENTORY_ITEMS_INT_TAG, Tag.TAG_LIST)) {
                loadInventoryWithIntSlots(tag, inventory);
            } else {
                // Backward compatibility for older saves written with ContainerHelper.
                // Note: for inventories >255 slots this legacy format can misplace high-index stacks on load.
                ContainerHelper.loadAllItems(tag, inventory);
            }
            MahjongMatchDefinition.SeatDefinition[] seats = rules.seats();
            for (int i = 0; i < Math.min(SEAT_COUNT, seats.length); i++) {
                MahjongMatchDefinition.SeatDefinition seat = seats[i];
                setSeatDefinition(i, seat.enabled(), seat.occupant());
            }
            rebuildSeatPlayers();
            if (tag.contains("MtPh", Tag.TAG_BYTE)) {
                int o = tag.getByte("MtPh") & 0xFF;
                TableMatchPhase[] vals = TableMatchPhase.values();
                matchPhase = o < vals.length ? vals[o] : TableMatchPhase.WAITING;
            } else {
                matchPhase = TableMatchPhase.WAITING;
            }
            matchRound = tag.getInt("MtRnd");
            gameState = null;
            if (tag.contains("MtGame", Tag.TAG_COMPOUND)) {
                gameState = MahjongGameStateNbt.load(tag.getCompound("MtGame"));
            }
            for (int seat = 0; seat < SEAT_COUNT; seat++) {
                String newKey = "MtMelds" + seat;
                String oldKey = "MtOpenMelds" + seat;
                if (tag.contains(newKey, Tag.TAG_LIST)) {
                    MahjongMeldNbt.loadMeldsList(tag, newKey, beMelds[seat]);
                } else if (tag.contains(oldKey, Tag.TAG_LIST)) {
                    MahjongMeldNbt.loadMeldsList(tag, oldKey, beMelds[seat]);
                } else {
                    beMelds[seat].clear();
                }
            }
            // Server-only invariant repair: on disk, an "in match" table must have a game state and a valid seat set.
            if (level instanceof ServerLevel && !level.isClientSide()
                    && isInMatch()
                    && (gameState == null || !canStartMatch())) {
                matchPhase = TableMatchPhase.WAITING;
                matchRound = 0;
                clearAllSeatOccupants();
                rebuildSeatPlayers();
                gameState = null;
                clearRoundSetupState();
                clearHandResultState();
            }
            // Note: open meld slots are currently a derived view; we do not materialize them into inventory on load
            // because that would spawn items. Meld legality/state uses {@link #beMelds} metadata directly.
            if (level != null && level.isClientSide()) {
                MahjongTableBlockEntity self = this;
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MahjongTableClientSide.afterTableBeData(self));
            }
        } finally {
            if (level instanceof ServerLevel sl && !level.isClientSide()) {
                setTableLit(sl, isInMatch());
            }
        }
    }

    private void setTableLit(ServerLevel sl, boolean lit) {
        BlockState masterState = sl.getBlockState(worldPosition);
        if (!(masterState.getBlock() instanceof MahjongTableBlock)) {
            return;
        }
        if (masterState.hasProperty(MahjongTableBlock.LIT) && masterState.getValue(MahjongTableBlock.LIT) == lit) {
            return;
        }
        // Update whole 3x3 so light is consistent even when only parts are loaded/rendered.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = worldPosition.offset(dx, 0, dz);
                BlockState st = sl.getBlockState(p);
                if (st.getBlock() instanceof MahjongTableBlock && st.hasProperty(MahjongTableBlock.LIT)) {
                    sl.setBlock(p, st.setValue(MahjongTableBlock.LIT, lit), Block.UPDATE_ALL);
                    sl.getChunkSource().getLightEngine().checkBlock(p);
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        // Intentionally sync the full BE snapshot (same shape as persisted save data).
        // This keeps client table rendering/UI and server save/load semantics aligned.
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static void saveInventoryWithIntSlots(CompoundTag tag, NonNullList<ItemStack> items) {
        ListTag out = new ListTag();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack st = items.get(slot);
            if (st.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            st.save(entry);
            out.add(entry);
        }
        tag.put(INVENTORY_ITEMS_INT_TAG, out);
    }

    private static void loadInventoryWithIntSlots(CompoundTag tag, NonNullList<ItemStack> items) {
        ListTag in = tag.getList(INVENTORY_ITEMS_INT_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < in.size(); i++) {
            CompoundTag entry = in.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= items.size()) {
                continue;
            }
            items.set(slot, ItemStack.of(entry));
        }
    }

}
