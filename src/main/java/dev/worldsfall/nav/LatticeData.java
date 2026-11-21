package dev.worldsfall.nav;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Gateway Lattice (proposal "Navigation"): every attuned End Gateway and every restored End Ship (Mooring) in the
 * world, plus which of them each player has attuned or visited. A player can only travel to nodes in their own set.
 */
public final class LatticeData extends SavedData {
    public static final int GATEWAY = 0, MOORING = 1;

    /** One node: where it is (the gateway block, or a Mooring's Drift Helm), what it is, and its name. */
    public record Node(BlockPos pos, int kind, String name) {
        public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(Node::pos), Codec.INT.fieldOf("kind").forGetter(Node::kind),
            Codec.STRING.fieldOf("name").forGetter(Node::name)
        ).apply(i, Node::new));
    }

    private record Known(UUID player, List<Long> nodes) {
        static final Codec<Known> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(Known::player), Codec.LONG.listOf().fieldOf("nodes").forGetter(Known::nodes)
        ).apply(i, Known::new));
    }

    private record Snapshot(List<Node> nodes, List<Known> known) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Node.CODEC.listOf().optionalFieldOf("nodes", List.of()).forGetter(Snapshot::nodes),
            Known.CODEC.listOf().optionalFieldOf("known", List.of()).forGetter(Snapshot::known)
        ).apply(i, Snapshot::new));
    }

    public static final Codec<LatticeData> CODEC = Snapshot.CODEC.xmap(LatticeData::new, LatticeData::snapshot);
    public static final SavedDataType<LatticeData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "lattice"),
        LatticeData::new, CODEC);

    private final Map<Long, Node> nodes = new LinkedHashMap<>();
    private final Map<UUID, Set<Long>> known = new HashMap<>();

    public LatticeData() {}

    private LatticeData(Snapshot s) {
        s.nodes.forEach(n -> nodes.put(n.pos.asLong(), n));
        s.known.forEach(k -> known.put(k.player, new HashSet<>(k.nodes)));
    }

    private Snapshot snapshot() {
        List<Known> k = new ArrayList<>();
        known.forEach((p, set) -> k.add(new Known(p, List.copyOf(set))));
        return new Snapshot(List.copyOf(nodes.values()), k);
    }

    public static LatticeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public int size() { return nodes.size(); }
    public List<Node> nodes() { return List.copyOf(nodes.values()); }
    public @Nullable Node at(BlockPos pos) { return nodes.get(pos.asLong()); }

    public boolean add(Node node) {
        if (nodes.containsKey(node.pos.asLong())) {
            return false;
        }
        nodes.put(node.pos.asLong(), node);
        setDirty();
        return true;
    }

    public void remove(BlockPos pos) {
        if (nodes.remove(pos.asLong()) != null) {
            known.values().forEach(s -> s.remove(pos.asLong()));
            setDirty();
        }
    }

    public boolean knows(UUID player, BlockPos node) {
        return known.getOrDefault(player, Set.of()).contains(node.asLong());
    }

    public boolean learn(UUID player, BlockPos node) {
        if (!nodes.containsKey(node.asLong())) {
            return false;
        }
        boolean added = known.computeIfAbsent(player, p -> new HashSet<>()).add(node.asLong());
        if (added) {
            setDirty();
        }
        return added;
    }

    public List<Node> knownBy(UUID player) {
        Set<Long> set = known.getOrDefault(player, Set.of());
        return nodes.values().stream().filter(n -> set.contains(n.pos.asLong())).toList();
    }
}
