package dev.sotnah.enchantmentlibrary.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.sotnah.enchantmentlibrary.Config;
import dev.sotnah.enchantmentlibrary.PlayerXpHelper;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.Optional;

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
    private static final int ENCH_NAME_COLOR = 0xFFFFFF;
    private static final int FILTER_TEXT_COLOR = 0xFFFFFF;
    private static final int RETURN_BTN_X = 143;
    private static final int RETURN_BTN_Y = 116;
    private static final int RETURN_BTN_W = 13;
    private static final int RETURN_BTN_H = 13;

    private static final int DISENCHANT_BTN_X = 143;
    private static final int DISENCHANT_BTN_Y = 14;
    private static final int DISENCHANT_BTN_W = 13;
    private static final int DISENCHANT_BTN_H = 13;

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
    private int lastKnownPointCount = -1;

    public EnchLibraryScreen(@Nonnull EnchLibraryMenu menu, @Nonnull Inventory inv, @Nonnull Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 230;
        this.inventoryLabelY = this.imageHeight - 94;
        menu.setNotifier(this::containerChanged);
    }

    private boolean isOverReturnButton(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + RETURN_BTN_X && mouseX < this.leftPos + RETURN_BTN_X + RETURN_BTN_W
                && mouseY >= this.topPos + RETURN_BTN_Y && mouseY < this.topPos + RETURN_BTN_Y + RETURN_BTN_H;
    }

    private boolean isOverDisenchantButton(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + DISENCHANT_BTN_X && mouseX < this.leftPos + DISENCHANT_BTN_X + DISENCHANT_BTN_W
                && mouseY >= this.topPos + DISENCHANT_BTN_Y
                && mouseY < this.topPos + DISENCHANT_BTN_Y + DISENCHANT_BTN_H;
    }

    private void renderButtonHighlight(GuiGraphics gfx, int x, int y, int width, int height) {
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 100);
        gfx.fill(x, y, x + width, y + height, 0x80FFFFFF);
        gfx.pose().popPose();
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
    public void containerTick() {
        super.containerTick();
        EnchLibraryBlockEntity tile = this.menu.getTile();
        if (tile != null) {
            Object2LongMap<Holder<Enchantment>> points = tile.getPoints();
            int currentCount = points.size();
            long currentSum = 0;
            for (long v : points.values()) {
                currentSum += v;
            }
            int signature = currentCount * 31 + (int) (currentSum ^ (currentSum >>> 32));
            if (this.lastKnownPointCount != signature) {
                this.lastKnownPointCount = signature;
                this.containerChanged();
            }
        }
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

        // Render highlight for the return button correctly sized (13x13)
        if (isOverReturnButton(mouseX, mouseY)) {
            renderButtonHighlight(gfx, this.leftPos + RETURN_BTN_X, this.topPos + RETURN_BTN_Y, RETURN_BTN_W,
                    RETURN_BTN_H);
        }

        if (isOverDisenchantButton(mouseX, mouseY)) {
            renderButtonHighlight(gfx, this.leftPos + DISENCHANT_BTN_X, this.topPos + DISENCHANT_BTN_Y,
                    DISENCHANT_BTN_W, DISENCHANT_BTN_H);
        }

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

        // Enchantment name (scaled to fit, cached in LibrarySlot)
        Component name = slot.displayName();
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

        // Return button tooltip check
        if (isOverReturnButton(mouseX, mouseY)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("tooltip.enchlib.return_book"));

            if (Screen.hasShiftDown()) {
                tooltip.add(Component.translatable("tooltip.enchlib.return_book_desc",
                        Component.translatable("tooltip.enchlib.output_slot_name")
                                .withStyle(s -> s.withColor(0xFFAA00))));
            } else {
                tooltip.add(Component.translatable("tooltip.enchlib.shift_info").withStyle(ChatFormatting.GRAY));
            }

            boolean requireXp2 = Config.requireXpForExtraction.get();
            boolean isCreative2 = this.minecraft != null && this.minecraft.player != null
                    && this.minecraft.player.isCreative();
            if (requireXp2 && !isCreative2) {
                ItemStack outputSlotItem = this.menu.getSlot(1).getItem();
                if (!outputSlotItem.isEmpty() && outputSlotItem.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
                    int totalRefundXp = 0;
                    net.minecraft.world.item.enchantment.ItemEnchantments enchantments = EnchantmentHelper
                            .getEnchantmentsForCrafting(outputSlotItem);
                    for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments
                            .entrySet()) {
                        totalRefundXp += PlayerXpHelper.getCost(entry.getIntValue());
                    }
                    if (totalRefundXp > 0) {
                        int levelEquivalent = PlayerXpHelper.getLevelFromXp(totalRefundXp);
                        String formattedXp = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
                                .format(totalRefundXp);
                        MutableComponent xpLine = Component.literal("+" + formattedXp + " XP Refund")
                                .withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(" (" + levelEquivalent + " Level)")
                                        .withStyle(ChatFormatting.GRAY));
                        tooltip.add(xpLine);
                    }
                }
            }

            gfx.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
            return; // We return here to avoid overlapping tooltips.
        }

        // Disenchant button tooltip check
        if (isOverDisenchantButton(mouseX, mouseY)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("tooltip.enchlib.disenchant"));
            boolean disenchantEnabled = Config.enableDisenchantButton.get();
            if (!disenchantEnabled) {
                tooltip.add(Component.literal("§c§lDisabled from config."));
            }

            if (Screen.hasShiftDown()) {
                Component filterWord = Component.literal("FILTER").withStyle(ChatFormatting.GRAY);
                tooltip.add(Component.translatable("tooltip.enchlib.disenchant_desc", filterWord));
            } else {
                tooltip.add(Component.translatable("tooltip.enchlib.shift_info").withStyle(ChatFormatting.GRAY));
            }

            gfx.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
            return;
        }

        LibrarySlot hovered = this.getHoveredSlot(mouseX, mouseY);
        if (hovered != null) {
            EnchLibraryBlockEntity tile = this.menu.getTile();
            if (tile == null)
                return;
            List<Component> tooltip = new ArrayList<>();
            // Title
            boolean isCurse = hovered.ench.is(EnchantmentTags.CURSE);
            ChatFormatting titleColor = isCurse ? ChatFormatting.RED : ChatFormatting.WHITE;

            MutableComponent styledTitle = hovered.ench.value().description().copy()
                    .withStyle(titleColor, ChatFormatting.BOLD)
                    .append(Component.literal(" "))
                    .append(levelComponent(hovered.maxLevel).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            tooltip.add(styledTitle);

            if (net.neoforged.fml.ModList.get().isLoaded("enchdesc")) {
                hovered.ench.unwrapKey().ifPresent(key -> {
                    ResourceLocation loc = key.location();
                    String descKey = "enchantment." + loc.getNamespace() + "." + loc.getPath() + ".desc";
                    if (net.minecraft.client.resources.language.I18n.exists(descKey)) {
                        ChatFormatting descColor = isCurse ? ChatFormatting.DARK_RED : ChatFormatting.BLUE;
                        tooltip.add(Component.translatable(descKey).withStyle(descColor));
                    }
                });
            }

            // Points: §7Points: §a96 §8/ §25368.7M
            tooltip.add(Component.literal("Points: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(format(hovered.points)).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(format(this.menu.getPointCap())).withStyle(ChatFormatting.DARK_GREEN)));

            if (hovered.points <= 0) {
                tooltip.add(
                        Component.translatable("tooltip.enchlib.extraction_unavailable").withStyle(ChatFormatting.RED));
            }

            // Cost calculation (next level or max possible if Shift is held)
            ItemStack output = this.menu.getSlot(1).getItem();
            int currentLevel = output.isEmpty() ? 0
                    : EnchantmentHelper.getEnchantmentsForCrafting(output).getLevel(hovered.ench);

            boolean ctrl = Screen.hasControlDown();
            boolean shift = Screen.hasShiftDown();
            boolean requireXp = Config.requireXpForExtraction.get();
            boolean isCreative = this.minecraft != null && this.minecraft.player != null
                    && this.minecraft.player.isCreative();
            int playerXp = (this.minecraft != null && this.minecraft.player != null)
                    ? PlayerXpHelper.getPlayerTotalXp(this.minecraft.player)
                    : 0;

            if (ctrl) {
                if (currentLevel > 0) {
                    int nextLevel = shift ? 0 : currentLevel - 1;
                    long refundAmount = EnchLibraryBlockEntity.levelToPoints(currentLevel)
                            - EnchLibraryBlockEntity.levelToPoints(nextLevel);
                    int xpRefundAmount = PlayerXpHelper.getCost(currentLevel)
                            - PlayerXpHelper.getCost(nextLevel);

                    if (shift) {
                        MutableComponent removeLine = Component.literal("Removing: ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("Total").withStyle(ChatFormatting.GREEN))
                                .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                                .append(Component.literal(format(refundAmount) + " Pts")
                                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                        tooltip.add(removeLine);

                        if (requireXp && xpRefundAmount > 0) {
                            int levelEquivalent = PlayerXpHelper.getLevelFromXp(xpRefundAmount);
                            MutableComponent xpLine = Component.literal("XP Refund: ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal(xpRefundAmount + " XP").withStyle(ChatFormatting.GREEN))
                                    .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                                    .append(Component.literal("(" + levelEquivalent + " Level)")
                                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                            tooltip.add(xpLine);
                        }
                    } else {
                        MutableComponent refundLine = Component.literal("Refunding: ").withStyle(ChatFormatting.GOLD)
                                .append(levelComponent(currentLevel)
                                        .withStyle(ChatFormatting.GREEN))
                                .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                                .append(Component.literal(format(refundAmount) + " Pts")
                                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                        tooltip.add(refundLine);

                        if (requireXp && xpRefundAmount > 0) {
                            int levelEquivalent = PlayerXpHelper.getLevelFromXp(xpRefundAmount);
                            MutableComponent xpLine = Component.literal("XP Refund: ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal(xpRefundAmount + " XP").withStyle(ChatFormatting.GREEN))
                                    .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                                    .append(Component.literal("(" + levelEquivalent + " Level)")
                                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                            tooltip.add(xpLine);
                        }
                    }
                } else {
                    tooltip.add(Component.translatable("tooltip.enchlib.not_enchanted").withStyle(ChatFormatting.RED));
                }
            } else {
                int targetLevel;
                if (shift) {
                    targetLevel = currentLevel;
                    while (targetLevel + 1 <= hovered.maxLevel
                            && tile.canExtract(hovered.ench, targetLevel + 1, currentLevel)) {
                        int nextCost = PlayerXpHelper.getCost(targetLevel + 1)
                                - PlayerXpHelper.getCost(currentLevel);
                        if (requireXp && !isCreative && playerXp < nextCost) {
                            break;
                        }
                        targetLevel++;
                    }
                } else {
                    targetLevel = currentLevel + 1;
                }

                if (targetLevel > currentLevel && tile.canExtract(hovered.ench, targetLevel, currentLevel)) {
                    long totalCost = EnchLibraryBlockEntity.levelToPoints(targetLevel)
                            - EnchLibraryBlockEntity.levelToPoints(currentLevel);
                    int xpCost = dev.sotnah.enchantmentlibrary.PlayerXpHelper.getCost(targetLevel)
                            - dev.sotnah.enchantmentlibrary.PlayerXpHelper.getCost(currentLevel);

                    MutableComponent extractLine = Component.literal("Extracting: ").withStyle(ChatFormatting.GOLD)
                            .append(levelComponent(targetLevel)
                                    .withStyle(ChatFormatting.GREEN))
                            .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(format(totalCost) + " Pts")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE));
                    tooltip.add(extractLine);

                    if (requireXp && xpCost > 0) {
                        int levelEquivalent = PlayerXpHelper.getLevelFromXp(xpCost);
                        MutableComponent xpLine = Component.literal("XP Costs: ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal(xpCost + " XP").withStyle(ChatFormatting.GREEN))
                                .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                                .append(Component.literal("(" + levelEquivalent + " Level)")
                                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                        tooltip.add(xpLine);
                    }
                } else {
                    // Check if it's unavailable purely due to XP
                    if (targetLevel > currentLevel && !tile.canExtract(hovered.ench, targetLevel, currentLevel)) {
                        tooltip.add(
                                Component.translatable("tooltip.enchlib.unavailable").withStyle(ChatFormatting.RED));
                    } else if (requireXp && !isCreative && targetLevel > currentLevel) {
                        int xpCost2 = PlayerXpHelper.getCost(targetLevel)
                                - PlayerXpHelper.getCost(currentLevel);
                        if (playerXp < xpCost2) {
                            tooltip.add(Component.literal("Insufficient XP").withStyle(ChatFormatting.RED));
                            tooltip.add(Component.literal("Needs " + xpCost2 + " XP").withStyle(ChatFormatting.RED));
                        }
                    } else {
                        tooltip.add(
                                Component.translatable("tooltip.enchlib.unavailable").withStyle(ChatFormatting.RED));
                    }
                }
            }

            gfx.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    // Suppressed: vanilla Registries.ENCHANTMENT, SimpleSoundInstance, SoundEvents
    // lack @Nonnull
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.scrolling = false;

        if (this.handleReturnButtonClick(mouseX, mouseY))
            return true;
        if (this.handleDisenchantButtonClick(mouseX, mouseY))
            return true;
        if (this.handleScrollbarClick(mouseX, mouseY, button))
            return true;
        if (this.handleEnchantmentSlotClick(mouseX, mouseY))
            return true;
        if (this.handleFilterRightClick(mouseX, mouseY, button))
            return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @SuppressWarnings("null")
    private boolean handleReturnButtonClick(double mouseX, double mouseY) {
        if (isOverReturnButton(mouseX, mouseY)) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, -1);
                if (this.minecraft.getSoundManager() != null) {
                    this.minecraft.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings("null")
    private boolean handleDisenchantButtonClick(double mouseX, double mouseY) {
        if (isOverDisenchantButton(mouseX, mouseY)) {
            if (!dev.sotnah.enchantmentlibrary.Config.enableDisenchantButton.get()) {
                return true;
            }
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        EnchLibraryMenu.DISENCHANT_ID);
                if (this.minecraft.getSoundManager() != null) {
                    this.minecraft.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleScrollbarClick(double mouseX, double mouseY, int button) {
        if (mouseX >= this.leftPos + 14 && mouseX < this.leftPos + 18 && mouseY >= this.topPos + 29
                && mouseY < this.topPos + 132) {
            this.scrolling = this.data.size() > MAX_ENTRIES;
            this.mouseDragged(mouseX, mouseY, button, 0, 0);
            return true;
        }
        return false;
    }

    @SuppressWarnings("null")
    private boolean handleEnchantmentSlotClick(double mouseX, double mouseY) {
        LibrarySlot libSlot = this.getHoveredSlot((int) mouseX, (int) mouseY);
        if (libSlot != null) {
            if (this.minecraft != null && this.minecraft.level != null && this.minecraft.gameMode != null) {
                var registry = this.minecraft.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
                ResourceLocation enchId = registry.getKey(libSlot.ench.value());
                if (enchId != null) {
                    boolean shift = Screen.hasShiftDown();
                    boolean ctrl = Screen.hasControlDown();
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.sotnah.enchantmentlibrary.network.EnchSelectionPayload(enchId, shift, ctrl));
                }
                if (this.minecraft.getSoundManager() != null) {
                    this.minecraft.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleFilterRightClick(double mouseX, double mouseY, int button) {
        if (button == 1 && this.filter.isMouseOver(mouseX, mouseY)) {
            this.filter.setValue("");
            return true;
        }
        return false;
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
            String sortKey = ench.value().description().getString();

            // Name filter
            if (!filterText.isEmpty() && !sortKey.toLowerCase().contains(filterText))
                continue;

            // Item filter
            if (!filterItem.isEmpty() && !filterItem.supportsEnchantment(ench))
                continue;

            int maxLvl = this.menu.getMaxLevel(ench);
            Component displayName = ench.value().description().copy()
                    .append(Component.literal(" "))
                    .append(levelComponent(maxLvl));

            if (ench.is(EnchantmentTags.CURSE)) {
                displayName = displayName.copy().withStyle(net.minecraft.ChatFormatting.RED);
            } else {
                displayName = displayName.copy().withStyle(net.minecraft.ChatFormatting.WHITE);
            }

            this.data.add(new LibrarySlot(ench, e.getLongValue(), maxLvl, sortKey, displayName));
        }

        this.data.sort(Comparator.comparing(LibrarySlot::sortKey, String.CASE_INSENSITIVE_ORDER));

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
        if (value < 1_000L)
            return String.valueOf(value);
        if (value >= 1_000_000_000_000_000_000L)
            return String.format(java.util.Locale.US, "%.1fQn", value / 1_000_000_000_000_000_000.0D);
        if (value >= 1_000_000_000_000_000L)
            return String.format(java.util.Locale.US, "%.1fQd", value / 1_000_000_000_000_000.0D);
        if (value >= 1_000_000_000_000L)
            return String.format(java.util.Locale.US, "%.1fT", value / 1_000_000_000_000.0D);
        if (value >= 1_000_000_000L)
            return String.format(java.util.Locale.US, "%.1fB", value / 1_000_000_000.0D);
        if (value >= 1_000_000L)
            return String.format(java.util.Locale.US, "%.1fM", value / 1_000_000.0D);
        return String.format(java.util.Locale.US, "%.1fK", value / 1_000.0D);
    }

    /**
     * Converts a positive integer to Roman numeral string.
     * Returns plain decimal string for values beyond 10000.
     */
    @Nonnull
    @SuppressWarnings("null")
    private static String toRoman(int number) {
        if (number <= 0 || number > 10_000)
            return String.valueOf(number);
        int[] values = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] symbols = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                sb.append(symbols[i]);
                number -= values[i];
            }
        }
        return sb.toString();
    }

    /**
     * Returns a Component for an enchantment level.
     * Uses Roman numerals strictly up to 10000; above 10000 use plain decimal.
     */
    private static MutableComponent levelComponent(int level) {
        return Component.literal(toRoman(level));
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics gfx, int mouseX, int mouseY) {
        // suppress vanilla container title + "Inventory" label
    }

    private record LibrarySlot(Holder<Enchantment> ench, long points, int maxLevel, String sortKey,
            Component displayName) {
    }
}
