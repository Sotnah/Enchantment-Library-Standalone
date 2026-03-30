package dev.sotnah.enchantmentlibrary.network;

import dev.sotnah.enchantmentlibrary.EnchantmentLibraryMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload sent from client to server when an enchantment is selected in the Library.
 * Uses the stable Identifier instead of registry indices for maximum reliability.
 */
public record EnchSelectionPayload(Identifier enchantmentId, boolean shift, boolean ctrl) implements CustomPacketPayload {
    public static final Type<EnchSelectionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EnchantmentLibraryMod.MOD_ID, "enchantment_select"));

    public static final StreamCodec<FriendlyByteBuf, EnchSelectionPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, EnchSelectionPayload::enchantmentId,
            ByteBufCodecs.BOOL, EnchSelectionPayload::shift,
            ByteBufCodecs.BOOL, EnchSelectionPayload::ctrl,
            EnchSelectionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
