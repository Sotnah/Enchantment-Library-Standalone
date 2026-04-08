package dev.sotnah.enchantmentlibrary.network;

import dev.sotnah.enchantmentlibrary.EnchantmentLibraryMod;
import dev.sotnah.enchantmentlibrary.block.EnchLibraryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EnchSelectionPayload(ResourceLocation enchantmentId, boolean shift, boolean ctrl) implements CustomPacketPayload {
    // Suppressed: ResourceLocation.fromNamespaceAndPath lacks @Nonnull
    @SuppressWarnings("null")
    public static final CustomPacketPayload.Type<EnchSelectionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EnchantmentLibraryMod.MOD_ID, "ench_selection"));

    // Suppressed: ResourceLocation.STREAM_CODEC, ByteBufCodecs.BOOL, and the
    // StreamCodec.composite constructor/function types all lack @Nonnull
    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, EnchSelectionPayload> STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC, EnchSelectionPayload::enchantmentId,
        net.minecraft.network.codec.ByteBufCodecs.BOOL, EnchSelectionPayload::shift,
        net.minecraft.network.codec.ByteBufCodecs.BOOL, EnchSelectionPayload::ctrl,
        EnchSelectionPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Suppressed: IPayloadContext.player() lacks @Nonnull
    @SuppressWarnings("null")
    public static void handleData(EnchSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof EnchLibraryMenu menu) {
                if (menu.getTile() == null || !menu.stillValid(context.player())) return;
                menu.handleSelection(payload.enchantmentId(), payload.shift(), payload.ctrl());
            }
        });
    }
}
