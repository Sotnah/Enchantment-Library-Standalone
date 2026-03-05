package dev.sotnah.enchantmentlibrary;

import javax.annotation.Nonnull;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@SuppressWarnings("null")
@Mod(EnchantmentLibraryMod.MOD_ID)
public class EnchantmentLibraryMod {

    public static final String MOD_ID = "enchantmentlibrary";

    public EnchantmentLibraryMod(@Nonnull IEventBus modBus) {
        ModRegistry.register(modBus);
    }
}
