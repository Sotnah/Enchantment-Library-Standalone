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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.Optional;

public class EnchLibraryScreen extends AbstractContainerScreen<EnchLibraryMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(EnchantmentLibraryMod.MOD_ID,
            "textures/gui/library.png");
    private static final int MAX_ENTRIES = 5;
    private static final int ENTRY_WIDTH = 113;
    private static final int ENTRY_HEIGHT = 20;
    private static final int SCROLLBAR_TOP_OFFSET = 29;
    private static final float SCROLLBAR_RANGE = 90F;
    private static final int SCROLLBAR_DRAG_HEIGHT = 103;
    private static final int PROGRESS_BAR_WIDTH = 85;
    private static final int ENCH_NAME_COLOR = 0xFFFFFFFF;
    private static final int FILTER_TEXT_COLOR = 0xFFFFFFFF;
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

    public EnchLibraryScreen(@Nonnull EnchLibraryMenu menu, @Nonnull Inventory inv, @Nonnull Component title) {
        super(menu, inv, title, 176, 230);
        this.inventoryLabelY = this.imageHeight - 94;
        menu.setNotifier(this::containerChanged);
    }

    private static boolean hasShiftDownA() {
        com.mojang.blaze3d.platform.Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) ||
                com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean hasControlDownA() {
        com.mojang.blaze3d.platform.Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                ||
                com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                        org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
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

    private void renderButtonHighlight(GuiGraphicsExtractor gfx, int x, int y, int width, int height) {
        org.joml.Matrix3x2fStack pose = gfx.pose();
        pose.pushMatrix();
        gfx.fill(x, y, x + width, y + height, 0x80FFFFFF);
        pose.popMatrix();
    }

    // Suppressed: vanilla EditBox, font, Component factories lack @Nonnull
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

    // Track tile state to detect server→client updates delivered via
    // handleUpdateTag
    private int lastKnownPointCount = -1;

    @Override
    protected void containerTick() {
        super.containerTick();
        // Refresh the enchantment list whenever the tile's data changes on the client.
        // handleUpdateTag() on the BlockEntity updates the points map; we detect that
        // here.
        EnchLibraryBlockEntity tile = this.menu.getTile();
        int currentCount = tile != null ? tile.getPoints().size() : 0;
        // Also track sum of points to detect value changes, not just count changes
        long currentSum = tile != null ? tile.getPoints().values().longStream().sum() : 0L;
        int signature = currentCount * 31 + (int) (currentSum ^ (currentSum >>> 32));
        if (signature != this.lastKnownPointCount) {
            this.lastKnownPointCount = signature;
            this.containerChanged();
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (this.filter.isFocused() && event.key() != 256) {
            this.filter.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        this.extractTransparentBackground(gfx);
        // Draw the background texture HERE so slots and items render on top of it.
        gfx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0f, 0f,
                this.imageWidth, this.imageHeight, 307, 256);
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);

        this.handleCustomTooltips(gfx, mouseX, mouseY);
    }

    @Override
    // Suppressed: vanilla TEXTURE Identifier and GuiGraphics.blit lack
    // @Nonnull
    @SuppressWarnings("null")
    public void extractContents(@Nonnull net.minecraft.client.gui.GuiGraphicsExtractor gfx, int mouseX, int mouseY,
            float partialTick) {
        // CRITICAL: super call renders all slots and items (player inventory, hotbar,
        // etc.)
        super.extractContents(gfx, mouseX, mouseY, partialTick);

        // Button Highlights
        if (isOverReturnButton(mouseX, mouseY)) {
            renderButtonHighlight(gfx, this.leftPos + RETURN_BTN_X, this.topPos + RETURN_BTN_Y, RETURN_BTN_W,
                    RETURN_BTN_H);
        }
        if (isOverDisenchantButton(mouseX, mouseY)) {
            renderButtonHighlight(gfx, this.leftPos + DISENCHANT_BTN_X, this.topPos + DISENCHANT_BTN_Y,
                    DISENCHANT_BTN_W, DISENCHANT_BTN_H);
        }

        // Scrollbar
        int scrollbarPos = (int) (SCROLLBAR_RANGE * this.scrollOffs);
        boolean isScrollBarActive = this.data.size() > MAX_ENTRIES;
        gfx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos + SCROLLBAR_X_OFFSET,
                this.topPos + SCROLLBAR_TOP_OFFSET + scrollbarPos, 303f,
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

    // Suppressed: vanilla TEXTURE Identifier, Enchantment.getFullname, Font
    // and Component lack @Nonnull
    private void renderEntry(@Nonnull net.minecraft.client.gui.GuiGraphicsExtractor gfx, @Nonnull LibrarySlot slot,
            int x, int y, int mouseX,
            int mouseY) {
        LibrarySlot hovered = this.getHoveredSlot(mouseX, mouseY);

        // Background
        gfx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 194f,
                slot == hovered ? ENTRY_HEIGHT : 0f, ENTRY_WIDTH, ENTRY_HEIGHT, 307, 256);

        // Enchantment name (scaled to fit, cached in LibrarySlot)
        Component name = slot.displayName();
        int nameWidth = this.font.width(name);
        float scale = nameWidth > ENTRY_WIDTH - 4 ? (ENTRY_WIDTH - 4f) / nameWidth : 1.0F;

        org.joml.Matrix3x2fStack pose = gfx.pose();
        pose.pushMatrix();
        pose.translate(x + 2, y + 2);
        pose.scale(scale, scale);
        gfx.text(this.font, name, 0, 0, ENCH_NAME_COLOR, true);
        pose.popMatrix();

        // Points progress bar — texture strip at UV (197, 42) in library.png, up to 85
        // px wide
        long tierMaxPts = this.menu.getPointCap();
        int barPixels = tierMaxPts > 0L
                ? (int) Math.round((double) PROGRESS_BAR_WIDTH * slot.points / tierMaxPts)
                : 0;
        barPixels = Mth.clamp(barPixels, 0, PROGRESS_BAR_WIDTH);
        if (barPixels > 0) {
            gfx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE, x + 3, y + 14, 197f, 42f,
                    barPixels, 3, 307, 256);
        }
    }

    // Suppressed: vanilla handleCustomTooltips, Enchantment.getFullname, Component,
    // font
    // lack @Nonnull
    @SuppressWarnings("null")
    protected void handleCustomTooltips(net.minecraft.client.gui.GuiGraphicsExtractor gfx, int mouseX,
            int mouseY) {

        // Return button tooltip check
        if (isOverReturnButton(mouseX, mouseY)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("tooltip.enchlib.return_book"));

            if (hasShiftDownA()) {
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

            gfx.setTooltipForNextFrame(this.font, tooltip, Optional.empty(), mouseX, mouseY);
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

            if (hasShiftDownA()) {
                Component filterWord = Component.literal("FILTER").withStyle(ChatFormatting.GRAY);
                tooltip.add(Component.translatable("tooltip.enchlib.disenchant_desc", filterWord));
            } else {
                tooltip.add(Component.translatable("tooltip.enchlib.shift_info").withStyle(ChatFormatting.GRAY));
            }

            gfx.setTooltipForNextFrame(this.font, tooltip, Optional.empty(), mouseX, mouseY);
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
                    Identifier loc = key.identifier();
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

            boolean ctrl = hasControlDownA();
            boolean shift = hasShiftDownA();
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
                        MutableComponent removeLine = Component.literal("Refunding: ").withStyle(ChatFormatting.GOLD)
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

            gfx.setTooltipForNextFrame(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        this.scrolling = false;

        if (this.handleReturnButtonClick(mouseX, mouseY))
            return true;
        if (this.handleDisenchantButtonClick(mouseX, mouseY))
            return true;
        if (this.handleScrollbarClick(event))
            return true;
        if (this.handleEnchantmentSlotClick(mouseX, mouseY))
            return true;
        if (this.handleFilterRightClick(mouseX, mouseY, button))
            return true;

        return super.mouseClicked(event, handled);
    }

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

    private boolean handleScrollbarClick(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (mouseX >= this.leftPos + 14 && mouseX < this.leftPos + 18 && mouseY >= this.topPos + 29
                && mouseY < this.topPos + 132) {
            this.scrolling = this.data.size() > MAX_ENTRIES;
            this.mouseDragged(event, 0, 0);
            return true;
        }
        return false;
    }

    private boolean handleEnchantmentSlotClick(double mouseX, double mouseY) {
        LibrarySlot libSlot = this.getHoveredSlot((int) mouseX, (int) mouseY);
        if (libSlot != null) {
            if (this.minecraft != null && this.minecraft.level != null && this.minecraft.gameMode != null) {
                libSlot.ench.unwrapKey().ifPresent(key -> {
                    boolean shift = hasShiftDownA();
                    boolean ctrl = hasControlDownA();
                    this.minecraft.getConnection()
                            .send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                                    new dev.sotnah.enchantmentlibrary.network.EnchSelectionPayload(key.identifier(),
                                            shift, ctrl)));
                });
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
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        double mouseY = event.y();
        if (this.scrolling && this.data.size() > MAX_ENTRIES) {
            int barTop = this.topPos + SCROLLBAR_TOP_OFFSET;
            int barBot = barTop + SCROLLBAR_DRAG_HEIGHT;
            this.scrollOffs = ((float) mouseY - barTop - 6F) / (barBot - barTop - 12F);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
            this.startIndex = (int) (this.scrollOffs * (this.data.size() - MAX_ENTRIES) + 0.5D);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        this.scrolling = false;
        return super.mouseReleased(event);
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
    protected void extractLabels(net.minecraft.client.gui.GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        // suppress vanilla container title + "Inventory" label
    }

    private record LibrarySlot(Holder<Enchantment> ench, long points, int maxLevel, String sortKey,
            Component displayName) {
    }
}
