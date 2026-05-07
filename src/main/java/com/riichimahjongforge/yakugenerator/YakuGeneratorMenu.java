package com.riichimahjongforge.yakugenerator;

import com.riichimahjongforge.RiichiMahjongForgeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class YakuGeneratorMenu extends AbstractContainerMenu {

    public static final int BTN_RESET = 0;
    public static final int BTN_TSUMO = 1;
    public static final int BTN_TOGGLE_AUTO_SORT = 2;
    public static final int BTN_SLOT_BASE = 100;
    public static final int BTN_DEBUG_BASE = 200;
    public static final int DEBUG_OUTCOME_COUNT = 7;

    private static final int DATA_TILE_START = 0;
    private static final int DATA_TILE_END = DATA_TILE_START + YakuGeneratorBlockEntity.TILE_SLOTS;
    private static final int DATA_ENERGY = DATA_TILE_END;
    private static final int DATA_GEN_TICKS = DATA_ENERGY + 1;
    private static final int DATA_RFPT = DATA_GEN_TICKS + 1;
    private static final int DATA_LAST_HAN = DATA_RFPT + 1;
    private static final int DATA_LAST_RFPT = DATA_LAST_HAN + 1;
    private static final int DATA_LAST_DURATION = DATA_LAST_RFPT + 1;
    private static final int DATA_DRAWS_USED = DATA_LAST_DURATION + 1;
    private static final int DATA_DRAW_LIMIT = DATA_DRAWS_USED + 1;
    private static final int DATA_TIER_INDEX = DATA_DRAW_LIMIT + 1;
    private static final int DATA_SLOT_COUNT = DATA_TIER_INDEX + 1;
    private static final int DATA_FLAGS = DATA_SLOT_COUNT + 1;
    private static final int DATA_COUNT = DATA_FLAGS + 1;

    private final BlockPos machinePos;
    private final ContainerData data;

    public YakuGeneratorMenu(int containerId, Inventory inventory, YakuGeneratorBlockEntity machine) {
        this(containerId, inventory, machine.getBlockPos(), trackedData(machine));
    }

    public YakuGeneratorMenu(int containerId, Inventory inventory, BlockPos machinePos) {
        this(containerId, inventory, machinePos, new SimpleContainerData(DATA_COUNT));
    }

    private YakuGeneratorMenu(int containerId, Inventory inventory, BlockPos machinePos, ContainerData data) {
        super(RiichiMahjongForgeMod.YAKU_GENERATOR_MENU.get(), containerId);
        this.machinePos = machinePos;
        this.data = data;
        addDataSlots(data);
    }

    public static YakuGeneratorMenu fromNetwork(int windowId, Inventory inventory, FriendlyByteBuf buffer) {
        return new YakuGeneratorMenu(windowId, inventory, buffer.readBlockPos());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        BlockEntity be = player.level().getBlockEntity(machinePos);
        if (!(be instanceof YakuGeneratorBlockEntity machine)) {
            return false;
        }
        if (id == BTN_RESET) {
            machine.resetMachine();
            return true;
        }
        if (id == BTN_TSUMO) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                machine.tsumo(serverPlayer);
            }
            return true;
        }
        if (id == BTN_TOGGLE_AUTO_SORT) {
            machine.toggleAutoSortOnReroll();
            return true;
        }
        if (id >= BTN_SLOT_BASE && id < BTN_SLOT_BASE + YakuGeneratorBlockEntity.TILE_SLOTS) {
            machine.rerollSlot(id - BTN_SLOT_BASE);
            return true;
        }
        if (id >= BTN_DEBUG_BASE && id < BTN_DEBUG_BASE + DEBUG_OUTCOME_COUNT) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && serverPlayer.getAbilities().instabuild) {
                machine.debugTriggerOutcome(serverPlayer, id - BTN_DEBUG_BASE);
                return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(machinePos) instanceof YakuGeneratorBlockEntity)) {
            return false;
        }
        return player.distanceToSqr(machinePos.getX() + 0.5, machinePos.getY() + 0.5, machinePos.getZ() + 0.5) <= 8.0 * 8.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public BlockPos getMachinePos() {
        return machinePos;
    }

    public int getTileCode(int slot) {
        if (slot < 0 || slot >= YakuGeneratorBlockEntity.TILE_SLOTS) {
            return 0;
        }
        return data.get(DATA_TILE_START + slot);
    }

    public int getEnergyStored() {
        return data.get(DATA_ENERGY);
    }

    public int getGenerationTicksRemaining() {
        return data.get(DATA_GEN_TICKS);
    }

    public int getCurrentRfPerTick() {
        return data.get(DATA_RFPT);
    }

    public int getLastHan() {
        return data.get(DATA_LAST_HAN);
    }

    public int getLastRfPerTick() {
        return data.get(DATA_LAST_RFPT);
    }

    public int getLastDurationTicks() {
        return data.get(DATA_LAST_DURATION);
    }

    public boolean isLastYakuman() {
        return (data.get(DATA_FLAGS) & 1) != 0;
    }

    public boolean isAutoSortEnabled() {
        return (data.get(DATA_FLAGS) & 2) != 0;
    }

    public int getDrawsUsed() {
        return data.get(DATA_DRAWS_USED);
    }

    public int getDrawLimit() {
        return data.get(DATA_DRAW_LIMIT);
    }

    public int getDrawsRemaining() {
        return Math.max(0, getDrawLimit() - getDrawsUsed());
    }

    public int getTierIndex() {
        return Math.max(1, data.get(DATA_TIER_INDEX));
    }

    public int getSlotCount() {
        int v = data.get(DATA_SLOT_COUNT);
        if (v <= 0 || v > YakuGeneratorBlockEntity.TILE_SLOTS) {
            return YakuGeneratorBlockEntity.TILE_SLOTS;
        }
        return v;
    }

    private static ContainerData trackedData(YakuGeneratorBlockEntity machine) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index >= DATA_TILE_START && index < DATA_TILE_END) {
                    return machine.getTileCode(index - DATA_TILE_START);
                }
                return switch (index) {
                    case DATA_ENERGY -> machine.getEnergyStored();
                    case DATA_GEN_TICKS -> machine.getGenerationTicksRemaining();
                    case DATA_RFPT -> machine.getCurrentRfPerTick();
                    case DATA_LAST_HAN -> machine.getLastHan();
                    case DATA_LAST_RFPT -> machine.getLastRfPerTick();
                    case DATA_LAST_DURATION -> machine.getLastDurationTicks();
                    case DATA_DRAWS_USED -> machine.getDrawsUsed();
                    case DATA_DRAW_LIMIT -> machine.getDrawLimit();
                    case DATA_TIER_INDEX -> machine.getTierIndex();
                    case DATA_SLOT_COUNT -> machine.getSlotCount();
                    case DATA_FLAGS -> (machine.isLastYakuman() ? 1 : 0) | (machine.isAutoSortOnReroll() ? 2 : 0);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }
}
