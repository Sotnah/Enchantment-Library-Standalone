package dev.sotnah.enchantmentlibrary;

import java.util.function.Supplier;

import javax.annotation.Nonnull;

import dev.sotnah.enchantmentlibrary.block.EnchLibraryBlock;
import dev.sotnah.enchantmentlibrary.block.EnchLibraryBlockEntity;
import dev.sotnah.enchantmentlibrary.block.EnchLibraryMenu;
import dev.sotnah.enchantmentlibrary.component.LibraryData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {

        // ── Registers ──────────────────────────────────────────────────────────────

        public static final DeferredRegister.Blocks BLOCKS = DeferredRegister
                        .createBlocks(EnchantmentLibraryMod.MOD_ID);
        public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EnchantmentLibraryMod.MOD_ID);
        @SuppressWarnings("null")
        public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
                        .create(Registries.BLOCK_ENTITY_TYPE, EnchantmentLibraryMod.MOD_ID);
        @SuppressWarnings("null")
        public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU,
                        EnchantmentLibraryMod.MOD_ID);
        @SuppressWarnings("null")
        public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
                        Registries.CREATIVE_MODE_TAB,
                        EnchantmentLibraryMod.MOD_ID);
        @SuppressWarnings("null")
        public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister
                        .createDataComponents(Registries.DATA_COMPONENT_TYPE, EnchantmentLibraryMod.MOD_ID);
        @SuppressWarnings("null")
        public static final DeferredRegister<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> LOOT_CONDITIONS = DeferredRegister
                        .create(Registries.LOOT_CONDITION_TYPE, EnchantmentLibraryMod.MOD_ID);

        // ── Custom Loot Conditions ─────────────────────────────────────────────────

        @SuppressWarnings("null")
        public static final DeferredHolder<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType, net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> PRESERVE_LIBRARY_DATA = LOOT_CONDITIONS
                        .register("preserve_library_data",
                                        () -> new net.minecraft.world.level.storage.loot.predicates.LootItemConditionType(
                                                        dev.sotnah.enchantmentlibrary.loot.PreserveLibraryDataCondition.CODEC));

        // ── Data Components ────────────────────────────────────────────────────────

        @SuppressWarnings("null")
        public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<LibraryData>> LIBRARY_DATA = DATA_COMPONENTS
                        .registerComponentType("library_data",
                                        builder -> builder.persistent(LibraryData.CODEC)
                                                        .networkSynchronized(LibraryData.STREAM_CODEC));

        // ── Blocks ─────────────────────────────────────────────────────────────────

        public static final DeferredHolder<Block, EnchLibraryBlock> LIBRARY_TIER1 = BLOCKS.register("library_tier1",
                        () -> blockTier1());

        public static final DeferredHolder<Block, EnchLibraryBlock> LIBRARY_TIER2 = BLOCKS.register("library_tier2",
                        () -> blockTier2());

        public static final DeferredHolder<Block, EnchLibraryBlock> LIBRARY_TIER3 = BLOCKS.register("library_tier3",
                        () -> blockTier3());

        // ── Block Entities ─────────────────────────────────────────────────────────
        // Helper methods are used so that forward references to LIBRARY_TIERx fields
        // are resolved via method bodies instead of field initializers (JLS §8.3.3).

        public static final Supplier<BlockEntityType<EnchLibraryBlockEntity.Tier1Tile>> BLOCK_ENTITY_TIER1 = BLOCK_ENTITIES
                        .register("library_tier1", () -> beTier1());

        public static final Supplier<BlockEntityType<EnchLibraryBlockEntity.Tier2Tile>> BLOCK_ENTITY_TIER2 = BLOCK_ENTITIES
                        .register("library_tier2", () -> beTier2());

        public static final Supplier<BlockEntityType<EnchLibraryBlockEntity.Tier3Tile>> BLOCK_ENTITY_TIER3 = BLOCK_ENTITIES
                        .register("library_tier3", () -> beTier3());

        // ── Items ──────────────────────────────────────────────────────────────────

        @SuppressWarnings("null")
        public static final DeferredHolder<Item, BlockItem> ITEM_TIER1 = ITEMS.register("library_tier1",
                        () -> new BlockItem(LIBRARY_TIER1.get(), new Item.Properties()));

        @SuppressWarnings("null")
        public static final DeferredHolder<Item, BlockItem> ITEM_TIER2 = ITEMS.register("library_tier2",
                        () -> new BlockItem(LIBRARY_TIER2.get(), new Item.Properties()));

        @SuppressWarnings("null")
        public static final DeferredHolder<Item, BlockItem> ITEM_TIER3 = ITEMS.register("library_tier3",
                        () -> new BlockItem(LIBRARY_TIER3.get(), new Item.Properties()));

        // ── Menu ───────────────────────────────────────────────────────────────────

        public static final DeferredHolder<MenuType<?>, MenuType<EnchLibraryMenu>> LIBRARY_MENU = MENUS.register(
                        "library",
                        () -> IMenuTypeExtension.create(EnchLibraryMenu::new));

        // ── Creative Tab ───────────────────────────────────────────────────────────

        @SuppressWarnings("null")
        public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("enchantmentlibrary",
                        () -> CreativeModeTab.builder()
                                        .title(Component.translatable("itemGroup.enchantmentlibrary"))
                                        .icon(() -> ITEM_TIER1.get().getDefaultInstance())
                                        .displayItems((params, output) -> {
                                                output.accept(ITEM_TIER1.get());
                                                output.accept(ITEM_TIER2.get());
                                                output.accept(ITEM_TIER3.get());
                                        })
                                        .build());

        // ── Registration ───────────────────────────────────────────────────────────

        public static void register(@Nonnull IEventBus bus) {
                DATA_COMPONENTS.register(bus);
                LOOT_CONDITIONS.register(bus);
                BLOCKS.register(bus);
                ITEMS.register(bus);
                BLOCK_ENTITIES.register(bus);
                MENUS.register(bus);
                TABS.register(bus);
                bus.addListener(ModRegistry::registerCapabilities);
        }

        // Suppressed: vanilla Capabilities.ItemHandler.BLOCK and DeferredHolder.get()
        // lack @Nonnull
        @SuppressWarnings("null")
        private static void registerCapabilities(RegisterCapabilitiesEvent event) {
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY_TIER1.get(),
                                (be, side) -> be.getItemHandler());
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY_TIER2.get(),
                                (be, side) -> be.getItemHandler());
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY_TIER3.get(),
                                (be, side) -> be.getItemHandler());
        }

        // ── Helpers (avoid forward-reference in field initializers, JLS §8.3.3) ──

        private static EnchLibraryBlock blockTier1() {
                return new EnchLibraryBlock(() -> BLOCK_ENTITY_TIER1.get(),
                                EnchLibraryBlockEntity.Tier.TIER1.defaultMaxLevel);
        }

        private static EnchLibraryBlock blockTier2() {
                return new EnchLibraryBlock(() -> BLOCK_ENTITY_TIER2.get(),
                                EnchLibraryBlockEntity.Tier.TIER2.defaultMaxLevel);
        }

        private static EnchLibraryBlock blockTier3() {
                return new EnchLibraryBlock(() -> BLOCK_ENTITY_TIER3.get(),
                                EnchLibraryBlockEntity.Tier.TIER3.defaultMaxLevel);
        }

        // Suppressed: DataFixer null is intentional — standard NeoForge BlockEntityType
        // registration pattern
        @SuppressWarnings("null")
        private static BlockEntityType<EnchLibraryBlockEntity.Tier1Tile> beTier1() {
                return BlockEntityType.Builder
                                .of(EnchLibraryBlockEntity.Tier1Tile::new, LIBRARY_TIER1.get()).build(null);
        }

        // Suppressed: DataFixer null is intentional — standard NeoForge BlockEntityType
        // registration pattern
        @SuppressWarnings("null")
        private static BlockEntityType<EnchLibraryBlockEntity.Tier2Tile> beTier2() {
                return BlockEntityType.Builder
                                .of(EnchLibraryBlockEntity.Tier2Tile::new, LIBRARY_TIER2.get()).build(null);
        }

        // Suppressed: DataFixer null is intentional — standard NeoForge BlockEntityType
        // registration pattern
        @SuppressWarnings("null")
        private static BlockEntityType<EnchLibraryBlockEntity.Tier3Tile> beTier3() {
                return BlockEntityType.Builder
                                .of(EnchLibraryBlockEntity.Tier3Tile::new, LIBRARY_TIER3.get()).build(null);
        }

        private ModRegistry() {
        }
}
