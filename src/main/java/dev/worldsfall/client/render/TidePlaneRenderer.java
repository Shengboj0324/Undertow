package dev.worldsfall.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.worldsfall.client.ClientWorldState;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The Void Tide plane (§5.1, §20.6): one translucent camera-centred quad at the tide height, plus a dashed
 * ghost line for 60 s where the tide was, so its direction of motion is readable. One draw call each.
 */
public final class TidePlaneRenderer {
    private static @Nullable GpuBuffer quad;
    private static long drawnFrame = -1;

    private TidePlaneRenderer() {}

    private static GpuBuffer quad() {
        if (quad == null) {
            float r = 1024f;
            try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * 4)) {
                BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
                b.addVertex(-r, 0, -r);
                b.addVertex(-r, 0, r);
                b.addVertex(r, 0, r);
                b.addVertex(r, 0, -r);
                try (MeshData mesh = b.buildOrThrow()) {
                    quad = RenderSystem.getDevice().createBuffer(() -> "Worldsfall tide plane", 32, mesh.vertexBuffer());
                }
            }
        }
        return quad;
    }

    /** Preferred path: inside the vanilla translucent pass. */
    public static void afterTranslucent(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        draw(event.getRenderPass(), event.getModelViewMatrix());
    }

    /** Fallback when OIT replaced the translucent pass: draw at the end of the level with our own pass. */
    public static void afterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || drawnFrame == frameId()) {
            return;
        }
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(() -> "Worldsfall tide", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            draw(pass, event.getModelViewMatrix());
        }
    }

    private static long frameId() {
        return Minecraft.getInstance().getFrameTimeNs();
    }

    private static void draw(@Nullable RenderPass pass, Matrix4fc modelView) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (pass == null || level == null || !Tides.appliesTo(level)) {
            return;
        }
        drawnFrame = frameId();
        ClientWorldState st = ClientWorldState.get();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double tideY = Tides.height(level, partial);
        float alpha = 0.92f;
        Vector3f tint = st.calmed() > 0.5f ? new Vector3f(0.725f, 0.827f, 1.0f) : new Vector3f(0.227f, 0.878f, 0.816f);
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(6);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setPipeline(RenderSystem.getCompiledPipeline(WFPipelines.TIDE_PLANE));
        pass.setVertexBuffer(0, quad().slice());
        pass.setIndexBuffer(indexBuffer, indices.type());
        // the plane, centred on the camera
        Matrix4f tex = new Matrix4f();
        tex.m00(st.timeSeconds()).m01(0).m02(st.tideMotion()).m03(st.tideBrightness());
        tex.m10((float) cam.x).m11((float) cam.y).m12((float) cam.z);
        GpuBufferSlice t = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView),
            new Vector4f(tint.x, tint.y, tint.z, alpha), new Vector3f(0, (float) (tideY - cam.y), 0), tex);
        pass.setUniform("DynamicTransforms", t);
        pass.drawIndexed(6, 1, 0, 0, 0);
        // the ghost line where the tide was a moment ago
        double ghost = st.ghostLineY();
        if (!Double.isNaN(ghost) && Math.abs(ghost - tideY) > 0.75) {
            Matrix4f gtex = new Matrix4f(tex).m01(1);
            GpuBufferSlice g = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView),
                new Vector4f(tint.x, tint.y, tint.z, st.ghostAlpha()), new Vector3f(0, (float) (ghost - cam.y), 0), gtex);
            pass.setUniform("DynamicTransforms", g);
            pass.drawIndexed(6, 1, 0, 0, 0);
        }
    }
}
