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
        
        // Register networking payload
        modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class, event -> {
            final net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar(EnchantmentLibraryMod.MOD_ID);
            registrar.playToServer(
                dev.sotnah.enchantmentlibrary.network.EnchSelectionPayload.TYPE,
                dev.sotnah.enchantmentlibrary.network.EnchSelectionPayload.STREAM_CODEC,
                dev.sotnah.enchantmentlibrary.network.EnchSelectionPayload::handleData
            );
        });

        // NeoForge 1.21.1+ prefers adding listeners directly
        modBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class, Config::onConfigLoad);
        modBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Reloading.class, Config::onConfigReload);
    }
}
