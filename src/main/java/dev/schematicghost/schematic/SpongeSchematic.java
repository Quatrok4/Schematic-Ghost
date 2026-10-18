package dev.schematicghost.schematic;

import dev.schematicghost.nbt.Nbt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Leitor de .schem (formato Sponge, usado pelo WorldEdit). Versões 2 e 3.
 * Devolve o mesmo tipo do leitor de .litematic, com uma única região.
 * Não usa classes do Minecraft.
 */
public final class SpongeSchematic {
    private SpongeSchematic() {}

    public static LitematicSchematic read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            String fallbackName = file.getFileName().toString();
            return read(in, fallbackName);
        }
    }

    public static LitematicSchematic read(InputStream in, String fallbackName) throws IOException {
        Map<String, Object> root = Nbt.readCompressed(in);
        // v3 guarda tudo dentro de "Schematic"; v2 usa a raiz.
        Map<String, Object> s = Nbt.compound(root, "Schematic");
        if (s == null) s = root;

        int version = Nbt.getInt(s, "Version", 0);
        int w = Nbt.getInt(s, "Width", 0) & 0xFFFF;   // short sem sinal
        int h = Nbt.getInt(s, "Height", 0) & 0xFFFF;
        int l = Nbt.getInt(s, "Length", 0) & 0xFFFF;
        if (w <= 0 || h <= 0 || l <= 0) throw new IOException("Não parece um .schem (sem Width/Height/Length)");

        Map<String, Object> paletteTag;
        byte[] data;
        Map<String, Object> blocks = Nbt.compound(s, "Blocks"); // v3
        if (blocks != null) {
            paletteTag = Nbt.compound(blocks, "Palette");
            data = Nbt.getByteArray(blocks, "Data");
        } else {                                                 // v2
            paletteTag = Nbt.compound(s, "Palette");
            data = Nbt.getByteArray(s, "BlockData");
        }
        if (paletteTag == null || data.length == 0) throw new IOException("Schematic sem Palette/dados de blocos");

        long volumeL = (long) w * h * l;
        if (volumeL > Integer.MAX_VALUE - 8) throw new IOException("Volume grande demais: " + volumeL);
        int volume = (int) volumeL;

        // paleta: "minecraft:oak_stairs[facing=north]" -> id
        int max = -1;
        for (Object v : paletteTag.values()) if (v instanceof Number n) max = Math.max(max, n.intValue());
        if (max < 0) throw new IOException("Paleta vazia");
        LitematicSchematic.BlockInfo[] arr = new LitematicSchematic.BlockInfo[max + 1];
        for (var e : paletteTag.entrySet()) {
            if (e.getValue() instanceof Number n && n.intValue() >= 0 && n.intValue() <= max) {
                arr[n.intValue()] = parseState(e.getKey());
            }
        }
        List<LitematicSchematic.BlockInfo> palette = new ArrayList<>();
        for (LitematicSchematic.BlockInfo b : arr) {
            palette.add(b != null ? b : new LitematicSchematic.BlockInfo("minecraft:air", new TreeMap<>()));
        }

        // BlockData: varints, ordem x + z*Width + y*Width*Length (igual ao nosso y,z,x)
        int[] indices = new int[volume];
        int pos = 0;
        for (int i = 0; i < volume; i++) {
            int value = 0, shift = 0, b;
            do {
                if (pos >= data.length) throw new IOException("BlockData curto demais (bloco " + i + " de " + volume + ")");
                b = data[pos++] & 0xFF;
                value |= (b & 0x7F) << shift;
                shift += 7;
                if (shift > 35) throw new IOException("Varint inválido no BlockData");
            } while ((b & 0x80) != 0);
            if (value >= palette.size()) throw new IOException("Índice de paleta inválido: " + value);
            indices[i] = value;
        }

        Map<String, Object> meta = Nbt.compound(s, "Metadata");
        String name = meta != null ? Nbt.getString(meta, "Name", fallbackName) : fallbackName;
        String author = meta != null ? Nbt.getString(meta, "Author", "?") : "?";

        Map<String, LitematicSchematic.Region> regions = new LinkedHashMap<>();
        regions.put("main", new LitematicSchematic.Region("main", 0, 0, 0, w, h, l, palette, indices));
        return LitematicSchematic.of(name, author, version, regions);
    }

    static LitematicSchematic.BlockInfo parseState(String key) {
        int br = key.indexOf('[');
        String name = br < 0 ? key : key.substring(0, br);
        if (!name.contains(":")) name = "minecraft:" + name;
        Map<String, String> props = new TreeMap<>();
        if (br >= 0 && key.endsWith("]")) {
            String inner = key.substring(br + 1, key.length() - 1);
            for (String kv : inner.split(",")) {
                int eq = kv.indexOf('=');
                if (eq > 0) props.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
        }
        return new LitematicSchematic.BlockInfo(name, props);
    }
}
