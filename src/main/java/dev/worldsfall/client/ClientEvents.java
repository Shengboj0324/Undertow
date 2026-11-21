package dev.worldsfall.client;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.client.hud.WFHud;
import dev.worldsfall.config.WFConfig;
import dev.worldsfall.entity.TheUnmade;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.fluid.StillwaterEffects;
import dev.worldsfall.item.ElytraUpgrade;
import dev.worldsfall.item.GatewayCompassItem;
import dev.worldsfall.item.ShulkerSatchelItem;
import dev.worldsfall.network.ElytraActionPayload;
import dev.worldsfall.network.SkateDismountPayload;
import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.fracture.FractureArena;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.world.level.block.state.BlockState;
import dev.worldsfall.registry.WFBlocks;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;

/**
 * Client game-bus logic: local-player physics that must run where movement is authoritative (Fracture pull and
 * updrafts, Gravity Well float), Skate double-tap dismount, elytra upgrade inputs, tooltips (Shift shows
 * mechanics, §17.3), Stillwater sound muffling (§7.2), and boss music layers (§19.4).
 */
public final class ClientEvents {
    private static boolean lastSneak, lastJump;
    private static int sneakTapTimer, jumpTapTimer;
    private static int galeCooldown, hoverTicks, stillTicks;
    private static long warnedNothingBelow = -1;

