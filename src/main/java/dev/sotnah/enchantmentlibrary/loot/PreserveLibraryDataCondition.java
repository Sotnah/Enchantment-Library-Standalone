package dev.sotnah.enchantmentlibrary.loot;

import com.mojang.serialization.MapCodec;

import dev.sotnah.enchantmentlibrary.Config;
import dev.sotnah.enchantmentlibrary.ModRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
public class PreserveLibraryDataCondition implements LootItemCondition {
    public static final PreserveLibraryDataCondition INSTANCE = new PreserveLibraryDataCondition();
    public static final MapCodec<PreserveLibraryDataCondition> CODEC = MapCodec.unit(INSTANCE);

    private PreserveLibraryDataCondition() {}

    @Override
    public MapCodec<? extends LootItemCondition> codec() {
        return ModRegistry.PRESERVE_LIBRARY_DATA.get();
    }

    @Override
    @SuppressWarnings("null")
    public boolean test(LootContext context) {
        if (Config.keepInventory.get()) {
            return true;
        }
        if (!Config.requireSilkTouchForKeepInventory.get()) {
            return false;
        }
        var param = context.getOptionalParameter(LootContextParams.TOOL);
        ItemStack tool = param instanceof ItemStack ? (ItemStack) param : ItemStack.EMPTY;
        if (tool != null && !tool.isEmpty()) {
            var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(tool);
            for (var entry : enchantments.entrySet()) {
                if (entry.getIntValue() > 0 && entry.getKey().unwrapKey()
                        .map(key -> key.identifier().toString()).orElse("").equals("minecraft:silk_touch")) {
                    return true;
                }
            }
        }
        return false;
    }
}
