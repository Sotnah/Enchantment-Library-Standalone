package dev.sotnah.enchantmentlibrary.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

import dev.sotnah.enchantmentlibrary.ModRegistry;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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

    // ── Slot Constants ─────────────────────────────────────────────────────────

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int FILTER_SLOT = 2;
    public static final int PLAYER_INV_START = 3;
    public static final int PLAYER_INV_END = 39;

    private final EnchLibraryBlockEntity tile;
    private final Level level;
    private final Player player;
    private final SimpleContainer ioInv = new SimpleContainer(3);
    private Runnable notifier;

    // Server-side constructor
    public EnchLibraryMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull BlockPos pos) {
        super(ModRegistry.LIBRARY_MENU.get(), containerId);
        this.level = playerInv.player.level();
        this.player = playerInv.player;
        net.minecraft.world.level.block.entity.BlockEntity be = this.level.getBlockEntity(pos);
        if (be instanceof EnchLibraryBlockEntity lib) {
            this.tile = lib;
            this.tile.activeMenus.add(this);
        } else {
            this.tile = null;
            if (!this.level.isClientSide) {
                // Fallback: log warning (Adım 2)
                System.err.println(
                        "[EnchantmentLibrary] Warning: EnchLibraryMenu created with null/invalid tile at " + pos);
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
        this.addSlot(new Slot(this.ioInv, INPUT_SLOT, 142, 77) {
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
                super.setChanged();
                ItemStack stack = this.getItem();
                if (!self.level.isClientSide && !stack.isEmpty() && self.tile != null) {
                    ItemStack toDeposit = stack.copyWithCount(1);
                    self.tile.depositBook(toDeposit);
                    self.level.playSound(null, self.tile.getBlockPos(), SoundEvents.ENCHANTMENT_TABLE_USE,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        this.set(ItemStack.EMPTY);
                    }
                }
            }
        });

        // Slot 1: Output slot for extracted enchanted books (enchanted books only,
        // maxStack=1)
        this.addSlot(new Slot(this.ioInv, OUTPUT_SLOT, 142, 106) {
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
        this.addSlot(new Slot(this.ioInv, FILTER_SLOT, 142, 18) {
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
    public boolean stillValid(@Nonnull Player player) {
        if (player.isSpectator())
            return false; // Adım 1: Spectator kontrolü
        if (this.tile == null || this.tile.isRemoved())
            return false;
        return player.distanceToSqr(this.tile.getBlockPos().getX() + 0.5,
                this.tile.getBlockPos().getY() + 0.5, this.tile.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    // ── Button Click Handling ──────────────────────────────────────────────────

    @Override
    // Suppressed: vanilla registry.getHolder and ItemStack constructors lack
    // @Nonnull
    @SuppressWarnings("null")
    public boolean clickMenuButton(@Nonnull Player player, int id) {
        if (this.tile == null)
            return false;

        // Restore button (0x7FFFFFFE) - Returns the item in output slot to the library
        if (id == 0x7FFFFFFE) {
            ItemStack outputSlotItem = this.ioInv.getItem(OUTPUT_SLOT);
            if (!outputSlotItem.isEmpty() && outputSlotItem.is(Items.ENCHANTED_BOOK)) {
                this.tile.depositBook(outputSlotItem);
                this.ioInv.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
                return true;
            }
            return false;
        }

        boolean shift = (id & 0x80000000) != 0;
        boolean ctrl = (id & 0x40000000) != 0;
        int enchId = id & 0x3FFFFFFF;

        var registry = this.level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        Holder.Reference<Enchantment> ench = registry.getHolder(enchId).orElse(null);
        if (ench == null)
            return false;

        ItemStack output = this.ioInv.getItem(OUTPUT_SLOT);

        // Refund logic (Ctrl or Shift+Ctrl)
        if (ctrl) {
            if (!output.isEmpty() && output.is(Items.ENCHANTED_BOOK)) {
                this.tile.refundEnchant(ench, output, shift);

                if (EnchantmentHelper.getEnchantmentsForCrafting(output).isEmpty()) {
                    this.ioInv.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
                }

                return true;
            }
            return false;
        }

        if (output.isEmpty()) {
            output = new ItemStack(Items.ENCHANTED_BOOK);
            this.ioInv.setItem(OUTPUT_SLOT, output);
        }

        this.tile.extractEnchant(ench, output, shift);
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
                .filter(e -> {
                    if (e.getLongValue() > 0L)
                        return true;
                    // Keep in list if it's currently in the output book
                    return outputEnchants != null && outputEnchants.getLevel(e.getKey()) > 0;
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
