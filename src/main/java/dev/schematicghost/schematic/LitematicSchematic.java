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
 * Schematic no formato .litematic (Litematica). Não usa nenhuma classe do Minecraft.
 *
 * Coordenadas: cada região tem um canto mínimo (minX/minY/minZ) relativo à origem do schematic.
 * Os blocos são entregues em coordenadas relativas à origem; some a origem do placement no mundo.
 */
public final class LitematicSchematic {

    /** Um bloco da paleta: id (ex. "minecraft:oak_stairs") e propriedades (ex. facing=north). */
    public record BlockInfo(String name, Map<String, String> properties) {
        public boolean isAir() {
            return name.equals("minecraft:air")
                    || name.equals("minecraft:cave_air")
                    || name.equals("minecraft:void_air");
        }

        @Override
        public String toString() {
            if (properties.isEmpty()) return name;
            StringBuilder sb = new StringBuilder(name).append('[');
            boolean first = true;
            for (var e : properties.entrySet()) {
                if (!first) sb.append(',');
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            return sb.append(']').toString();
        }
    }

    @FunctionalInterface
    public interface BlockVisitor {
        void visit(int x, int y, int z, BlockInfo block);
    }

    public static final class Region {
        public final String name;
        public final int minX, minY, minZ;
        public final int sizeX, sizeY, sizeZ; // sempre positivos
        public final List<BlockInfo> palette;
        private final int[] indices; // índice da paleta, ordem y, z, x

        Region(String name, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
               List<BlockInfo> palette, int[] indices) {
            this.name = name;
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
            this.palette = palette;
            this.indices = indices;
        }

        public long volume() {
            return (long) sizeX * sizeY * sizeZ;
        }

        /** x,y,z locais à região (0..size-1). */
        public BlockInfo getLocal(int x, int y, int z) {
            return palette.get(indices[(y * sizeZ + z) * sizeX + x]);
        }

