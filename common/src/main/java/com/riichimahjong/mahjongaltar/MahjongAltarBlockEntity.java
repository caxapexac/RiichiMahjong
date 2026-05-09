package com.riichimahjong.mahjongaltar;

import com.riichimahjong.mahjongcore.MahjongModEvents;
import com.riichimahjong.registry.ModBlockEntities;
import com.riichimahjong.registry.ModRecipeTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MahjongAltarBlockEntity extends BlockEntity implements Container {
    private static final int SLOT_COUNT = 9;
    private static final int EVENT_RADIUS = 8;
    private static final double EVENT_RADIUS_SQR = EVENT_RADIUS * EVENT_RADIUS;

    /**
     * Live (loaded) altar BEs. Architectury Events delivers a single global listener;
     * we maintain this set so the listener can dispatch to all nearby altars without
     * needing per-instance bus subscriptions. Add on {@code onLoad}/{@code clearRemoved},
     * remove on {@code setRemoved}/{@code onChunkUnloaded}.
     */
    private static final Set<MahjongAltarBlockEntity> LIVE = ConcurrentHashMap.newKeySet();

    private final NonNullList<ItemStack> inventory = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public MahjongAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAHJONG_ALTAR_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MahjongAltarBlockEntity altar) {
    }

    /**
     * Wires the global {@link MahjongModEvents#ROUND_RESOLVED} listener that fans out
     * to all loaded altars within {@link #EVENT_RADIUS}. Call once from common init.
     */
    public static void registerEvents() {
        MahjongModEvents.ROUND_RESOLVED.register((level, pos, han) -> {
            for (MahjongAltarBlockEntity altar : LIVE) {
                if (altar.isRemoved() || altar.level != level) continue;
                if (pos.distSqr(altar.worldPosition) > EVENT_RADIUS_SQR) continue;
                altar.tryCraftForHan(level, han);
            }
        });
    }

    // 1.21 BE lifecycle changed — onLoad() and onChunkUnloaded() were removed.
    // setLevel fires when a BE is attached to a level (placement OR chunk-load); setRemoved
    // fires for both block-break AND chunk-unload. Together they cover the LIVE membership.
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel) LIVE.add(this);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level instanceof ServerLevel) LIVE.add(this);
    }

    @Override
    public void setRemoved() {
        LIVE.remove(this);
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
        AltarRecipeInput input = currentInput();
        ArrayList<MahjongAltarRecipe> out = new ArrayList<>();
        // 1.21: getAllRecipesFor returns List<RecipeHolder<T>>; the recipe is on .value().
        for (RecipeHolder<MahjongAltarRecipe> holder
                : sl.getRecipeManager().getAllRecipesFor(ModRecipeTypes.MAHJONG_ALTAR.get())) {
            MahjongAltarRecipe recipe = holder.value();
            if (recipe.matches(input, sl)) {
                out.add(recipe);
            }
        }
        return out;
    }

    private AltarRecipeInput currentInput() {
        ArrayList<ItemStack> items = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            items.add(inventory.get(i));
        }
        return new AltarRecipeInput(items);
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
        Ingredient ingredient = recipe.getIngredients().get(ingredientIndex);
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clearInventorySlots();
        ContainerHelper.loadAllItems(tag, inventory, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
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
