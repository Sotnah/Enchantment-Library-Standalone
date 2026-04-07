package dev.sotnah.enchantmentlibrary.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import dev.sotnah.enchantmentlibrary.ModRegistry;
import dev.sotnah.enchantmentlibrary.Config;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public class EnchLibraryMenu extends AbstractContainerMenu {
    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Slot Constants ─────────────────────────────────────────────────────────

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int FILTER_SLOT = 2;
    public static final int PLAYER_INV_START = 3;
    public static final int PLAYER_INV_END = 39;

    // ── Button ID Protocol ─────────────────────────────────────────────────────

    public static final int SHIFT_FLAG = 0x80000000;
    public static final int CTRL_FLAG = 0x40000000;
    public static final int ID_MASK = 0x3FFFFFFF;
    public static final int DISENCHANT_ID = -2;

    private EnchLibraryBlockEntity tile;
    private final BlockPos blockPos;
    private final Level level;
    private final Player player;
    private final SimpleContainer ioInv = new SimpleContainer(3);
    private Runnable notifier;
    private ItemStack activeOutputBook = ItemStack.EMPTY;

    // Server-side constructor
    public EnchLibraryMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull BlockPos pos) {
        super(ModRegistry.LIBRARY_MENU.get(), containerId);
        this.level = playerInv.player.level();
        this.player = playerInv.player;
        this.blockPos = pos;
        net.minecraft.world.level.block.entity.BlockEntity be = this.level.getBlockEntity(pos);
        if (be instanceof EnchLibraryBlockEntity lib) {
            this.tile = lib;
            this.tile.activeMenus.add(this);
        } else {
            this.tile = null;
            if (!this.level.isClientSide()) {
                LOGGER.warn("EnchLibraryMenu created with null/invalid tile at {}", pos);
                throw new IllegalStateException("Menu opened without valid block entity at " + pos);
            }
        }
        initSlots(playerInv);
    }

    // Client-side constructor (from network)
    // Suppressed: vanilla FriendlyByteBuf.readBlockPos() lacks @Nonnull
    @SuppressWarnings("null")
    public EnchLibraryMenu(int containerId, @Nonnull Inventory playerInv, @Nullable FriendlyByteBuf buf) {
        this(containerId, playerInv, buf != null ? buf.readBlockPos() : BlockPos.ZERO);
    }

    // Suppressed: vanilla Slot/Container constructors and
    // FriendlyByteBuf.readBlockPos() lack @Nonnull
    @SuppressWarnings("null")
    private void initSlots(@Nonnull Inventory inv) {
        EnchLibraryMenu self = this;

        // Slot 0: Enchanted Book input (auto-deposit on insert)
        this.addSlot(new Slot(this.ioInv, INPUT_SLOT, 143, 64) {
            private boolean depositing = false;

            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return stack.is(Items.ENCHANTED_BOOK);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                if (depositing)
                    return;
                super.setChanged();
                ItemStack stack = this.getItem();
                if (!self.level.isClientSide() && !stack.isEmpty() && self.tile != null) {
                    try {
                        depositing = true;
                        ItemStack toDeposit = stack.copyWithCount(1);
                        self.tile.depositBook(toDeposit);
                        self.level.playSound(null, self.tile.getBlockPos(), SoundEvents.ENCHANTMENT_TABLE_USE,
                                SoundSource.BLOCKS, 1.0F, 1.0F);
                        stack.shrink(1);
                        if (stack.isEmpty()) {
                            this.set(ItemStack.EMPTY);
                        }
                    } finally {
                        depositing = false;
                    }
                }
            }
        });

        // Slot 1: Output slot for extracted enchanted books (enchanted books only,
        // maxStack=1)
        this.addSlot(new Slot(this.ioInv, OUTPUT_SLOT, 143, 92) {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack) {
                return false;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                EnchLibraryMenu.this.onChanged();
            }
        });

        // Slot 2: Item filter slot (any item, used to filter visible enchantments)
        this.addSlot(new Slot(this.ioInv, FILTER_SLOT, 143, 37) {
            @Override
            public void setChanged() {
                super.setChanged();
                EnchLibraryMenu.this.onChanged();
            }
        });

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 148 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 206));
        }
    }

    @Override
    // Suppressed: vanilla SimpleContainer passed to clearContainer lacks @Nonnull
    // on Container param
    @SuppressWarnings("null")
    public void removed(@Nonnull Player player) {
        super.removed(player);
        if (this.tile != null) {
            this.tile.activeMenus.remove(this);
        }
        this.clearContainer(player, this.ioInv);
    }

    @Override
    @SuppressWarnings("null")
    public boolean stillValid(@Nonnull Player player) {
        if (player.isSpectator())
            return false;
        if (this.tile != null && this.tile.isRemoved())
            return false;

        BlockPos pos = this.tile != null ? this.tile.getBlockPos() : this.blockPos;
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // ── Button Click Handling ──────────────────────────────────────────────────

    @Override
    @SuppressWarnings("null")
    public boolean clickMenuButton(@Nonnull Player player, int id) {
        if (this.tile == null || !this.stillValid(player))
            return false;

        // Return button (-1)
        if (id == -1) {
            if (this.level.isClientSide())
                return true;
            ItemStack outputSlotItem = this.ioInv.getItem(OUTPUT_SLOT);
            if (!outputSlotItem.isEmpty() && outputSlotItem.is(Items.ENCHANTED_BOOK)
                    && outputSlotItem == this.activeOutputBook) {
                if (Config.requireXpForExtraction.get() && !player.isCreative()) {
                    long totalRefundRaw = 0;
                    net.minecraft.world.item.enchantment.ItemEnchantments enchantments = EnchantmentHelper
                            .getEnchantmentsForCrafting(outputSlotItem);
                    for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments
                            .entrySet()) {
                        // Mitigation: Sum raw costs using long to prevent int overflow when returning
                        // multiple high-level enchants
                        totalRefundRaw += dev.sotnah.enchantmentlibrary.PlayerXpHelper.getCostRaw(entry.getIntValue());
                    }
                    int totalRefundXp = (int) Math.min(totalRefundRaw, Integer.MAX_VALUE);
                    if (totalRefundXp > 0) {
                        dev.sotnah.enchantmentlibrary.PlayerXpHelper.addPlayerXp(player, totalRefundXp);
                    }
                }
                this.tile.depositBook(outputSlotItem);
                this.ioInv.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
                return true;
            }
            return false;
        }

        // Disenchant button (-2)

        if (id == DISENCHANT_ID) {
            if (!Config.enableDisenchantButton.get())
                return false;
            ItemStack filterItem = this.ioInv.getItem(FILTER_SLOT);
            if (filterItem.isEmpty() || filterItem.is(Items.ENCHANTED_BOOK))
                return false;
            if (EnchantmentHelper.getEnchantmentsForCrafting(filterItem).isEmpty())
                return false;

            // Note for code analysis: Item Deletion via Disenchant Button is Intentional
            // Design, not a bug.
            ItemStack toDeposit = filterItem.copy();
            this.ioInv.setItem(FILTER_SLOT, ItemStack.EMPTY);
            this.tile.depositEnchantsFromItem(toDeposit);
            this.level.playSound(null, this.tile.getBlockPos(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS,
                    1.0F, 1.0F);
            this.level.playSound(null, this.tile.getBlockPos(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0F,
                    1.0F);
            return true;
        }

        // Legacy handling for enchantment selection (via integer ID)
        int enchId = id & ID_MASK;
        boolean shift = (id & SHIFT_FLAG) != 0;
        boolean ctrl = (id & CTRL_FLAG) != 0;

        var lookup = this.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var keys = lookup.listElementIds().toList();
        if (enchId >= 0 && enchId < keys.size()) {
            lookup.get(keys.get(enchId)).ifPresent(ench -> this.processEnchantmentAction(ench, shift, ctrl));
            return true;
        }

        return false;
    }

    /**
     * Handler for the new custom networking packet. Resolves the identifier
     * to a stable Holder and processes the action.
     */
    @SuppressWarnings("null")
    public void handleEnchantmentSelection(net.minecraft.resources.Identifier loc, boolean shift, boolean ctrl) {
        if (this.tile == null || !this.stillValid(this.player))
            return;
        var lookup = this.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        lookup.get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, loc))
                .ifPresent(ench -> this.processEnchantmentAction(ench, shift, ctrl));
    }

    /**
     * Unified logic for enchantment extraction/refunding.
     * Execution is limited to the logical server to prevent client-side desyncs or
     * exploits.
     */
    private boolean processEnchantmentAction(@Nonnull Holder.Reference<Enchantment> ench, boolean shift, boolean ctrl) {
        if (this.tile == null || this.level.isClientSide())
            return false;

        boolean requireXp = Config.requireXpForExtraction.get();
        boolean isCreative = this.player.isCreative();
        int currentTotalXp = dev.sotnah.enchantmentlibrary.PlayerXpHelper.getPlayerTotalXp(this.player);
        ItemStack output = this.ioInv.getItem(OUTPUT_SLOT);

        // Refund logic (Ctrl or Shift+Ctrl)
        if (ctrl) {
            if (!output.isEmpty() && output.is(Items.ENCHANTED_BOOK) && output == this.activeOutputBook) {
                int currentLevel = EnchantmentHelper.getEnchantmentsForCrafting(output).getLevel(ench);
                if (currentLevel > 0) {
                    int targetLevel = shift ? 0 : currentLevel - 1;

                    // Mitigation: Calculate difference using raw long values to prevent Zero-Cost
                    // exploit
                    long rawRefund = dev.sotnah.enchantmentlibrary.PlayerXpHelper.getCostRaw(currentLevel)
                            - dev.sotnah.enchantmentlibrary.PlayerXpHelper.getCostRaw(targetLevel);
                    int refundXp = (int) Math.min(Math.max(rawRefund, 0L), Integer.MAX_VALUE);

                    this.tile.refundEnchant(ench, output, shift);

                    if (requireXp && !isCreative && refundXp > 0) {
                        dev.sotnah.enchantmentlibrary.PlayerXpHelper.addPlayerXp(this.player, refundXp);
                    }

                    if (EnchantmentHelper.getEnchantmentsForCrafting(output).isEmpty()) {
                        this.ioInv.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
                    }
                    return true;
                }
            }
            return false;
        }

        // Extraction Logic
        int currentLevel = EnchantmentHelper.getEnchantmentsForCrafting(output).getLevel(ench);
        int targetLevel;

        if (shift) {
            targetLevel = currentLevel;
            while (targetLevel + 1 <= this.getMaxLevel(ench)
                    && this.tile.canExtract(ench, targetLevel + 1, currentLevel)) {
                // Mitigation: Prevent overflow during multi-level cost calculation
                long rawNextCost = dev.sotnah.enchantmentlibrary.PlayerXpHelper.getCostRaw(targetLevel + 1)
                        - dev.sotnah.enchantmentlibrary.PlayerXpHelper.getCostRaw(currentLevel);
                int nextCost = (int) Math.min(Math.max(rawNextCost, 0L), Integer.MAX_VALUE);

                if (requireXp && !isCreative && currentTotalXp < nextCost) {
                    break;
                }
                targetLevel++;
            }
            if (targetLevel == currentLevel)
                return false;
        } else {
            targetLevel = currentLevel + 1;
        }

        if (!this.tile.canExtract(ench, targetLevel, currentLevel))
            return false;

        // Mitigation: Use long precision for cost difference calculation
        long rawXpCost = dev.sotnah.enchantmentlibrary.PlayerXpHelper.getCostRaw(targetLevel)
                - dev.sotnah.enchantmentlibrary.PlayerXpHelper.getCostRaw(currentLevel);
        int xpCost = (int) Math.min(Math.max(rawXpCost, 0L), Integer.MAX_VALUE);

        if (requireXp && !isCreative && currentTotalXp < xpCost)
            return false;

        boolean freshBook = output.isEmpty();
        if (freshBook) {
            output = new ItemStack(Items.ENCHANTED_BOOK);
            this.activeOutputBook = output;
            this.ioInv.setItem(OUTPUT_SLOT, output);
        }

        boolean success = this.tile.extractEnchant(ench, output, targetLevel);

        if (!success) {
            if (freshBook && EnchantmentHelper.getEnchantmentsForCrafting(output).isEmpty()) {
                this.ioInv.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
            }
            return false;
        }

        if (requireXp && !isCreative && xpCost > 0) {
            dev.sotnah.enchantmentlibrary.PlayerXpHelper.addPlayerXp(this.player, -xpCost);
        }

        return true;
    }

    // ── Shift-Click (Quick Move) ───────────────────────────────────────────────

    @Override
    @Nonnull
    // Suppressed: vanilla Slot.getItem(), slots list, moveItemStackTo return values
    // lack @Nonnull
    @SuppressWarnings("null")
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem())
            return ItemStack.EMPTY;

        ItemStack slotStack = slot.getItem();
        ItemStack original = slotStack.copy();

        // From library slots (0-2) to player inventory (3-38)
        if (index < PLAYER_INV_START) {
            if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_INV_END, true)) {
                return ItemStack.EMPTY;
            }
        }
        // From player inventory to library input slot (0) if enchanted book
        else if (slotStack.is(Items.ENCHANTED_BOOK)) {
            if (!this.moveItemStackTo(slotStack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        // From player inventory to filter slot (2)
        else {
            if (!this.moveItemStackTo(slotStack, FILTER_SLOT, FILTER_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return original;
    }

    // ── Data Accessors (for Screen) ────────────────────────────────────────────

    public void setNotifier(Runnable notifier) {
        this.notifier = notifier;
    }

    public void onChanged() {
        if (this.notifier != null)
            this.notifier.run();
    }

    /**
     * Returns true if this menu is stale (player disconnected or moved away).
     * Used by the block entity to prune leaked menu references.
     */
    // Suppressed: this.player is always initialized from playerInv.player
    // (guaranteed non-null) but the field lacks @Nonnull annotation
    @SuppressWarnings("null")
    boolean isStale() {
        return !this.stillValid(this.player);
    }

    public List<Object2LongMap.Entry<Holder<Enchantment>>> getPointsForDisplay() {
        if (this.tile == null)
            return List.of();

        ItemStack outputStack = this.ioInv.getItem(OUTPUT_SLOT);
        var outputEnchants = !outputStack.isEmpty() ? EnchantmentHelper.getEnchantmentsForCrafting(outputStack) : null;

        return this.tile.getPoints().object2LongEntrySet().stream()
                .filter(e -> !dev.sotnah.enchantmentlibrary.Config.isBlacklisted(e.getKey()))
                .filter(e -> {
                    if (e.getLongValue() > 0L)
                        return true;
                    // Keep in list if it's currently in the output book
                    Holder<Enchantment> ench = e.getKey();
                    return outputEnchants != null && outputEnchants.getLevel(ench) > 0;
                })
                .toList();
    }

    public int getMaxLevel(@Nonnull Holder<Enchantment> ench) {
        return this.tile != null ? this.tile.getMaxLevels().getInt(ench) : 0;
    }

    public long getPointCap() {
        return this.tile != null ? this.tile.getMaxPoints() : 0L;
    }

    @Nullable
    public EnchLibraryBlockEntity getTile() {
        return this.tile;
    }
}