    private ClientEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientWorldState.get().tick(mc);
        if (p == null || mc.level == null || mc.isPaused()) {
            return;
        }
        Input in = p.input.keyPresses;
        skateControls(mc, p, in);
        elytraUpgrades(p, in);
        fracturePhysics(mc, p);
        ambient(mc, p);
        lastSneak = in.shift();
        lastJump = in.jump();
    }

    private static void skateControls(Minecraft mc, LocalPlayer p, Input in) {
        sneakTapTimer = Math.max(0, sneakTapTimer - 1);
        if (p.getVehicle() instanceof VoidSkate skate && in.shift() && !lastSneak) {
            if (sneakTapTimer > 0 && !skate.isPerched()) {
                ClientPacketDistributor.sendToServer(new SkateDismountPayload());
                if (warnedNothingBelow != mc.level.getGameTime() / 72000) {
                    warnedNothingBelow = mc.level.getGameTime() / 72000;
                    p.sendOverlayMessage(Component.translatable("message.worldsfall.skate.nothing_below"));
                }
                sneakTapTimer = 0;
            } else {
                sneakTapTimer = 8;
            }
        }
    }

    /** Gale: double-tap jump boost; Still: hold sneak to brake; Hover: hold sneak when nearly stopped (§9.7). */
    private static void elytraUpgrades(LocalPlayer p, Input in) {
        galeCooldown = Math.max(0, galeCooldown - 1);
        jumpTapTimer = Math.max(0, jumpTapTimer - 1);
        if (!p.isFallFlying()) {
            hoverTicks = Math.min(60, hoverTicks + 2) > 0 && p.onGround() ? 0 : hoverTicks;
            return;
        }
        ElytraUpgrade up = ElytraActionPayload.upgradeOf(p.getItemBySlot(EquipmentSlot.CHEST));
        if (up == null) {
            return;
        }
        Vec3 v = p.getDeltaMovement();
        switch (up) {
            case GALE, TEMPEST -> {
                if (in.jump() && !lastJump) {
                    if (jumpTapTimer > 0 && galeCooldown == 0) {
                        Vec3 look = p.getLookAngle();
                        boolean tempest = up == ElytraUpgrade.TEMPEST;
                        p.setDeltaMovement(v.add(look.scale(tempest ? 0.8 : 0.55)));
                        galeCooldown = tempest ? 100 : 160;
                        ClientPacketDistributor.sendToServer(new ElytraActionPayload(ElytraActionPayload.GALE));
                    }
                    jumpTapTimer = 7;
                }
            }
            case STILL -> {
                if (in.shift()) {
                    p.setDeltaMovement(v.x * 0.9, Math.max(v.y * 0.9, -0.06), v.z * 0.9);
                    if (++stillTicks % 20 == 0) {
                        ClientPacketDistributor.sendToServer(new ElytraActionPayload(ElytraActionPayload.STILL_SECOND));
                    }
                } else {
                    stillTicks = 0;
                }
            }
            case HOVER -> {
                if (in.shift() && v.length() < 0.25 && hoverTicks < 60) {
                    if (hoverTicks == 0) {
                        ClientPacketDistributor.sendToServer(new ElytraActionPayload(ElytraActionPayload.HOVER));
                    }
                    hoverTicks++;
                    p.setDeltaMovement(v.x * 0.6, 0.0, v.z * 0.6);
                } else if (!in.shift()) {
                    hoverTicks = Math.max(0, hoverTicks - 1);
                }
            }
        }
    }

    /** Fracture Phase 3 inward pull and Updraft Columns, applied where player movement is computed. */
    private static void fracturePhysics(Minecraft mc, LocalPlayer p) {
        if (mc.level.dimension() != WFDimensions.THE_FRACTURE) {
            return;
        }
        for (Vec3 col : FractureArena.updrafts()) {
            if (Math.hypot(p.getX() - col.x, p.getZ() - col.z) < 2.2 && p.getY() > col.y - 40 && p.getY() < col.y + 24
                && (p.isFallFlying() || p.getVehicle() instanceof VoidSkate || !p.onGround())) {
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, Math.max(v.y, 0) + 0.11, v.z);
            }
        }
        TheUnmade boss = mc.level.getEntitiesOfClass(TheUnmade.class, new AABB(FractureArena.CENTER).inflate(96)).stream().findFirst().orElse(null);
        if (boss != null && boss.phase() == TheUnmade.PHASE_UNMADE && boss.freezeTicks() == 0) {
            Vec3 to = boss.position().subtract(p.position());
            double d = to.length();
            double strength = (boss.phase3Ticks() > 4 * 1200 ? 0.03 : 0.015) * (1 + Math.max(0, 1 - d / 64));
            p.setDeltaMovement(p.getDeltaMovement().add(to.normalize().scale(strength)));
        }
    }

    private static void ambient(Minecraft mc, LocalPlayer p) {
        if (mc.level.dimension() == WFDimensions.THE_FRACTURE && mc.level.getGameTime() % 2 == 0) {
            mc.level.addParticle(WFParticles.ASH_INFLOW.get(), p.getX() + (p.getRandom().nextDouble() - 0.5) * 32, p.getY() + p.getRandom().nextDouble() * 12 - 4,
                p.getZ() + (p.getRandom().nextDouble() - 0.5) * 32, 0, 0, 0);
        }
        if (dev.worldsfall.world.gravity.GravityZones.hasModifier(p) && mc.level.getGameTime() % 3 == 0) {
            // the main low-gravity cue: motes drifting upward (§5.2)
            mc.level.addParticle(WFParticles.UP_MOTE.get(), p.getX() + (p.getRandom().nextDouble() - 0.5) * 10, p.getY() - 1 + p.getRandom().nextDouble() * 4,
                p.getZ() + (p.getRandom().nextDouble() - 0.5) * 10, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ tooltips (§9.1, §17.3, §18.9)
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        List<Component> tip = event.getToolTip();
        ElytraUpgrade up = stack.get(WFComponents.ELYTRA_UPGRADE.get());
        if (up != null) {
            tip.add(1, Component.translatable("tooltip.worldsfall.upgrade", Component.translatable("tooltip.worldsfall.upgrade." + up.getSerializedName()))
                .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (!Worldsfall.MODID.equals(id.getNamespace())) {
            return;
        }
        String kind = stack.getItem() instanceof net.minecraft.world.item.BlockItem && !stack.is(WFItems.GLOWBULB.get()) ? "block" : "item";
        String base = kind + "." + Worldsfall.MODID + "." + id.getPath();
        if (stack.getItem() instanceof ShulkerSatchelItem) {
            tip.add(Component.translatable("tooltip.worldsfall.satchel_size", ShulkerSatchelItem.size(stack)).withStyle(ChatFormatting.GRAY));
        }
        if (stack.getItem() instanceof GatewayCompassItem && GatewayCompassItem.isBound(stack) && Minecraft.getInstance().player != null) {
            LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
            if (tracker != null && tracker.target().isPresent()) {
                BlockPos target = tracker.target().get().pos();
                int dist = (int) Math.sqrt(Minecraft.getInstance().player.blockPosition().distSqr(target));
                tip.add(Component.translatable("tooltip.worldsfall.distance", dist).withStyle(ChatFormatting.GRAY));
            }
        }
        boolean hasDesc = net.minecraft.locale.Language.getInstance().has(base + ".desc");
        if (hasDesc) {
            if (Minecraft.getInstance().hasShiftDown()) {
                tip.add(Component.translatable(base + ".desc").withStyle(ChatFormatting.GRAY));
            } else {
                tip.add(Component.translatable("tooltip.worldsfall.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (net.minecraft.locale.Language.getInstance().has(base + ".flavor")) {
            tip.add(Component.translatable(base + ".flavor").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
    }

    // ------------------------------------------------------------------ muffling: Stillwater (§7.2, volume x0.4 near a submerged player)
    // and Stillglass (§6.2, sound passing through Stillglass is muffled by 50%)
    @SubscribeEvent
    public static void sound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null || mc.level == null) {
            return;
        }
        if (s.getSource() == SoundSource.MUSIC || s.getSource() == SoundSource.MASTER || s.getSource() == SoundSource.UI || s.isRelative()) {
            return;
        }
        Vec3 ear = mc.player.getEyePosition();
        double d = ear.distanceToSqr(s.getX(), s.getY(), s.getZ());
        float volume = 1.0f;
        float pitch = 1.0f;
        if (WFConfig.get(WFConfig.CLIENT.stillwaterSoundMuffle, true) && StillwaterEffects.isEyeIn(mc.player) && d < 16 * 16) {
            volume = 0.4f;
            pitch = 0.8f;
        }
        if (d > 1.0 && d < 32 * 32 && throughStillglass(mc, ear, new Vec3(s.getX(), s.getY(), s.getZ()))) {
            volume *= 0.5f;
            pitch *= 0.92f;
        }
        if (volume < 1.0f) {
            event.setSound(new SimpleSoundInstance(s.getIdentifier(), s.getSource(), volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                s.getAttenuation(), s.getX(), s.getY(), s.getZ(), false));
        }
    }

    /** True if the straight line from the listener to the sound passes through a Stillglass block or pane. */
    static boolean throughStillglass(Minecraft mc, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int n = (int) Math.ceil(step.length() * 2);
        step = step.scale(1.0 / n);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos last = null;
        Vec3 p = from;
        for (int i = 1; i < n; i++) {
            p = p.add(step);
            pos.set(p.x, p.y, p.z);
            if (pos.equals(last)) {
                continue;
            }
            last = pos.immutable();
            BlockState st = mc.level.getBlockState(pos);
            if (st.is(WFBlocks.STILLGLASS.get()) || st.is(WFBlocks.STILLGLASS_PANE.get())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ music: The Unmade's three layers (§19.4)
    @SubscribeEvent
    public static void music(SelectMusicEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != WFDimensions.THE_FRACTURE) {
            return;
        }
        TheUnmade boss = mc.level.getEntitiesOfClass(TheUnmade.class, mc.player.getBoundingBox().inflate(128)).stream().findFirst().orElse(null);
        if (boss == null || !boss.isActive()) {
            event.setMusic(null);
            return;
        }
        var layer = switch (boss.phase()) {
            case TheUnmade.PHASE_SHELL -> WFSounds.MUSIC_UNMADE_1;
            case TheUnmade.PHASE_UNBINDING -> WFSounds.MUSIC_UNMADE_2;
            case TheUnmade.PHASE_UNMADE -> WFSounds.MUSIC_UNMADE_3;
            default -> WFSounds.MUSIC_STILLNESS;
        };
        event.setMusic(new Music(layer, 0, 0, true));
    }
}
