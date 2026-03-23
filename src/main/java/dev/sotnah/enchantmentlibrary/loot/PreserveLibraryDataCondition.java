package dev.sotnah.enchantmentlibrary.loot;

import com.mojang.serialization.MapCodec;

import dev.sotnah.enchantmentlibrary.Config;
import dev.sotnah.enchantmentlibrary.ModRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

public class PreserveLibraryDataCondition implements LootItemCondition {
    public static final PreserveLibraryDataCondition INSTANCE = new PreserveLibraryDataCondition();
    public static final MapCodec<PreserveLibraryDataCondition> CODEC = MapCodec.unit(INSTANCE);

    private PreserveLibraryDataCondition() {}

    @Override
    public LootItemConditionType getType() {
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
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool != null && !tool.isEmpty()) {
            var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(tool);
            for (var entry : enchantments.entrySet()) {
                if (entry.getIntValue() > 0 && entry.getKey().unwrapKey()
                        .map(key -> key.location().toString()).orElse("").equals("minecraft:silk_touch")) {
                    return true;
                }
            }
        }
        return false;
    }
}
