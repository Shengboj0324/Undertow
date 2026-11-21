package dev.worldsfall.nav;

import dev.worldsfall.city.Cities;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.entity.Wyrm;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.restoration.Restoration;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * End Ships become Moorings (proposal "End Cities and End Ships"). A Drift Helm set on a ship's deck restores it: the
 * ship joins the Gateway Lattice, its dragon head starts to glow so it reads from a distance, and its Ship's Bell can call
 * the restorer's tamed Skates and Drakes to the deck. Nothing of the vanilla ship is changed.
 */
public final class Moorings {
    public static final String GLOW_TAG = "worldsfall_mooring_glow";
    public static final int BELL_RANGE = 256;

    private Moorings() {}

    static void onHelmPlaced(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        BoundingBox ship = Cities.shipAt(level, pos);
        if (ship == null) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.mooring.not_a_ship"));
            return;
        }
        LatticeData data = LatticeData.get(level.getServer());
        for (LatticeData.Node n : data.nodes()) {
            if (n.kind() == LatticeData.MOORING && ship.inflatedBy(2).isInside(n.pos())) {
                player.sendOverlayMessage(Component.translatable("message.worldsfall.mooring.already", n.name()));
                return;
            }
        }
        if (!Lattice.addMooring(level, pos, player)) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.lattice.full", data.size()));
            return;
        }
        level.setBlock(pos, state.setValue(DriftHelmBlock.MOORED, true), Block.UPDATE_ALL);
        glowHeads(level, ship);
        level.playSound(null, pos, WFSounds.BLOCK_SHIPS_BELL_RING.get(), SoundSource.BLOCKS, 2.0f, 0.8f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 50, 1.5, 1, 1.5, 0.04);
        player.sendSystemMessage(Component.translatable("message.worldsfall.mooring.restored", data.at(pos).name()));
        Progress.award(player, "moored");
        Restoration.award(level.getServer(), player, Restoration.Act.MOORING, "ship" + ship.minX() + "," + ship.minZ());
    }

    static void onHelmRemoved(ServerLevel level, BlockPos pos) {
        LatticeData.get(level.getServer()).remove(pos);
        for (var e : level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos).inflate(48), e -> e.entityTags().contains(GLOW_TAG))) {
            e.discard();
        }
    }

    /** A glowing, fully lit copy of each dragon head on the ship (a block display), so restored ships stand out. */
    static void glowHeads(ServerLevel level, BoundingBox ship) {
        for (BlockPos p : BlockPos.betweenClosed(ship.minX(), ship.minY(), ship.minZ(), ship.maxX(), ship.maxY(), ship.maxZ())) {
            BlockState s = level.getBlockState(p);
            if (!s.is(Blocks.DRAGON_WALL_HEAD) && !s.is(Blocks.DRAGON_HEAD)) {
                continue;
            }
            String props = s.is(Blocks.DRAGON_WALL_HEAD)
                ? "{facing:\"" + s.getValue(BlockStateProperties.HORIZONTAL_FACING).getSerializedName() + "\"}"
                : "{rotation:\"" + s.getValue(BlockStateProperties.ROTATION_16) + "\"}";
            String name = s.is(Blocks.DRAGON_WALL_HEAD) ? "minecraft:dragon_wall_head" : "minecraft:dragon_head";
            String cmd = String.format(Locale.ROOT,
                "summon minecraft:block_display %d %d %d {block_state:{Name:\"%s\",Properties:%s},Glowing:1b,glow_color_override:13404415,"
                    + "brightness:{sky:15,block:15},view_range:4f,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                    + "translation:[-0.01f,-0.01f,-0.01f],scale:[1.02f,1.02f,1.02f]},Tags:[\"%s\"]}",
                p.getX(), p.getY(), p.getZ(), name, props, GLOW_TAG);
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
        }
    }

    /** A Ship's Bell rung within 24 blocks of a Mooring calls the ringer's tamed Skates and Drakes from up to 256 blocks. */
    static void ringBell(ServerLevel level, BlockPos bell, ServerPlayer player) {
        LatticeData data = LatticeData.get(level.getServer());
        boolean moored = data.nodes().stream().anyMatch(n -> n.kind() == LatticeData.MOORING && n.pos().distSqr(bell) < 24 * 24);
        if (!moored) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.mooring.bell_unmoored"));
            return;
        }
        int called = 0;
        AABB range = new AABB(bell).inflate(BELL_RANGE);
        for (TamableAnimal pet : level.getEntitiesOfClass(TamableAnimal.class, range, t -> t.isTame() && t.isOwnedBy(player)
            && (t instanceof VoidSkate || t instanceof Wyrm w && w.stage() == Wyrm.Stage.DRAKE) && !t.isPassenger() && !t.isVehicle())) {
            Vec3 spot = deckSpot(level, bell, called);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, pet.getX(), pet.getY() + 0.5, pet.getZ(), 30, 0.5, 0.5, 0.5, 0.05);
            pet.snapTo(spot.x, spot.y, spot.z, pet.getYRot(), 0);
            pet.getNavigation().stop();
            pet.setOrderedToSit(false);
            level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 0.5, spot.z, 20, 0.5, 0.5, 0.5, 0.03);
            called++;
        }
        player.sendOverlayMessage(Component.translatable(called > 0 ? "message.worldsfall.mooring.bell_called" : "message.worldsfall.mooring.bell_nobody",
            called));
    }

    private static Vec3 deckSpot(ServerLevel level, BlockPos bell, int i) {
        double a = i * 1.9;
        for (int r = 2; r <= 6; r++) {
            BlockPos p = BlockPos.containing(bell.getX() + 0.5 + Math.cos(a) * r, bell.getY(), bell.getZ() + 0.5 + Math.sin(a) * r);
            for (int dy = 2; dy >= -3; dy--) {
                if (Lattice.standable(level, p.above(dy)) && level.getBlockState(p.above(dy + 2)).getCollisionShape(level, p.above(dy + 2)).isEmpty()) {
                    return Vec3.atBottomCenterOf(p.above(dy));
                }
            }
        }
        return Vec3.atBottomCenterOf(bell.above(2));
    }
}
