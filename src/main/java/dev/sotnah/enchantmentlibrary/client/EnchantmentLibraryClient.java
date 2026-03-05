package dev.sotnah.enchantmentlibrary.client;

import javax.annotation.Nonnull;

import dev.sotnah.enchantmentlibrary.EnchantmentLibraryMod;
import dev.sotnah.enchantmentlibrary.ModRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@SuppressWarnings("null")
@Mod(value = EnchantmentLibraryMod.MOD_ID, dist = Dist.CLIENT)
public class EnchantmentLibraryClient {

    public EnchantmentLibraryClient(@Nonnull IEventBus modBus) {
        modBus.addListener(this::onRegisterMenuScreens);
    }

    private void onRegisterMenuScreens(@Nonnull RegisterMenuScreensEvent event) {
        event.register(ModRegistry.LIBRARY_MENU.get(), EnchLibraryScreen::new);
    }
}
