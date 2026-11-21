package dev.worldsfall.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.worldsfall.Worldsfall;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** Custom GPU pipelines: the layered End sky, the Fracture sky and the Void Tide plane (§18.7, §5.1). */
public final class WFPipelines {
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Worldsfall.MODID, path);
    }

    public static final RenderPipeline END_SKY = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withLocation(id("pipeline/end_sky"))
        .withVertexShader(id("core/end_sky"))
        .withFragmentShader(id("core/end_sky"))
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .build();

    public static final RenderPipeline FRACTURE_SKY = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withLocation(id("pipeline/fracture_sky"))
        .withVertexShader(id("core/end_sky"))
        .withFragmentShader(id("core/fracture_sky"))
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .build();

    public static final RenderPipeline UNDERTOW_SKY = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withLocation(id("pipeline/undertow_sky"))
        .withVertexShader(id("core/end_sky"))
        .withFragmentShader(id("core/undertow_sky"))
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .build();

    public static final RenderPipeline TIDE_PLANE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
        .withLocation(id("pipeline/tide_plane"))
        .withVertexShader(id("core/tide_plane"))
        .withFragmentShader(id("core/tide_plane"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .build();

    private WFPipelines() {}

    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(END_SKY);
        event.registerPipeline(FRACTURE_SKY);
        event.registerPipeline(UNDERTOW_SKY);
        event.registerPipeline(TIDE_PLANE);
    }
}
