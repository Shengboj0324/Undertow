package dev.worldsfall.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/** Base for Blender-authored JSON models: resolves parts by name through the authored hierarchy. */
public abstract class WFModel<S extends LivingEntityRenderState> extends EntityModel<S> {
    protected final String modelName;

    protected WFModel(ModelPart root, String modelName) {
        super(root);
        this.modelName = modelName;
    }

    protected WFModel(ModelPart root, String modelName, Function<Identifier, RenderType> renderType) {
        super(root, renderType);
        this.modelName = modelName;
    }

    protected ModelPart p(String name) {
        return JsonModels.part(modelName, root(), name);
    }

    protected static float rad(float deg) {
        return deg * (float) (Math.PI / 180.0);
    }
}
