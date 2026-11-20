package dev.worldsfall.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Animated creature models (§18.5): weight tells the story — Skates glide with slow deep wingbeats, Grazers
 * rock, Wardens are heavy and deliberate, Lurkers are still then sudden, Echoes snap. Low-gravity zones are
 * handled by the renderer slowing ageInTicks.
 */
public final class CreatureModels {
    private CreatureModels() {}

    public static final class VoidSkate extends WFModel<WFStates.Skate> {
        private final ModelPart body, lw, lwt, rw, rwt, t1, t2, t3, lf, rf, harness;

        public VoidSkate(ModelPart root) {
            super(root, "void_skate");
            body = p("body"); lw = p("left_wing"); lwt = p("left_wing_tip"); rw = p("right_wing"); rwt = p("right_wing_tip");
            t1 = p("tail_1"); t2 = p("tail_2"); t3 = p("tail_3"); lf = p("left_fin"); rf = p("right_fin"); harness = p("harness");
        }

        @Override
        public void setupAnim(WFStates.Skate s) {
            super.setupAnim(s);
            float t = s.ageInTicks;
            harness.visible = s.saddled;
            if (s.perched) {
                // resting: wings droop over the perch edge, slow breathing ripple
                float breathe = Mth.sin(t * 0.05f) * 0.04f;
                lw.zRot += 0.32f + breathe;
                rw.zRot -= 0.32f + breathe;
                lwt.zRot += 0.25f;
                rwt.zRot -= 0.25f;
            } else {
                // slow, deep wingbeats while climbing; long glides with rippling tips otherwise
                float beatAmp = Mth.clamp(0.18f + s.climb * 0.5f + s.speed * 0.15f, 0.12f, 0.62f);
                float beat = Mth.sin(t * 0.11f) * beatAmp;
                float ripple = Mth.sin(t * 0.11f - 1.1f) * beatAmp * 0.9f;
                lw.zRot += -beat;
                rw.zRot += beat;
                lwt.zRot += -ripple;
                rwt.zRot += ripple;
            }
            body.zRot += s.bank;
            body.xRot += Mth.clamp(-s.climb * 0.35f, -0.35f, 0.35f);
            float sway = Mth.sin(t * 0.08f);
            t1.yRot += sway * 0.12f;
            t2.yRot += Mth.sin(t * 0.08f - 0.7f) * 0.2f;
            t3.yRot += Mth.sin(t * 0.08f - 1.4f) * 0.28f;
            t1.xRot += Mth.sin(t * 0.06f) * 0.05f;
            lf.xRot += Mth.sin(t * 0.07f) * 0.12f;
            rf.xRot += Mth.sin(t * 0.07f + 0.5f) * 0.12f;
        }
    }

    public static final class ChorusGrazer extends WFModel<WFStates.Grazer> {
        private final ModelPart body, n1, n2, n3, head, frill, tail;
        private final ModelPart[] legs;

        public ChorusGrazer(ModelPart root) {
            super(root, "chorus_grazer");
            body = p("body"); n1 = p("neck_1"); n2 = p("neck_2"); n3 = p("neck_3"); head = p("head"); frill = p("frill"); tail = p("tail");
            legs = new ModelPart[] {p("leg_front_left"), p("leg_mid_right"), p("leg_back_left"), p("leg_front_right"), p("leg_mid_left"), p("leg_back_right")};
        }

