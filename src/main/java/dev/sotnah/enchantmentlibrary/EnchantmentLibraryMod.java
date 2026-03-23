package dev.sotnah.enchantmentlibrary;

import javax.annotation.Nonnull;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

@Mod(EnchantmentLibraryMod.MOD_ID)
public class EnchantmentLibraryMod {

    public static final String MOD_ID = "enchantmentlibrary";

    public EnchantmentLibraryMod(@Nonnull IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        // NeoForge 1.21.1+ prefers adding listeners directly rather than using @EventBusSubscriber for better performance
        modBus.addListener(Config::onConfigLoad);
        modBus.addListener(Config::onConfigReload);
    }
}
