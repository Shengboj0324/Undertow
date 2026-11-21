package dev.worldsfall.client.model;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Render states for the mod's creatures (extracted each frame from entities). */
public final class WFStates {
    private WFStates() {}

    public static class Skate extends LivingEntityRenderState {
        public boolean saddled, perched, dusk, ridden;
        public float bank, speed, climb;
    }

    public static class Grazer extends LivingEntityRenderState {
        public boolean sheared;
        public float graze;
    }

    public static class Lurker extends LivingEntityRenderState {
        public int state;
        public boolean hidden, lit;
        public float stateTicks;
        public float threadLength;
    }

    public static class Hoarder extends LivingEntityRenderState {
        public boolean angry;
        public float barter;
    }

    public static class Warden extends LivingEntityRenderState {
        public int attack, damageState;
        public float attackTicks;
    }

    public static class Echo extends LivingEntityRenderState {
        public boolean disguised;
        public BlockState disguise = Blocks.PURPUR_BLOCK.defaultBlockState();
        public float shimmer;
    }

    public static class Wyrm extends LivingEntityRenderState {
        public int stage;
        public int look;
        public boolean flying, sitting;
    }

    public static class Shardling extends LivingEntityRenderState {
        public boolean diving;
    }

    public static class Unmade extends LivingEntityRenderState {
        public int phase, rings, attack, freeze, hitFlash;
        public boolean exposed, active;
        public float pulsePeriod, attackTicks;
        public Vector3f teleA = new Vector3f(), teleB = new Vector3f();
    }

    public static class Tidewisp extends LivingEntityRenderState {
        public float pulse;
    }
}
