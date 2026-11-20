package dev.worldsfall.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.worldsfall.Worldsfall;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads entity geometry authored in Blender and exported as JSON (art/SPEC_MODELS.md) into vanilla
 * {@link LayerDefinition}s. Read from the mod jar so it works during layer registration.
 */
public final class JsonModels {
    /** model name -> (part name -> parent name or null), in file order. */
    private static final Map<String, LinkedHashMap<String, String>> HIERARCHY = new HashMap<>();

    private JsonModels() {}

    public static LayerDefinition load(String name, String... requiredParts) {
        String path = "/assets/" + Worldsfall.MODID + "/models/entity/" + name + ".json";
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        LinkedHashMap<String, String> tree = new LinkedHashMap<>();
        int texW = 64, texH = 64;
        try (InputStream in = JsonModels.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray size = json.getAsJsonArray("texture_size");
            texW = size.get(0).getAsInt();
            texH = size.get(1).getAsInt();
            Map<String, PartDefinition> defs = new HashMap<>();
            List<JsonObject> pending = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("parts")) {
                pending.add(e.getAsJsonObject());
            }
            // parents may be declared after children: resolve in passes
            int guard = 0;
            while (!pending.isEmpty() && guard++ < 64) {
                List<JsonObject> next = new ArrayList<>();
                for (JsonObject p : pending) {
                    String parent = p.has("parent") && !p.get("parent").isJsonNull() ? p.get("parent").getAsString() : null;
                    PartDefinition parentDef = parent == null ? root : defs.get(parent);
                    if (parentDef == null) {
                        next.add(p);
                        continue;
                    }
                    String partName = p.get("name").getAsString();
                    defs.put(partName, parentDef.addOrReplaceChild(partName, cubes(p), pose(p)));
                    tree.put(partName, parent);
                }
                pending = next;
            }
            if (!pending.isEmpty()) {
                Worldsfall.LOGGER.error("Model {} has parts with unknown parents: {}", name, pending.size());
            }
        } catch (Exception e) {
            Worldsfall.LOGGER.error("Could not load entity model {}: {}", name, e.toString());
        }
        for (String required : requiredParts) {
            if (!tree.containsKey(required)) {
                Worldsfall.LOGGER.warn("Model {} lacks part '{}'; adding an empty placeholder", name, required);
                root.addOrReplaceChild(required, CubeListBuilder.create(), PartPose.ZERO);
                tree.put(required, null);
            }
        }
        HIERARCHY.put(name, tree);
        return LayerDefinition.create(mesh, texW, texH);
    }

    private static CubeListBuilder cubes(JsonObject p) {
        CubeListBuilder b = CubeListBuilder.create();
        if (!p.has("cubes")) {
            return b;
        }
        for (JsonElement ce : p.getAsJsonArray("cubes")) {
            JsonObject c = ce.getAsJsonObject();
            JsonArray o = c.getAsJsonArray("origin"), s = c.getAsJsonArray("size"), uv = c.getAsJsonArray("uv");
            float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0f;
            boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
            b.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).mirror(mirror)
                .addBox("", o.get(0).getAsFloat(), o.get(1).getAsFloat(), o.get(2).getAsFloat(), s.get(0).getAsFloat(), s.get(1).getAsFloat(),
                    s.get(2).getAsFloat(), new CubeDeformation(inflate));
        }
        return b;
    }

    private static PartPose pose(JsonObject p) {
        JsonArray pv = p.getAsJsonArray("pivot");
        float[] r = {0, 0, 0};
        if (p.has("rotation")) {
            JsonArray ra = p.getAsJsonArray("rotation");
            for (int i = 0; i < 3; i++) {
                r[i] = ra.get(i).getAsFloat() * Mth.DEG_TO_RAD;
            }
        }
        return PartPose.offsetAndRotation(pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat(), r[0], r[1], r[2]);
    }

    /** Resolves a baked part by name, walking the authored hierarchy from the root. */
    public static ModelPart part(String model, ModelPart root, String name) {
        LinkedHashMap<String, String> tree = HIERARCHY.get(model);
        if (tree == null || !tree.containsKey(name)) {
            return EMPTY;
        }
        List<String> chain = new ArrayList<>();
        for (String n = name; n != null; n = tree.get(n)) {
            chain.add(0, n);
        }
        ModelPart cur = root;
        for (String n : chain) {
            if (!cur.hasChild(n)) {
                return EMPTY;
            }
            cur = cur.getChild(n);
        }
        return cur;
    }

    private static final ModelPart EMPTY = new ModelPart(List.of(), Map.of());
}