        /** Percorre todos os blocos que não são ar, em coordenadas relativas à origem do schematic. */
        public void forEachNonAir(BlockVisitor v) {
            boolean[] air = new boolean[palette.size()];
            for (int i = 0; i < air.length; i++) air[i] = palette.get(i).isAir();
            int i = 0;
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++, i++) {
                        int p = indices[i];
                        if (!air[p]) v.visit(minX + x, minY + y, minZ + z, palette.get(p));
                    }
                }
            }
        }
    }

    public final String name;
    public final String author;
    public final int version;
    public final Map<String, Region> regions;

    private LitematicSchematic(String name, String author, int version, Map<String, Region> regions) {
        this.name = name;
        this.author = author;
        this.version = version;
        this.regions = regions;
    }

    /** Usado por outros leitores (ex. .schem) para montar um schematic a partir de regiões prontas. */
    static LitematicSchematic of(String name, String author, int version, Map<String, Region> regions) {
        return new LitematicSchematic(name, author, version, regions);
    }

    // ---------- leitura ----------

    public static LitematicSchematic read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        }
    }

    public static LitematicSchematic read(InputStream in) throws IOException {
        Map<String, Object> root = Nbt.readCompressed(in);

        Map<String, Object> meta = Nbt.compound(root, "Metadata");
        String name = meta != null ? Nbt.getString(meta, "Name", "sem nome") : "sem nome";
        String author = meta != null ? Nbt.getString(meta, "Author", "?") : "?";
        int version = Nbt.getInt(root, "Version", 0);

        Map<String, Object> regionsTag = Nbt.compound(root, "Regions");
        if (regionsTag == null) throw new IOException("Não parece um .litematic (sem 'Regions')");

        Map<String, Region> regions = new LinkedHashMap<>();
        for (var entry : regionsTag.entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) entry.getValue();
            regions.put(entry.getKey(), readRegion(entry.getKey(), r));
        }
        if (regions.isEmpty()) throw new IOException("Schematic sem regiões");
        return new LitematicSchematic(name, author, version, regions);
    }

    private static Region readRegion(String name, Map<String, Object> r) throws IOException {
        Map<String, Object> pos = Nbt.compound(r, "Position");
        Map<String, Object> size = Nbt.compound(r, "Size");
        if (pos == null || size == null) throw new IOException("Região '" + name + "' sem Position/Size");

        int px = Nbt.getInt(pos, "x", 0), py = Nbt.getInt(pos, "y", 0), pz = Nbt.getInt(pos, "z", 0);
        int sx = Nbt.getInt(size, "x", 0), sy = Nbt.getInt(size, "y", 0), sz = Nbt.getInt(size, "z", 0);

        // Tamanho negativo significa que a região se estende para o lado negativo a partir de Position.
        int minX = px + (sx < 0 ? sx + 1 : 0);
        int minY = py + (sy < 0 ? sy + 1 : 0);
        int minZ = pz + (sz < 0 ? sz + 1 : 0);
        int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);

        long volumeL = (long) ax * ay * az;
        if (volumeL <= 0 || volumeL > Integer.MAX_VALUE - 8) {
            throw new IOException("Região '" + name + "' com volume inválido: " + volumeL);
        }
        int volume = (int) volumeL;

        List<BlockInfo> palette = new ArrayList<>();
        for (Map<String, Object> p : Nbt.compoundList(r, "BlockStatePalette")) {
            String bn = Nbt.getString(p, "Name", "minecraft:air");
            Map<String, String> props = new TreeMap<>();
            Map<String, Object> pt = Nbt.compound(p, "Properties");
            if (pt != null) {
                for (var e : pt.entrySet()) props.put(e.getKey(), String.valueOf(e.getValue()));
            }
            palette.add(new BlockInfo(bn, props));
        }
        if (palette.isEmpty()) throw new IOException("Região '" + name + "' sem paleta");

        long[] packed = Nbt.getLongArray(r, "BlockStates");
        int[] indices = unpack(packed, palette.size(), volume, name);

        return new Region(name, minX, minY, minZ, ax, ay, az, palette, indices);
    }

    /** Quantos bits por entrada: mínimo 2, ou log2 arredondado para cima do tamanho da paleta. */
    static int bitsFor(int paletteSize) {
        int bits = 32 - Integer.numberOfLeadingZeros(Math.max(paletteSize - 1, 1));
        return Math.max(2, bits);
    }

    /** Desempacota o array de longs. As entradas atravessam a fronteira entre longs (sem padding). */
    static int[] unpack(long[] data, int paletteSize, int volume, String regionName) throws IOException {
        int[] out = new int[volume];
        if (data.length == 0) return out; // tudo no índice 0

        int bits = bitsFor(paletteSize);
        long needed = ((long) volume * bits + 63) / 64;
        if (data.length < needed) {
            throw new IOException("Região '" + regionName + "': BlockStates curto demais ("
                    + data.length + " < " + needed + ")");
        }
        long mask = (1L << bits) - 1L;

        for (int i = 0; i < volume; i++) {
            long startOffset = (long) i * bits;
            int startIdx = (int) (startOffset >>> 6);
            int endIdx = (int) (((long) (i + 1) * bits - 1) >>> 6);
            int startBit = (int) (startOffset & 63L);

            int value;
            if (startIdx == endIdx) {
                value = (int) ((data[startIdx] >>> startBit) & mask);
            } else {
                int firstPart = 64 - startBit;
                value = (int) (((data[startIdx] >>> startBit) | (data[endIdx] << firstPart)) & mask);
            }
            if (value >= paletteSize) {
                throw new IOException("Região '" + regionName + "': índice de paleta inválido " + value);
            }
            out[i] = value;
        }
        return out;
    }

    // ---------- utilidades ----------

    /** Contagem de blocos não-ar por nome (sem propriedades), ordenada do mais para o menos frequente. */
    public Map<String, Integer> countBlocks() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Region reg : regions.values()) {
            reg.forEachNonAir((x, y, z, b) -> counts.merge(b.name(), 1, Integer::sum));
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (var e : list) sorted.put(e.getKey(), e.getValue());
        return sorted;
    }

    public int totalNonAir() {
        int total = 0;
        for (int c : countBlocks().values()) total += c;
        return total;
    }
}
