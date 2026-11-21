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
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.worldsfall.client.ClientWorldState;
import dev.worldsfall.config.WFConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws the layered End sky and the Fracture sky with custom shaders (§18.7). A cube around the camera; the
 * fragment shader turns each pixel's direction into gradient, stars, nebula, debris silhouettes and tide glow.
 */
public final class WFSkyRenderer implements CustomSkyboxRenderer {
    private final boolean fracture;
    private @Nullable GpuBuffer cube;

    public WFSkyRenderer(boolean fracture) {
        this.fracture = fracture;
    }

    private GpuBuffer cube() {
        if (cube == null) {
            float s = 100f;
            try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * 24)) {
                BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
                // +Y, -Y, +X, -X, +Z, -Z
                b.addVertex(-s, s, -s); b.addVertex(-s, s, s); b.addVertex(s, s, s); b.addVertex(s, s, -s);
                b.addVertex(-s, -s, -s); b.addVertex(s, -s, -s); b.addVertex(s, -s, s); b.addVertex(-s, -s, s);
                b.addVertex(s, -s, -s); b.addVertex(s, s, -s); b.addVertex(s, s, s); b.addVertex(s, -s, s);
                b.addVertex(-s, -s, -s); b.addVertex(-s, -s, s); b.addVertex(-s, s, s); b.addVertex(-s, s, -s);
                b.addVertex(-s, -s, s); b.addVertex(s, -s, s); b.addVertex(s, s, s); b.addVertex(-s, s, s);
                b.addVertex(-s, -s, -s); b.addVertex(-s, s, -s); b.addVertex(s, s, -s); b.addVertex(s, -s, -s);
                try (MeshData mesh = b.buildOrThrow()) {
                    cube = RenderSystem.getDevice().createBuffer(() -> "Worldsfall sky cube", 32, mesh.vertexBuffer());
                }
            }
        }
        return cube;
    }

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState, Matrix4fc modelViewMatrix, GpuBufferSlice skyFog) {
        if (!WFConfig.get(WFConfig.CLIENT.customSky, true)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        ClientWorldState st = ClientWorldState.get();
        Matrix4f tex = new Matrix4f();
        Vector4f colorMod;
        RenderPipeline pipeline;
        if (fracture) {
            pipeline = WFPipelines.FRACTURE_SKY;
            tex.m00(st.timeSeconds()).m01(st.bossPulse()).m02(st.bossCalm()).m03(0);
            Vector3f core = st.coreDirection();
            tex.m10(core.x).m11(core.y).m12(core.z).m13(st.coreAngularSize());
            colorMod = new Vector4f(1, 1, 1, 1);
        } else {
            pipeline = WFPipelines.END_SKY;
            Vector3f tint = st.nebulaTint();
            colorMod = new Vector4f(tint.x, tint.y, tint.z, st.nebulaStrength() * (0.35f + 0.65f * st.starBrightness()));
            tex.m00(st.timeSeconds()).m01(st.tideGlow()).m02(st.calmed()).m03(st.underHaze());
            Vector3f cam = st.cameraPos();
            tex.m10(cam.x).m11(cam.y).m12(cam.z).m13(st.starBrightness());
            float[] stars = st.orreryStarDirections();
            tex.m20(stars[0]).m21(stars[1]).m22(stars[2]).m23(stars[3]);
            tex.m30(stars[4]).m31(stars[5]).m32(st.overmoonRadius()).m33(st.constellation());
        }
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(36);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelViewMatrix), colorMod, new Vector3f(), tex);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(() -> "Worldsfall sky", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, cube().slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(36, 1, 0, 0, 0);
        }
        return true;
    }
}
