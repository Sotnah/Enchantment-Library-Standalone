package dev.sotnah.enchantmentlibrary.component;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Immutable data container for enchantment library state, stored as a
 * DataComponent on the item form of the block.
 * <p>
 * Uses FastUtil primitive maps to eliminate autoboxing during synchronization.
 */
public record LibraryData(
                @Nonnull Object2LongMap<Identifier> points,
                @Nonnull Object2IntMap<Identifier> maxLevels) {

        // Suppressed: RecordCodecBuilder and Codec.unboundedMap types lack @Nonnull
        @SuppressWarnings("null")
        public static final Codec<LibraryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.unboundedMap(Identifier.CODEC, Codec.LONG).fieldOf("points")
                                        .forGetter(d -> d.points),
                        Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("max_levels")
                                        .forGetter(d -> d.maxLevels))
                        .apply(instance, (pts, lvls) -> new LibraryData(new Object2LongOpenHashMap<>(pts),
                                        new Object2IntOpenHashMap<>(lvls))));

        @SuppressWarnings("null")
        private static final StreamCodec<ByteBuf, Object2LongMap<Identifier>> POINTS_STREAM_CODEC = StreamCodec.<ByteBuf, Object2LongMap<Identifier>>of(
                (ByteBuf buf, Object2LongMap<Identifier> map) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, map.size());
                        for (var entry : map.object2LongEntrySet()) {
                                Identifier.STREAM_CODEC.encode(buf, entry.getKey());
                                ByteBufCodecs.VAR_LONG.encode(buf, entry.getLongValue());
                        }
                },
                (ByteBuf buf) -> {
                        int size = ByteBufCodecs.VAR_INT.decode(buf);
                        Object2LongMap<Identifier> map = new Object2LongOpenHashMap<>(size);
                        for (int i = 0; i < size; i++) {
                                map.put(Identifier.STREAM_CODEC.decode(buf),
                                                ByteBufCodecs.VAR_LONG.decode(buf).longValue());
                        }
                        return map;
                }
        );

        @SuppressWarnings("null")
        private static final StreamCodec<ByteBuf, Object2IntMap<Identifier>> LEVELS_STREAM_CODEC = StreamCodec.<ByteBuf, Object2IntMap<Identifier>>of(
                (ByteBuf buf, Object2IntMap<Identifier> map) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, map.size());
                        for (var entry : map.object2IntEntrySet()) {
                                Identifier.STREAM_CODEC.encode(buf, entry.getKey());
                                ByteBufCodecs.VAR_INT.encode(buf, entry.getIntValue());
                        }
                },
                (ByteBuf buf) -> {
                        int size = ByteBufCodecs.VAR_INT.decode(buf);
                        Object2IntMap<Identifier> map = new Object2IntOpenHashMap<>(size);
                        for (int i = 0; i < size; i++) {
                                map.put(Identifier.STREAM_CODEC.decode(buf),
                                                ByteBufCodecs.VAR_INT.decode(buf).intValue());
                        }
                        return map;
                }
        );

        // Suppressed: StreamCodec.composite() parameters or return type lack @Nonnull
        @SuppressWarnings("null")
        public static final StreamCodec<ByteBuf, LibraryData> STREAM_CODEC = StreamCodec.composite(
                        POINTS_STREAM_CODEC, LibraryData::points,
                        LEVELS_STREAM_CODEC, LibraryData::maxLevels,
                        LibraryData::new);

        /**
         * @return the number of distinct enchantments with at least 1 stored point.
         */
        public int storedEnchantmentCount() {
                int count = 0;
                for (long v : this.points.values()) {
                        if (v > 0L)
                                count++;
                }
                return count;
        }
}