        @Override
        public void setupAnim(WFStates.Grazer s) {
            super.setupAnim(s);
            float pos = s.walkAnimationPos, spd = Math.min(1f, s.walkAnimationSpeed);
            // tripod gait: legs 0-2 and 3-5 alternate
            for (int i = 0; i < 6; i++) {
                float phase = i < 3 ? 0 : Mth.PI;
                legs[i].xRot += Mth.cos(pos * 0.85f + phase) * 0.9f * spd;
            }
            body.zRot += Mth.sin(pos * 0.85f) * 0.05f * spd;           // gentle rocking walk
            body.y += Mth.abs(Mth.sin(pos * 0.85f)) * -0.4f * spd;
            float graze = Mth.clamp(s.graze, 0f, 1f);
            float look = s.yRot * Mth.DEG_TO_RAD * 0.33f;
            n1.yRot += look;
            n2.yRot += look;
            n3.yRot += look;
            // long smooth curve down to graze
            n1.xRot += graze * 0.55f;
            n2.xRot += graze * 0.45f;
            n3.xRot += graze * 0.35f;
            head.xRot += s.xRot * Mth.DEG_TO_RAD * 0.5f + graze * 0.3f;
            n2.xRot += Mth.sin(s.ageInTicks * 0.04f) * 0.03f;
            frill.visible = true;
            frill.yScale = s.sheared ? 0.45f : 1.0f;
            tail.yRot += Mth.sin(s.ageInTicks * 0.1f) * 0.15f;
        }
    }

    public static final class Lurker extends WFModel<WFStates.Lurker> {
        private final ModelPart body, crown;
        private final ModelPart[] legs = new ModelPart[7], lowers = new ModelPart[7];

        public Lurker(ModelPart root) {
            super(root, "underside_lurker", RenderTypes::entityTranslucent);
            body = p("body"); crown = p("crown");
            for (int i = 0; i < 7; i++) {
                legs[i] = p("leg_" + i);
                lowers[i] = p("leg_" + i + "_lower");
            }
        }

        @Override
        public void setupAnim(WFStates.Lurker s) {
            super.setupAnim(s);
            float t = s.ageInTicks;
            // HIDDEN 0 / ALERT 1 / WINDUP 2 / RETREAT 3 — still, then sudden
            float open = switch (s.state) {
                case 1 -> 1.0f;
                case 2 -> 1.25f;
                default -> 0.15f;
            };
            for (int i = 0; i < 7; i++) {
                float a = i * Mth.TWO_PI / 7f;
                float twitch = s.state == 0 && ((int) (t / 37f + i) % 7 == 0) ? Mth.sin(t * 2.2f) * 0.06f : 0f;
                float windup = s.state == 2 ? Mth.sin(t * 3.1f + i) * 0.08f : 0f;
                legs[i].xRot += Mth.cos(a) * 0.55f * open + twitch;
                legs[i].zRot += Mth.sin(a) * 0.55f * open;
                lowers[i].xRot += (0.35f + 0.5f * (1 - open)) + windup;
            }
            body.yRot += Mth.sin(t * 0.02f) * 0.08f;
            crown.yRot += Mth.sin(t * 0.03f) * 0.05f;
        }
    }

    /** Hoarder, Echo: Enderman-proportioned humanoids. */
    public static class Humanoid<S extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState> extends WFModel<S> {
        protected final ModelPart head, bodyPart, rightArm, leftArm, rightLeg, leftLeg;

        public Humanoid(ModelPart root, String name) {
            super(root, name);
            head = p("head"); bodyPart = p("body"); rightArm = p("right_arm"); leftArm = p("left_arm"); rightLeg = p("right_leg"); leftLeg = p("left_leg");
        }

        public Humanoid(ModelPart root, String name, java.util.function.Function<net.minecraft.resources.Identifier, net.minecraft.client.renderer.rendertype.RenderType> rt) {
            super(root, name, rt);
            head = p("head"); bodyPart = p("body"); rightArm = p("right_arm"); leftArm = p("left_arm"); rightLeg = p("right_leg"); leftLeg = p("left_leg");
        }

        @Override
        public void setupAnim(S s) {
            super.setupAnim(s);
            float pos = s.walkAnimationPos, spd = Math.min(1f, s.walkAnimationSpeed);
            head.yRot += s.yRot * Mth.DEG_TO_RAD;
            head.xRot += s.xRot * Mth.DEG_TO_RAD;
            rightArm.xRot += Mth.cos(pos * 0.6662f + Mth.PI) * spd * 0.5f;
            leftArm.xRot += Mth.cos(pos * 0.6662f) * spd * 0.5f;
            rightLeg.xRot += Mth.cos(pos * 0.6662f) * spd * 0.7f;
            leftLeg.xRot += Mth.cos(pos * 0.6662f + Mth.PI) * spd * 0.7f;
            rightArm.zRot += 0.05f + Mth.sin(s.ageInTicks * 0.06f) * 0.03f;
            leftArm.zRot -= 0.05f + Mth.sin(s.ageInTicks * 0.06f) * 0.03f;
        }
    }

