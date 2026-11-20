package dev.worldsfall.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** The single elytra upgrade an elytra may hold (§9.7, D8). */
public enum ElytraUpgrade implements StringRepresentable {
    GALE, STILL, HOVER;

    public static final Codec<ElytraUpgrade> CODEC = StringRepresentable.fromEnum(ElytraUpgrade::values);
    public static final StreamCodec<ByteBuf, ElytraUpgrade> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
