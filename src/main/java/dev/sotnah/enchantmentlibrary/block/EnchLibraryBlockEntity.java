package dev.sotnah.enchantmentlibrary.block;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import dev.sotnah.enchantmentlibrary.Config;
import dev.sotnah.enchantmentlibrary.LibraryConstants;
import dev.sotnah.enchantmentlibrary.ModRegistry;
import dev.sotnah.enchantmentlibrary.component.LibraryData;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public abstract class EnchLibraryBlockEntity extends BlockEntity {

    // ── Tier definitions (single source of truth) ───────────────────────────
    public enum Tier {
        TIER1(LibraryConstants.DEFAULT_TIER1_MAX_LEVEL),
        TIER2(LibraryConstants.DEFAULT_TIER2_MAX_LEVEL),
        TIER3(LibraryConstants.DEFAULT_TIER3_MAX_LEVEL);

        public final int defaultMaxLevel;

        Tier(int defaultMaxLevel) {
            this.defaultMaxLevel = defaultMaxLevel;
        }
    }

    protected final Object2LongMap<Holder<Enchantment>> points = new Object2LongOpenHashMap<>();
    protected final Object2IntMap<Holder<Enchantment>> maxLevels = new Object2IntOpenHashMap<>();
    protected final Set<EnchLibraryMenu> activeMenus = ConcurrentHashMap.newKeySet();
    protected final IItemHandler itemHandler = new EnchLibItemHandler();

    protected final Tier tier;
    private int lastAppliedConfigEpoch = Integer.MIN_VALUE;

    protected EnchLibraryBlockEntity(@Nonnull BlockEntityType<?> type, @Nonnull BlockPos pos, @Nonnull BlockState state,
            Tier tier) {
        super(type, pos, state);
        this.tier = tier;
    }

    // ── Core Mechanics ─────────────────────────────────────────────────────────

    /**
     * Deposits all enchantments from an enchanted book into this library, consuming
     * the book.
     */
    // Suppressed: vanilla Items constants lack @Nonnull; Items.ENCHANTED_BOOK is
    // never null at runtime
    @SuppressWarnings("null")
    public void depositBook(@Nonnull ItemStack book) {
        this.ensureConfigApplied();
        if (!book.is(Items.ENCHANTED_BOOK))
            return;

        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(book);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Holder<Enchantment> ench = entry.getKey();
            if (Config.isBlacklisted(ench)) {
                continue;
            }
            int bookLevel = entry.getIntValue();

            long currentPoints = this.points.getLong(ench);
            long added = levelToPoints(bookLevel);
            long newPoints = currentPoints + added;
            if (newPoints < 0L)
                newPoints = this.getMaxPoints(); // overflow guard
            this.points.put(ench, Math.min(this.getMaxPoints(), newPoints));

            int currentMax = this.maxLevels.getInt(ench);
            this.maxLevels.put(ench, Math.min(this.getMaxLevel(), Math.max(currentMax, bookLevel)));
        }
        this.markUpdated();
    }

    /**
     * Deposits all enchantments from an item into this library.
     * <p>
     * The caller is responsible for consuming/clearing the item; this method only
     * updates the library's points and max-level tracking.
     */
    public void depositEnchantsFromItem(@Nonnull ItemStack stack) {
        this.ensureConfigApplied();
        if (stack.isEmpty())
            return;

        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (enchantments.isEmpty())
            return;

        long count = stack.getCount();
        if (count <= 0L)
            return;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Holder<Enchantment> ench = entry.getKey();
            if (Config.isBlacklisted(ench)) {
                continue;
            }
            int enchLevel = entry.getIntValue();

            long currentPoints = this.points.getLong(ench);
            long added = levelToPoints(enchLevel) * count;
            long newPoints = currentPoints + added;
            if (newPoints < 0L)
                newPoints = this.getMaxPoints(); // overflow guard
            this.points.put(ench, Math.min(this.getMaxPoints(), newPoints));

            int currentMax = this.maxLevels.getInt(ench);
            this.maxLevels.put(ench, Math.min(this.getMaxLevel(), Math.max(currentMax, enchLevel)));
        }

        this.markUpdated();
    }

    /**
     * Extracts an enchantment level onto an item (typically an enchanted book in
     * the output slot).
     *
     * @param ench        the enchantment to extract
     * @param stack       the target item stack
     * @param targetLevel the exact enchantment level to extract
     */
    public boolean extractEnchant(@Nonnull Holder<Enchantment> ench, @Nonnull ItemStack stack, int targetLevel) {
        this.ensureConfigApplied();
        int currentLevel = EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(ench);

        if (!canExtract(ench, targetLevel, currentLevel))
            return false;

        long cost = levelToPoints(targetLevel) - levelToPoints(currentLevel);

        // Final guard: verify points haven't changed since the canExtract() check to
        // prevent exploits
        long currentPoints = this.points.getLong(ench);
        if (currentPoints < cost) {
            return false;
        }

        this.points.put(ench, Math.max(0L, currentPoints - cost));

        stack.enchant(ench, targetLevel);

        this.markUpdated();
        return true;
    }

    /**
     * @return true if the library has enough points and the enchantment has been
     *         seen at the target level.
     */
    public boolean canExtract(@Nonnull Holder<Enchantment> ench, int targetLevel, int currentLevel) {
        this.ensureConfigApplied();
        if (Config.isBlacklisted(ench))
            return false;
        if (targetLevel <= currentLevel)
            return false;
        if (this.maxLevels.getInt(ench) < targetLevel)
            return false;
        long cost = levelToPoints(targetLevel) - levelToPoints(currentLevel);
        return this.points.getLong(ench) >= cost;
    }

    /**
     * Refunds an enchantment level (or points) from an item back into the library.
     */
    // Suppressed: vanilla ItemEnchantments.Mutable.toImmutable() lacks @Nonnull
    @SuppressWarnings("null")
    public void refundEnchant(@Nonnull Holder<Enchantment> ench, @Nonnull ItemStack stack, boolean all) {
        this.ensureConfigApplied();
        if (Config.isBlacklisted(ench)) {
            return;
        }
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        int currentLevel = enchantments.getLevel(ench);
        if (currentLevel <= 0)
            return;

        int nextLevel;
        if (all) {
            nextLevel = 0;
        } else {
            nextLevel = currentLevel - 1;
        }

        long refundAmount = levelToPoints(currentLevel) - levelToPoints(nextLevel);

        long newPoints = this.points.getLong(ench) + refundAmount;
        if (newPoints < 0L)
            newPoints = this.getMaxPoints(); // overflow guard
        this.points.put(ench, Math.min(this.getMaxPoints(), newPoints));

        // Update the book's enchantments
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        if (nextLevel > 0) {
            mutable.set(ench, nextLevel);
        } else {
            mutable.removeIf(h -> h.equals(ench));
        }
        EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());

        this.markUpdated();
    }

    public static long levelToPoints(int level) {
        if (level <= 0)
            return 0L;
        return 1L << (level - 1); // 2^(level-1)
    }

    // ── Data Component Conversion ──────────────────────────────────────────────

    @Override
    @SuppressWarnings("null")
    protected void collectImplicitComponents(@Nonnull DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModRegistry.LIBRARY_DATA.get(), this.toLibraryData());
    }

    /**
     * Converts runtime holder-keyed maps to a serializable {@link LibraryData} for
     * item persistence.
     */
    @Nonnull
    public LibraryData toLibraryData() {
        Object2LongMap<ResourceLocation> pts = new Object2LongOpenHashMap<>();
        for (Object2LongMap.Entry<Holder<Enchantment>> e : this.points.object2LongEntrySet()) {
            e.getKey().unwrapKey().ifPresent(key -> pts.put(key.location(), e.getLongValue()));
        }
        Object2IntMap<ResourceLocation> lvls = new Object2IntOpenHashMap<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> e : this.maxLevels.object2IntEntrySet()) {
            e.getKey().unwrapKey().ifPresent(key -> lvls.put(key.location(), e.getIntValue()));
        }
        return new LibraryData(pts, lvls);
    }

    /**
     * Loads a {@link LibraryData} component from an item into this block entity's
     * runtime maps.
     */
    // Suppressed: vanilla ResourceKey/ResourceLocation factory methods lack
    // @Nonnull; outputs are guaranteed non-null
    @SuppressWarnings("null")
    public void loadLibraryData(@Nonnull LibraryData data, @Nonnull RegistryLookup<Enchantment> lookup) {
        this.points.clear();
        this.maxLevels.clear();
        data.points().forEach((loc, val) -> lookup.get(ResourceKey.create(Registries.ENCHANTMENT, loc))
                .ifPresent(holder -> this.points.put(holder, val.longValue())));
        data.maxLevels().forEach((loc, val) -> lookup.get(ResourceKey.create(Registries.ENCHANTMENT, loc))
                .ifPresent(holder -> this.maxLevels.put(holder, val.intValue())));
        this.ensureConfigApplied();
    }

    // ── BlockEntity NBT (world save/load) ──────────────────────────────────────

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeEnchData(tag, registries);
    }

    @Override
    // Suppressed: vanilla Registries.ENCHANTMENT and RegistryLookup.lookupOrThrow
    // lack @Nonnull
    @SuppressWarnings("null")
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readEnchData(tag, registries.lookupOrThrow(Registries.ENCHANTMENT));
        this.ensureConfigApplied();
    }

    @Override
    @Nonnull
    // Suppressed: vanilla CompoundTag from super.getUpdateTag() lacks @Nonnull
    @SuppressWarnings("null")
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeEnchData(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    // Suppressed: vanilla Registries.ENCHANTMENT and RegistryLookup.lookupOrThrow
    // lack @Nonnull
    @SuppressWarnings("null")
    public void onDataPacket(
            @Nonnull net.minecraft.network.Connection connection,
            @Nonnull ClientboundBlockEntityDataPacket packet,
            @Nonnull net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            readEnchData(tag, registries.lookupOrThrow(Registries.ENCHANTMENT));
        }
        notifyMenus();
    }

    // Suppressed: vanilla HolderLookup and Holder accessor methods lack @Nonnull
    @SuppressWarnings("null")
    private void writeEnchData(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        CompoundTag pointsTag = new CompoundTag();
        CompoundTag levelsTag = new CompoundTag();

        for (Object2LongMap.Entry<Holder<Enchantment>> e : this.points.object2LongEntrySet()) {
            e.getKey().unwrapKey().ifPresent(key -> pointsTag.putLong(key.location().toString(), e.getLongValue()));
        }
        for (Object2IntMap.Entry<Holder<Enchantment>> e : this.maxLevels.object2IntEntrySet()) {
            e.getKey().unwrapKey().ifPresent(key -> levelsTag.putInt(key.location().toString(), e.getIntValue()));
        }

        tag.put("points", pointsTag);
        tag.put("max_levels", levelsTag);
    }

    // Suppressed: vanilla ResourceKey.create and ResourceLocation.tryParse lack
    // @Nonnull
    @SuppressWarnings("null")
    private void readEnchData(@Nonnull CompoundTag tag, @Nonnull RegistryLookup<Enchantment> lookup) {
        this.points.clear();
        this.maxLevels.clear();

        if (tag.contains("points")) {
            CompoundTag pointsTag = tag.getCompound("points");
            for (String key : pointsTag.getAllKeys()) {
                ResourceLocation loc = ResourceLocation.tryParse(key);
                if (loc != null) {
                    lookup.get(ResourceKey.create(Registries.ENCHANTMENT, loc))
                            .ifPresent(holder -> this.points.put(holder, pointsTag.getLong(key)));
                }
            }
        }

        if (tag.contains("max_levels")) {
            CompoundTag levelsTag = tag.getCompound("max_levels");
            for (String key : levelsTag.getAllKeys()) {
                ResourceLocation loc = ResourceLocation.tryParse(key);
                if (loc != null) {
                    lookup.get(ResourceKey.create(Registries.ENCHANTMENT, loc))
                            .ifPresent(holder -> this.maxLevels.put(holder, levelsTag.getInt(key)));
                }
            }
        }
        this.ensureConfigApplied();
    }

    private void ensureConfigApplied() {
        int epoch = Config.getTierConfigEpoch();
        if (this.lastAppliedConfigEpoch == epoch) {
            return;
        }
        this.lastAppliedConfigEpoch = epoch;
        boolean changed = this.applyConfigConstraints();
        if (changed && this.level != null && !this.level.isClientSide) {
            this.markUpdated();
        }
    }

    private boolean applyConfigConstraints() {
        boolean changed = false;
        int tierMaxLevel = this.getMaxLevel();
        long tierMaxPoints = this.getMaxPoints();

        var pointsIt = this.points.object2LongEntrySet().iterator();
        while (pointsIt.hasNext()) {
            Object2LongMap.Entry<Holder<Enchantment>> entry = pointsIt.next();
            if (Config.isBlacklisted(entry.getKey())) {
                pointsIt.remove();
                changed = true;
                continue;
            }
            long clamped = Math.max(0L, Math.min(tierMaxPoints, entry.getLongValue()));
            if (clamped != entry.getLongValue()) {
                entry.setValue(clamped);
                changed = true;
            }
        }

        var levelIt = this.maxLevels.object2IntEntrySet().iterator();
        while (levelIt.hasNext()) {
            Object2IntMap.Entry<Holder<Enchantment>> entry = levelIt.next();
            if (Config.isBlacklisted(entry.getKey())) {
                levelIt.remove();
                changed = true;
                continue;
            }
            int clamped = Math.max(0, Math.min(tierMaxLevel, entry.getIntValue()));
            if (clamped != entry.getIntValue()) {
                entry.setValue(clamped);
                changed = true;
            }
        }
        return changed;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    // Suppressed: vanilla Level.sendBlockUpdated, BlockState, Block.UPDATE_CLIENTS
    // lack @Nonnull
    @SuppressWarnings("null")
    private void syncToClient() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    private void notifyMenus() {
        // Prune stale menus (player disconnected, crashed, or moved away)
        this.activeMenus.removeIf(EnchLibraryMenu::isStale);
        for (EnchLibraryMenu menu : this.activeMenus) {
            menu.onChanged();
        }
    }

    /**
     * Marks the block entity as changed, syncs it to tracking clients, and notifies
     * active menus.
     * This ensures external observers of this block entity are updated.
     */
    protected void markUpdated() {
        this.setChanged();
        this.syncToClient();
        this.notifyMenus();
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    @Nonnull
    // Suppressed: vanilla Object2LongMap (fastutil) return type lacks @Nonnull
    @SuppressWarnings("null")
    public Object2LongMap<Holder<Enchantment>> getPoints() {
        this.ensureConfigApplied();
        return Object2LongMaps.unmodifiable(this.points);
    }

    @Nonnull
    // Suppressed: vanilla Object2IntMap (fastutil) return type lacks @Nonnull
    @SuppressWarnings("null")
    public Object2IntMap<Holder<Enchantment>> getMaxLevels() {
        this.ensureConfigApplied();
        return Object2IntMaps.unmodifiable(this.maxLevels);
    }

    public int getMaxLevel() {
        return Config.getTierLimits(this.tier).maxLevel();
    }

    public long getMaxPoints() {
        return Config.getTierLimits(this.tier).maxPoints();
    }

    @Nonnull
    // Suppressed: vanilla IItemHandler interface return type lacks @Nonnull
    @SuppressWarnings("null")
    public IItemHandler getItemHandler() {
        return this.itemHandler;
    }

    // ── IItemHandler (hopper support) ──────────────────────────────────────────

    private class EnchLibItemHandler implements IItemHandlerModifiable {

        private ItemStack stack = ItemStack.EMPTY;

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        @Nonnull
        // Suppressed: vanilla ItemStack.EMPTY field lacks @Nonnull
        @SuppressWarnings("null")
        public ItemStack getStackInSlot(int slot) {
            return this.stack;
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            this.stack = stack;
        }

        /**
         * Acts as a "deposit chute" for automated systems (like hoppers).
         * Enchanted books inserted here are immediately consumed and converted to
         * points,
         * rather than being stored in a physical slot. This keeps the handler logically
         * "empty"
         * even during rapid insertion.
         */
        @Override
        @Nonnull
        // Suppressed: vanilla Items.ENCHANTED_BOOK and ItemStack.EMPTY lack @Nonnull
        @SuppressWarnings("null")
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            // Validation: Only slot 0, non-empty enchanted books, and respect our own slot
            // limit
            if (slot != 0 || stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK) || !this.stack.isEmpty())
                return stack;

            // We behave as a slot with limit 1: accept 1, return the rest.
            ItemStack remainder = stack.copy();
            remainder.shrink(1);

            if (!simulate) {
                ItemStack toDeposit = stack.copyWithCount(1);
                EnchLibraryBlockEntity.this.depositBook(toDeposit);
            }

            return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
        }

        @Override
        @Nonnull
        // Suppressed: vanilla ItemStack.EMPTY field lacks @Nonnull
        @SuppressWarnings("null")
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        // Suppressed: vanilla Items.ENCHANTED_BOOK field lacks @Nonnull
        @SuppressWarnings("null")
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.is(Items.ENCHANTED_BOOK);
        }
    }

    // ── Tier Implementations ───────────────────────────────────────────────────

    public static class Tier1Tile extends EnchLibraryBlockEntity {
        // Suppressed: ModRegistry.BLOCK_ENTITY_TIER1.get() (DeferredHolder) lacks
        // @Nonnull
        @SuppressWarnings("null")
        public Tier1Tile(@Nonnull BlockPos pos, @Nonnull BlockState state) {
            super(ModRegistry.BLOCK_ENTITY_TIER1.get(), pos, state, Tier.TIER1);
        }
    }

    public static class Tier2Tile extends EnchLibraryBlockEntity {
        // Suppressed: ModRegistry.BLOCK_ENTITY_TIER2.get() (DeferredHolder) lacks
        // @Nonnull
        @SuppressWarnings("null")
        public Tier2Tile(@Nonnull BlockPos pos, @Nonnull BlockState state) {
            super(ModRegistry.BLOCK_ENTITY_TIER2.get(), pos, state, Tier.TIER2);
        }
    }

    public static class Tier3Tile extends EnchLibraryBlockEntity {
        // Suppressed: ModRegistry.BLOCK_ENTITY_TIER3.get() (DeferredHolder) lacks
        // @Nonnull
        @SuppressWarnings("null")
        public Tier3Tile(@Nonnull BlockPos pos, @Nonnull BlockState state) {
            super(ModRegistry.BLOCK_ENTITY_TIER3.get(), pos, state, Tier.TIER3);
        }
    }
}