    public static final class Hoarder extends Humanoid<WFStates.Hoarder> {
        private final ModelPart pouch;

        public Hoarder(ModelPart root) {
            super(root, "hoarder");
            pouch = p("pouch");
        }

        @Override
        public void setupAnim(WFStates.Hoarder s) {
            super.setupAnim(s);
            bodyPart.xRot += 0.18f; // stooped
            head.xRot += 0.12f;
            if (s.barter > 0) {
                float k = Mth.clamp(s.barter / 30f, 0, 1);
                rightArm.xRot -= 1.1f * k;
                leftArm.xRot -= 0.9f * k;
                pouch.zRot += Mth.sin(s.ageInTicks * 0.8f) * 0.2f * k;
            }
            if (s.angry) {
                head.xRot -= 0.2f;
            }
        }
    }

    public static final class Echo extends Humanoid<WFStates.Echo> {
        public Echo(ModelPart root) {
            super(root, "echo", RenderTypes::entityTranslucent);
        }

        @Override
        public void setupAnim(WFStates.Echo s) {
            super.setupAnim(s);
            float jitter = s.shimmer > 0 ? 0.08f : 0.02f;
            head.zRot += Mth.sin(s.ageInTicks * 7.3f) * jitter;
            rightArm.zRot += Mth.sin(s.ageInTicks * 5.1f) * jitter;
            leftArm.zRot -= Mth.sin(s.ageInTicks * 6.7f) * jitter;
            if (s.shimmer > 0) {
                rightArm.xRot = -1.6f;
                leftArm.xRot = -1.6f;
            }
        }
    }

    public static final class SpireWarden extends Humanoid<WFStates.Warden> {
        private final ModelPart crest, rightForearm, leftForearm, plShR, plShL, plChest, plFaR, plFaL;

        public SpireWarden(ModelPart root) {
            super(root, "spire_warden");
            crest = p("crest"); rightForearm = p("right_forearm"); leftForearm = p("left_forearm");
            plShR = p("plate_shoulder_right"); plShL = p("plate_shoulder_left"); plChest = p("plate_chest");
            plFaR = p("plate_forearm_right"); plFaL = p("plate_forearm_left");
        }

        @Override
        public void setupAnim(WFStates.Warden s) {
            super.setupAnim(s);
            // heavy and deliberate: halve the limb swing
            rightLeg.xRot *= 0.6f;
            leftLeg.xRot *= 0.6f;
            // plates fall away with damage (3 states)
            plShL.visible = s.damageState < 2;
            plFaR.visible = s.damageState < 2;
            plChest.visible = s.damageState < 1;
            switch (s.attack) {
                case 1 -> { // SLAM wind-up: both arms raised, held still (stillness is the cue)
                    float k = Mth.clamp(s.attackTicks / 6f, 0, 1);
                    rightArm.xRot = -2.6f * k;
                    leftArm.xRot = -2.6f * k;
                    rightForearm.xRot = -0.4f * k;
                    leftForearm.xRot = -0.4f * k;
                }
                case 2 -> { // TELEPORT wind-up: crouch, arms spread
                    bodyPart.xRot += 0.3f;
                    rightArm.zRot += 0.9f;
                    leftArm.zRot -= 0.9f;
                }
                case 4 -> { // PLATE SHIELD: forearm plates raised before the face
                    rightArm.xRot = -1.4f;
                    leftArm.xRot = -1.4f;
                    rightArm.yRot = -0.5f;
                    leftArm.yRot = 0.5f;
                }
                default -> {
                    plShR.zRot += Mth.sin(s.ageInTicks * 0.05f) * 0.02f; // plate adjust idle
                }
            }
            crest.xRot += Mth.sin(s.ageInTicks * 0.03f) * 0.02f;
        }
    }

