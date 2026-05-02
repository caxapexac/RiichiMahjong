package com.riichimahjongforge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MahjongAltarBlockEntity extends BlockEntity implements Container {
    private static final int SLOT_COUNT = 9;
    private static final int EVENT_RADIUS = 8;
    private static final double EVENT_RADIUS_SQR = EVENT_RADIUS * EVENT_RADIUS;

    private final NonNullList<ItemStack> inventory = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private boolean registeredToForgeBus;

    public MahjongAltarBlockEntity(BlockPos pos, BlockState state) {
        super(RiichiMahjongForgeMod.MAHJONG_ALTAR_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MahjongAltarBlockEntity altar) {
    }

    @Override
    public void onLoad() {
        super.onLoad();
        registerForgeListenerIfServer();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        registerForgeListenerIfServer();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterForgeListenerIfServer();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterForgeListenerIfServer();
        super.setRemoved();
    }

    public boolean insertOneFromPlayer(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return false;
        }
        int slot = firstEmptySlot();
        if (slot < 0) {
            return false;
        }
        ItemStack inserted = held.copy();
        inserted.setCount(1);
        inventory.set(slot, inserted);
        held.shrink(1);
        if (held.isEmpty()) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        }
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 1.15f);
        }
        setChangedAndSync();
        return true;
    }

    public boolean extractOneToPlayer(ServerPlayer player) {
        int slot = lastNonEmptySlot();
        if (slot < 0) {
            return false;
        }
        ItemStack extracted = inventory.get(slot);
        inventory.set(slot, ItemStack.EMPTY);
        if (!player.addItem(extracted.copy()) && level != null) {
            ItemEntity itemEntity = new ItemEntity(
                    level,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5,
                    extracted);
            level.addFreshEntity(itemEntity);
        }
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1.0f);
        }
        setChangedAndSync();
        return true;
    }

    public boolean extractAllToPlayer(ServerPlayer player) {
        boolean changed = false;
        for (int slot = SLOT_COUNT - 1; slot >= 0; slot--) {
            ItemStack extracted = inventory.get(slot);
            if (extracted.isEmpty()) {
                continue;
            }
            inventory.set(slot, ItemStack.EMPTY);
            if (!player.addItem(extracted.copy()) && level != null) {
                ItemEntity itemEntity = new ItemEntity(
                        level,
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 1.0,
                        worldPosition.getZ() + 0.5,
                        extracted);
                level.addFreshEntity(itemEntity);
            }
            changed = true;
        }
        if (changed) {
            if (level != null) {
                level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 0.95f);
            }
            setChangedAndSync();
        }
        return changed;
    }

    public List<ItemStack> renderStacks() {
        ArrayList<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    @SubscribeEvent
    public void onNearbyRoundResolved(MahjongRoundResolvedEvent event) {
        ServerLevel eventLevel = event.level();
        BlockPos sourcePos = event.sourcePos();
        int han = event.han();
        if (isRemoved() || level != eventLevel) {
            return;
        }
        if (sourcePos.distSqr(worldPosition) > EVENT_RADIUS_SQR) {
            return;
        }
        tryCraftForHan(eventLevel, han);
    }

    private void tryCraftForHan(ServerLevel sl, int han) {
        if (isEmpty()) {
            return;
        }
        List<MahjongAltarRecipe> matchingRecipes = matchingRecipes(sl);
        if (matchingRecipes.isEmpty()) {
            return;
        }
        matchingRecipes.sort(Comparator.comparingInt(MahjongAltarRecipe::minHan).reversed());
        for (MahjongAltarRecipe recipe : matchingRecipes) {
            if (han < recipe.minHan()) {
                continue;
            }
            if (!craft(sl, recipe)) {
                continue;
            }
            setChangedAndSync();
            return;
        }
    }

    private List<MahjongAltarRecipe> matchingRecipes(ServerLevel sl) {
        SimpleContainer container = new SimpleContainer(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            container.setItem(i, inventory.get(i));
        }
        ArrayList<MahjongAltarRecipe> out = new ArrayList<>();
        for (MahjongAltarRecipe recipe : sl.getRecipeManager().getAllRecipesFor(MahjongAltarRecipe.TYPE)) {
            if (recipe.matches(container, sl)) {
                out.add(recipe);
            }
        }
        return out;
    }

    private boolean craft(ServerLevel sl, MahjongAltarRecipe recipe) {
        int[] slotMatch = slotMatchForRecipe(recipe);
        if (slotMatch == null) {
            return false;
        }
        for (int slot : slotMatch) {
            ItemStack stack = inventory.get(slot);
            stack.shrink(1);
            if (stack.isEmpty()) {
                inventory.set(slot, ItemStack.EMPTY);
            }
        }
        ItemStack result = recipe.getResultItem(sl.registryAccess()).copy();
        ItemEntity output = new ItemEntity(
                sl,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 1.1,
                worldPosition.getZ() + 0.5,
                result);
        output.setDefaultPickUpDelay();
        sl.addFreshEntity(output);
        sl.playSound(null, worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.7f, 1.0f);
        return true;
    }

    private int[] slotMatchForRecipe(MahjongAltarRecipe recipe) {
        ArrayList<Integer> filledSlots = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!inventory.get(slot).isEmpty()) {
                filledSlots.add(slot);
            }
        }
        if (filledSlots.size() != recipe.getIngredients().size()) {
            return null;
        }
        int[] match = new int[recipe.getIngredients().size()];
        boolean[] used = new boolean[filledSlots.size()];
        return matchSlotsRecursive(recipe, filledSlots, used, 0, match) ? match : null;
    }

    private boolean matchSlotsRecursive(
            MahjongAltarRecipe recipe,
            List<Integer> filledSlots,
            boolean[] used,
            int ingredientIndex,
            int[] match) {
        if (ingredientIndex >= recipe.getIngredients().size()) {
            return true;
        }
        var ingredient = recipe.getIngredients().get(ingredientIndex);
        for (int i = 0; i < filledSlots.size(); i++) {
            if (used[i]) {
                continue;
            }
            int slot = filledSlots.get(i);
            if (!ingredient.test(inventory.get(slot))) {
                continue;
            }
            used[i] = true;
            match[ingredientIndex] = slot;
            if (matchSlotsRecursive(recipe, filledSlots, used, ingredientIndex + 1, match)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }

    private int firstEmptySlot() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (inventory.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int lastNonEmptySlot() {
        for (int i = SLOT_COUNT - 1; i >= 0; i--) {
            if (!inventory.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void registerForgeListenerIfServer() {
        if (registeredToForgeBus || !(level instanceof ServerLevel)) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        registeredToForgeBus = true;
    }

    private void unregisterForgeListenerIfServer() {
        if (!registeredToForgeBus || !(level instanceof ServerLevel)) {
            return;
        }
        MinecraftForge.EVENT_BUS.unregister(this);
        registeredToForgeBus = false;
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? inventory.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(inventory, slot, amount);
        if (!removed.isEmpty()) {
            setChangedAndSync();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = inventory.get(slot);
        inventory.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChangedAndSync();
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        clearInventorySlots();
        setChangedAndSync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, inventory);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        clearInventorySlots();
        ContainerHelper.loadAllItems(tag, inventory);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void clearInventorySlots() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            inventory.set(i, ItemStack.EMPTY);
        }
    }
}
