package dev.worldsfall.gap;

import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.util.WFUtil;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The pillars remember (proposal "The main island after the Sundering"): each obsidian pillar top carries an Ascendant
 * glyph visible only through the Void Lens. Read in order, the ten words give the phrase that unseals the Warden's Gallery,
 * and the reader receives the Pact of Depths glyph — so the hardest pact is discovered, not crafted.
 */
public final class PillarGlyphs {
    public static final int WORDS = 10;
    /** Pillar tops within this range of the player are shown in the lens. */
    public static final double RANGE = 220;

    private PillarGlyphs() {}

    public record Mark(BlockPos top, int index) {}

    public static List<Mark> marks(ServerLevel end) {
        List<Mark> out = new ArrayList<>();
        List<EndSpikeFeature.EndSpike> spikes = EndSpikeFeature.getSpikesForLevel(end);
        for (int i = 0; i < spikes.size() && i < WORDS; i++) {
            EndSpikeFeature.EndSpike s = spikes.get(i);
            out.add(new Mark(new BlockPos(s.getCenterX(), s.getHeight() + 1, s.getCenterZ()), i));
        }
        return out;
    }

    /** Called about once a second while a player looks through a Void Lens in the End. */
    public static void read(ServerLevel end, ServerPlayer player) {
        int next = player.getData(WFAttachments.PILLARS_READ);
        if (next >= WORDS) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        for (Mark m : marks(end)) {
            Vec3 to = Vec3.atCenterOf(m.top()).subtract(eye);
            double dist = to.length();
            if (dist > RANGE || to.normalize().dot(look) < Math.cos(Math.toRadians(2.5))) {
                continue;
            }
            if (m.index() != next) {
                player.sendOverlayMessage(Component.translatable("message.worldsfall.pillars.out_of_order",
                    Component.translatable("pillar.worldsfall.word." + m.index())));
                return;
            }
            player.setData(WFAttachments.PILLARS_READ, next + 1);
            player.sendOverlayMessage(Component.translatable("message.worldsfall.pillars.read", next + 1, WORDS,
                Component.translatable("pillar.worldsfall.word." + m.index())));
            WFUtil.playTo(player, WFSounds.ITEM_ECHO_TABLET_READ.get(), SoundSource.PLAYERS, 1.0f, 0.8f + next * 0.05f);
            if (next + 1 >= WORDS) {
                player.sendSystemMessage(Component.translatable("message.worldsfall.pillars.phrase"));
                WFUtil.give(player, new ItemStack(WFItems.PACT_GLYPH_DEPTHS.get()));
                Progress.award(player, "the_pillars_remember");
            }
            return;
        }
    }
}
