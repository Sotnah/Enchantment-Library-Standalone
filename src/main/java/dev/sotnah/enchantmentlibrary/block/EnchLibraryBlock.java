package dev.sotnah.enchantmentlibrary.block;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import dev.sotnah.enchantmentlibrary.ModRegistry;
import dev.sotnah.enchantmentlibrary.component.LibraryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class EnchLibraryBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final Component NAME = Component.translatable("container.enchantmentlibrary.library");

    private final Supplier<? extends BlockEntityType<? extends EnchLibraryBlockEntity>> tileType;
    private final int maxLevel;

    // Suppressed: vanilla BlockBehaviour.Properties, BlockState, DirectionProperty
    // lack @Nonnull
    @SuppressWarnings("null")
    public EnchLibraryBlock(@Nonnull Supplier<? extends BlockEntityType<? extends EnchLibraryBlockEntity>> tileType,
            int maxLevel) {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(maxLevel >= EnchLibraryBlockEntity.Tier.TIER3.maxLevel ? 5.0F
                        : (maxLevel >= EnchLibraryBlockEntity.Tier.TIER2.maxLevel ? 4.0F : 3.0F), 1200.0F)
                .requiresCorrectToolForDrops());
        this.tileType = tileType;
        this.maxLevel = maxLevel;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public static final MapCodec<EnchLibraryBlock> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder
            .mapCodec(inst -> inst.group(
                    com.mojang.serialization.Codec.INT.fieldOf("max_level").forGetter(EnchLibraryBlock::getMaxLevel))
                    .apply(inst, maxLevel -> {
                        Supplier<? extends BlockEntityType<? extends EnchLibraryBlockEntity>> tileType = () -> {
                            if (maxLevel >= EnchLibraryBlockEntity.Tier.TIER3.maxLevel)
                                return ModRegistry.BLOCK_ENTITY_TIER3.get();
                            if (maxLevel >= EnchLibraryBlockEntity.Tier.TIER2.maxLevel)
                                return ModRegistry.BLOCK_ENTITY_TIER2.get();
                            return ModRegistry.BLOCK_ENTITY_TIER1.get();
                        };
                        return new EnchLibraryBlock(tileType, maxLevel);
                    }));

    @Override
    @Nonnull
    @SuppressWarnings("null")
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nonnull
    // Suppressed: vanilla ctx.getHorizontalDirection() lacks @Nonnull
    @SuppressWarnings("null")
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    @Nonnull
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        // BlockEntityType.create() is unannotated in vanilla; requireNonNull enforces
        // @Nonnull contract
        BlockEntityType<? extends EnchLibraryBlockEntity> type = java.util.Objects.requireNonNull(this.tileType.get(),
                "BlockEntityType must not be null");
        BlockEntity be = type.create(pos, state);
        return java.util.Objects.requireNonNull(be, "BlockEntity must not be null");
    }

    // ── GUI ────────────────────────────────────────────────────────────────────

    @Override
    @Nonnull
    // Suppressed: vanilla InteractionResult.sidedSuccess() lacks @Nonnull
    @SuppressWarnings("null")
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
            @Nonnull Player player, @Nonnull BlockHitResult hit) {
        if (player.isSpectator()) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) {
                player.openMenu(provider, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @Nullable
    // Suppressed: vanilla SimpleMenuProvider and NAME Component.translatable lack
    // @Nonnull
    @SuppressWarnings("null")
    protected MenuProvider getMenuProvider(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof EnchLibraryBlockEntity) {
            return new SimpleMenuProvider(
                    (containerId, inv, player) -> player.isSpectator() ? null
                            : new EnchLibraryMenu(containerId, inv, pos),
                    NAME);
        }
        return null;
    }

    // ── Data Component Persistence ─────────────────────────────────────────────

    @Override
    @Nonnull
    // Suppressed: vanilla LootContextParams, ItemStack, DataComponentType accessors
    // lack @Nonnull
    @SuppressWarnings("null")
    public List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder ctx) {
        ItemStack stack = new ItemStack(this);
        BlockEntity be = ctx.getParameter(LootContextParams.BLOCK_ENTITY);
        saveDataToItem(stack, be);
        return List.of(stack);
    }

    @Override
    @Nonnull
    public ItemStack getCloneItemStack(@Nonnull BlockState state, @Nonnull HitResult target, @Nonnull LevelReader level,
            @Nonnull BlockPos pos, @Nonnull Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return new ItemStack(this);
        }

        ItemStack stack = new ItemStack(this);
        saveDataToItem(stack, be);
        return stack;
    }

    @Override
    // Suppressed: vanilla Registries constants and RegistryLookup methods lack
    // @Nonnull
    @SuppressWarnings("null")
    public void setPlacedBy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state,
            @Nullable LivingEntity placer, @Nonnull ItemStack stack) {
        LibraryData data = stack.get(ModRegistry.LIBRARY_DATA.get());
        BlockEntity be = level.getBlockEntity(pos);
        if (data != null && be instanceof EnchLibraryBlockEntity lib) {
            lib.loadLibraryData(data,
                    level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT));
        }
    }

    private void saveDataToItem(ItemStack stack, @Nullable BlockEntity be) {
        if (be instanceof EnchLibraryBlockEntity lib) {
            LibraryData data = lib.toLibraryData();
            if (data.storedEnchantmentCount() > 0) {
                stack.set(java.util.Objects.requireNonNull(ModRegistry.LIBRARY_DATA.get()), data);
            }
        }
    }

    // ── Tooltip ────────────────────────────────────────────────────────────────

    @Override
    // Suppressed: vanilla String.valueOf and DataComponentType from
    // LIBRARY_DATA.get() lack @Nonnull
    @SuppressWarnings("null")
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull Item.TooltipContext context,
            @Nonnull List<Component> list, @Nonnull TooltipFlag flag) {
        list.add(Component.translatable("tooltip.enchlib.capacity",
                Component.literal(String.valueOf(this.maxLevel))).withStyle(ChatFormatting.GOLD));

        LibraryData data = stack.get(ModRegistry.LIBRARY_DATA.get());
        if (data != null) {
            int count = data.storedEnchantmentCount();
            if (count > 0) {
                list.add(Component.translatable("tooltip.enchlib.item", count).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // ── Block Entity Removal ───────────────────────────────────────────────────

    @Override
    // Suppressed: vanilla newState.getBlock() lacks @Nonnull
    @SuppressWarnings("null")
    protected void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
            @Nonnull BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            level.removeBlockEntity(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public int getMaxLevel() {
        return this.maxLevel;
    }
}
