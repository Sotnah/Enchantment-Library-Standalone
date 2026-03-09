package dev.sotnah.enchantmentlibrary.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.sotnah.enchantmentlibrary.EnchantmentLibraryMod;
import dev.sotnah.enchantmentlibrary.block.EnchLibraryBlockEntity;
import dev.sotnah.enchantmentlibrary.block.EnchLibraryMenu;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class EnchLibraryScreen extends AbstractContainerScreen<EnchLibraryMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(EnchantmentLibraryMod.MOD_ID,
            "textures/gui/library.png");
    private static final int MAX_ENTRIES = 5;
    private static final int ENTRY_WIDTH = 113;
    private static final int ENTRY_HEIGHT = 20;
    private static final int SCROLLBAR_TOP_OFFSET = 29;
    private static final float SCROLLBAR_RANGE = 90F;
    private static final int SCROLLBAR_DRAG_HEIGHT = 103;
    private static final int PROGRESS_BAR_WIDTH = 85;
    private static final int ENCH_NAME_COLOR = 0x404040;
    private static final int FILTER_TEXT_COLOR = 0x404040;
    private static final int TOOLTIP_TITLE_COLOR = 0x40FFFF;

    // ── UI Coordinates ─────────────────────────────────────────────────────────
    private static final int FILTER_BOX_X = 16;
    private static final int FILTER_BOX_Y = 16;
    private static final int FILTER_BOX_WIDTH = 110;
    private static final int FILTER_BOX_HEIGHT = 11;

    private static final int SCROLLBAR_X_OFFSET = 13;
    private static final int ENTRY_START_X = 20;
    private static final int ENTRY_START_Y = 30;
    private static final int ENTRY_HOVER_X = 21;
    private static final int ENTRY_HOVER_Y = 31;

    private List<LibrarySlot> data = new ArrayList<>();
    private EditBox filter;
    private float scrollOffs = 0;
    private boolean scrolling = false;
    private int startIndex = 0;

    public EnchLibraryScreen(@Nonnull EnchLibraryMenu menu, @Nonnull Inventory inv, @Nonnull Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 230;
        this.inventoryLabelY = this.imageHeight - 94;
        menu.setNotifier(this::containerChanged);
    }

    // Suppressed: vanilla EditBox, font, Component factories lack @Nonnull
    @SuppressWarnings("null")
    @Override
    protected void init() {
        super.init();
        this.filter = new EditBox(this.font, this.leftPos + FILTER_BOX_X, this.topPos + FILTER_BOX_Y,
                FILTER_BOX_WIDTH, FILTER_BOX_HEIGHT,
                Component.translatable("tooltip.enchlib.nfilt"));
        this.filter.setBordered(false);
        this.filter.setTextColor(FILTER_TEXT_COLOR);
        this.filter.setResponder(s -> this.containerChanged());
        this.addRenderableWidget(this.filter);
        this.containerChanged();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.filter.isFocused() && keyCode != 256) {
            this.filter.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(@Nonnull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    // Suppressed: vanilla TEXTURE ResourceLocation and GuiGraphics.blit lack
    // @Nonnull
    @SuppressWarnings("null")
    protected void renderBg(@Nonnull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.blit(TEXTURE, this.leftPos, this.topPos, 0f, 0f, this.imageWidth, this.imageHeight, 307, 256);

        // Scrollbar
        int scrollbarPos = (int) (SCROLLBAR_RANGE * this.scrollOffs);
        boolean isScrollBarActive = this.data.size() > MAX_ENTRIES;
        gfx.blit(TEXTURE, this.leftPos + SCROLLBAR_X_OFFSET, this.topPos + SCROLLBAR_TOP_OFFSET + scrollbarPos, 303f,
                40f + (isScrollBarActive ? 0 : 12),
                4, 12, 307, 256);

        // Enchantment entries
        int idx = this.startIndex;
        while (idx < this.startIndex + MAX_ENTRIES && idx < this.data.size()) {
            this.renderEntry(gfx, this.data.get(idx), this.leftPos + ENTRY_START_X,
                    this.topPos + ENTRY_START_Y + ENTRY_HEIGHT * (idx - this.startIndex), mouseX, mouseY);
            idx++;
        }
    }

    // Suppressed: vanilla TEXTURE ResourceLocation, Enchantment.getFullname, Font
    // and Component lack @Nonnull
    @SuppressWarnings("null")
    private void renderEntry(@Nonnull GuiGraphics gfx, @Nonnull LibrarySlot slot, int x, int y, int mouseX,
            int mouseY) {
        LibrarySlot hovered = this.getHoveredSlot(mouseX, mouseY);

        // Background
        gfx.blit(TEXTURE, x, y, 194f, slot == hovered ? ENTRY_HEIGHT : 0f, ENTRY_WIDTH, ENTRY_HEIGHT, 307, 256);

        // Enchantment name (scaled to fit)
        Component name = Enchantment.getFullname(slot.ench, slot.maxLevel);
        int nameWidth = this.font.width(name);
        float scale = nameWidth > ENTRY_WIDTH - 4 ? (ENTRY_WIDTH - 4f) / nameWidth : 1.0F;

        gfx.pose().pushPose();
        gfx.pose().translate(x + 2, y + 2, 0);
        gfx.pose().scale(scale, scale, 1.0F);
        gfx.drawString(this.font, name, 0, 0, ENCH_NAME_COLOR, true);
        gfx.pose().popPose();

        // Points progress bar — texture strip at UV (197, 42) in library.png, up to 85
        // px wide
        long tierMaxPts = this.menu.getPointCap();
        int barPixels = tierMaxPts > 0L
                ? (int) Math.round((double) PROGRESS_BAR_WIDTH * slot.points / tierMaxPts)
                : 0;
        barPixels = Mth.clamp(barPixels, 0, PROGRESS_BAR_WIDTH);
        if (barPixels > 0) {
            gfx.blit(TEXTURE, x + 3, y + 14, 197f, 42f, barPixels, 3, 307, 256);
        }
    }

    @Override
    // Suppressed: vanilla renderTooltip, Enchantment.getFullname, Component, font
    // lack @Nonnull
    @SuppressWarnings("null")
    protected void renderTooltip(@Nonnull GuiGraphics gfx, int mouseX, int mouseY) {
        super.renderTooltip(gfx, mouseX, mouseY);

        LibrarySlot hovered = this.getHoveredSlot(mouseX, mouseY);
        if (hovered != null) {
            EnchLibraryBlockEntity tile = this.menu.getTile();
            if (tile == null)
                return;
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Enchantment.getFullname(hovered.ench, hovered.maxLevel)
                    .copy().withStyle(s -> s.withColor(TOOLTIP_TITLE_COLOR)));
            tooltip.add(Component.translatable("tooltip.enchlib.points",
                    format(hovered.points), format(this.menu.getPointCap())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.enchlib.max_lvl",
                    Component.translatable("enchantment.level." + hovered.maxLevel)).withStyle(ChatFormatting.GRAY));

            // Cost calculation (next level or max possible if Shift is held)
            ItemStack output = this.menu.getSlot(1).getItem();
            int currentLevel = output.isEmpty() ? 0
                    : EnchantmentHelper.getEnchantmentsForCrafting(output).getLevel(hovered.ench);

            int targetLevel;
            if (Screen.hasShiftDown()) {
                targetLevel = currentLevel;
                while (targetLevel + 1 <= hovered.maxLevel
                        && tile.canExtract(hovered.ench, targetLevel + 1, currentLevel)) {
                    targetLevel++;
                }
            } else {
                targetLevel = currentLevel + 1;
            }

            if (targetLevel > currentLevel && tile.canExtract(hovered.ench, targetLevel, currentLevel)) {
                long totalCost = EnchLibraryBlockEntity.levelToPoints(targetLevel)
                        - EnchLibraryBlockEntity.levelToPoints(currentLevel);
                tooltip.add(Component.translatable("tooltip.enchlib.extracting",
                        Component.translatable("enchantment.level." + targetLevel)).withStyle(ChatFormatting.GREEN));
                tooltip.add(Component.translatable("tooltip.enchlib.cost",
                        format(totalCost)).withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(Component.translatable("tooltip.enchlib.unavailable").withStyle(ChatFormatting.RED));
            }

            gfx.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    // Suppressed: vanilla Registries.ENCHANTMENT, SimpleSoundInstance, SoundEvents
    // lack @Nonnull
    @SuppressWarnings("null")
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.scrolling = false;

        // Scrollbar drag
        if (mouseX >= this.leftPos + 14 && mouseX < this.leftPos + 18 && mouseY >= this.topPos + 29
                && mouseY < this.topPos + 132) {
            this.scrolling = this.data.size() > MAX_ENTRIES;
            this.mouseDragged(mouseX, mouseY, button, 0, 0);
            return true;
        }

        // Enchantment slot click
        LibrarySlot libSlot = this.getHoveredSlot((int) mouseX, (int) mouseY);
        if (libSlot != null) {
            if (this.minecraft != null && this.minecraft.level != null && this.minecraft.gameMode != null) {
                var registry = this.minecraft.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
                int id = registry.getId(libSlot.ench.value());
                if (Screen.hasShiftDown())
                    id |= 0x80000000;
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
                if (this.minecraft.getSoundManager() != null) {
                    this.minecraft.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                }
            }
            return true;
        }

        // Right-click on filter clears it
        if (button == 1 && this.filter.isMouseOver(mouseX, mouseY)) {
            this.filter.setValue("");
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrolling && this.data.size() > MAX_ENTRIES) {
            int barTop = this.topPos + SCROLLBAR_TOP_OFFSET;
            int barBot = barTop + SCROLLBAR_DRAG_HEIGHT;
            this.scrollOffs = ((float) mouseY - barTop - 6F) / (barBot - barTop - 12F);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
            this.startIndex = (int) (this.scrollOffs * (this.data.size() - MAX_ENTRIES) + 0.5D);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.data.size() > MAX_ENTRIES) {
            this.scrollOffs = Mth.clamp(this.scrollOffs - (float) scrollY / (this.data.size() - MAX_ENTRIES), 0.0F,
                    1.0F);
            this.startIndex = Math.max(0, (int) (this.scrollOffs * (this.data.size() - MAX_ENTRIES) + 0.5));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ── Data Refresh ───────────────────────────────────────────────────────────

    // Suppressed: vanilla Enchantment, registry, and filter accessors lack @Nonnull
    @SuppressWarnings("null")
    private void containerChanged() {
        this.data.clear();

        List<Object2LongMap.Entry<Holder<Enchantment>>> entries = this.menu.getPointsForDisplay();
        ItemStack filterItem = this.menu.getSlot(2).getItem();
        String filterText = this.filter != null ? this.filter.getValue().toLowerCase() : "";

        for (Object2LongMap.Entry<Holder<Enchantment>> e : entries) {
            Holder<Enchantment> ench = e.getKey();

            // Name filter
            String enchName = Enchantment.getFullname(ench, 1).getString().toLowerCase();
            if (!filterText.isEmpty() && !enchName.contains(filterText))
                continue;

            // Item filter
            if (!filterItem.isEmpty() && !filterItem.supportsEnchantment(ench))
                continue;

            this.data.add(new LibrarySlot(ench, e.getLongValue(), this.menu.getMaxLevel(ench)));
        }

        this.data.sort(Comparator.comparing(s -> Enchantment.getFullname(s.ench, 1).getString()));

        if (this.data.size() <= MAX_ENTRIES) {
            this.scrollOffs = 0;
            this.startIndex = 0;
        }
    }

    @Nullable
    private LibrarySlot getHoveredSlot(int mouseX, int mouseY) {
        for (int i = 0; i < MAX_ENTRIES; i++) {
            if (this.startIndex + i < this.data.size()) {
                int x = this.leftPos + ENTRY_HOVER_X;
                int y = this.topPos + ENTRY_HOVER_Y + i * ENTRY_HEIGHT;
                if (mouseX >= x && mouseX < x + ENTRY_WIDTH && mouseY >= y && mouseY < y + ENTRY_HEIGHT - 2) {
                    return this.data.get(i + this.startIndex);
                }
            }
        }
        return null;
    }

    private static String format(long value) {
        if (value >= 1_000_000L)
            return String.format("%.1fM", value / 1_000_000.0D);
        if (value >= 1_000L)
            return String.format("%.1fK", value / 1_000.0D);
        return String.valueOf(value);
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics gfx, int mouseX, int mouseY) {
        // suppress vanilla container title + "Inventory" label
    }

    private record LibrarySlot(Holder<Enchantment> ench, long points, int maxLevel) {
    }
}