    public static final class Wyrm extends WFModel<WFStates.Wyrm> {
        private final ModelPart body, neck, head, jaw, lw, lwt, rw, rwt, t1, t2, t3, crest;
        private final ModelPart[] legs;

        public Wyrm(ModelPart root, String name) {
            super(root, name);
            body = p("body"); neck = p("neck"); head = p("head"); jaw = p("jaw"); lw = p("left_wing"); lwt = p("left_wing_tip");
            rw = p("right_wing"); rwt = p("right_wing_tip"); t1 = p("tail_1"); t2 = p("tail_2"); t3 = p("tail_3"); crest = p("crest");
            legs = new ModelPart[] {p("leg_front_left"), p("leg_back_right"), p("leg_front_right"), p("leg_back_left")};
        }

        @Override
        public void setupAnim(WFStates.Wyrm s) {
            super.setupAnim(s);
            float t = s.ageInTicks;
            float pos = s.walkAnimationPos, spd = Math.min(1f, s.walkAnimationSpeed);
            // hatchlings flutter fast; drakes beat slow and wide
            float rate = switch (s.stage) {
                case 0 -> 0.9f;
                case 1 -> 0.45f;
                default -> 0.16f;
            };
            float amp = s.flying ? (s.stage == 2 ? 0.75f : 0.6f) : 0.08f;
            float fold = s.flying ? 0f : 0.35f;
            lw.zRot += -Mth.sin(t * rate) * amp - fold;
            rw.zRot += Mth.sin(t * rate) * amp + fold;
            lwt.zRot += -Mth.sin(t * rate - 0.8f) * amp * 0.7f;
            rwt.zRot += Mth.sin(t * rate - 0.8f) * amp * 0.7f;
            neck.yRot += s.yRot * Mth.DEG_TO_RAD * 0.4f;
            head.yRot += s.yRot * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += s.xRot * Mth.DEG_TO_RAD;
            jaw.xRot += Math.max(0, Mth.sin(t * 0.07f)) * 0.08f;
            t1.yRot += Mth.sin(t * 0.1f) * 0.18f;
            t2.yRot += Mth.sin(t * 0.1f - 0.6f) * 0.25f;
            t3.yRot += Mth.sin(t * 0.1f - 1.2f) * 0.32f;
            for (int i = 0; i < 4; i++) {
                legs[i].xRot += Mth.cos(pos * 0.9f + (i % 2 == 0 ? 0 : Mth.PI)) * 0.8f * spd;
                if (s.flying) {
                    legs[i].xRot += 0.6f;
                }
            }
            if (s.stage == 0 && spd > 0.05f) {
                body.zRot += Mth.sin(pos * 0.9f) * 0.12f; // hatchlings wobble when they walk
            }
            if (s.sitting) {
                body.y += 2f;
                t1.yRot += 0.8f; // tail curls around
                t2.yRot += 0.8f;
            }
            crest.xRot += Mth.sin(t * 0.05f) * 0.03f;
        }
    }

    public static final class Shardling extends WFModel<WFStates.Shardling> {
        private final ModelPart core, a, b;

        public Shardling(ModelPart root) {
            super(root, "shardling");
            core = p("core"); a = p("splinter_a"); b = p("splinter_b");
        }

        @Override
        public void setupAnim(WFStates.Shardling s) {
            super.setupAnim(s);
            float t = s.ageInTicks;
            core.yRot += t * (s.diving ? 0.5f : 0.12f);
            core.y += Mth.sin(t * 0.15f) * 1.0f;
            float r = 9f;
            a.x += Mth.cos(t * 0.2f) * r;
            a.z += Mth.sin(t * 0.2f) * r;
            b.x += Mth.cos(t * 0.2f + Mth.PI) * r;
            b.z += Mth.sin(t * 0.2f + Mth.PI) * r;
            a.yRot += t * 0.4f;
            b.xRot += t * 0.3f;
        }
    }
}
