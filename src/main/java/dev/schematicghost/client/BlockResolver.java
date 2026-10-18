package dev.schematicghost.client;

import dev.schematicghost.schematic.LitematicSchematic.BlockInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Converte o bloco do schematic (nome + propriedades em texto) em um BlockState real do jogo. */
public final class BlockResolver {
    private static Map<String, Block> blocksByName;
    private static final Map<BlockInfo, BlockState> CACHE = new HashMap<>();

    private BlockResolver() {}

    /** Devolve null se o bloco não existir nesta versão do jogo. */
    public static BlockState resolve(BlockInfo info) {
        if (CACHE.containsKey(info)) return CACHE.get(info);

        if (blocksByName == null) {
            blocksByName = new HashMap<>();
            for (Block b : BuiltInRegistries.BLOCK) {
                blocksByName.put(BuiltInRegistries.BLOCK.getKey(b).toString(), b);
            }
        }
        Block block = blocksByName.get(info.name());
        BlockState state = null;
        if (block != null) {
            state = block.defaultBlockState();
            for (Property<?> p : state.getProperties()) {
                String v = info.properties().get(p.getName());
                if (v != null) state = withValue(state, p, v);
            }
        }
        CACHE.put(info, state);
        return state;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState s, Property<T> p, String value) {
        Optional<T> v = p.getValue(value);
        return v.isPresent() ? s.setValue(p, v.get()) : s;
    }
}
