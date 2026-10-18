package dev.schematicghost.client;

import dev.schematicghost.schematic.LitematicSchematic;
import dev.schematicghost.schematic.SpongeSchematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;

/** Guarda o schematic carregado e onde ele está posicionado no mundo. */
public final class SchematicManager {
    private static LitematicSchematic current;
    private static String currentFile;
    private static int originX, originY, originZ;

    private SchematicManager() {}

    public static Path schematicsDir() throws IOException {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("schematics");
        Files.createDirectories(dir);
        return dir;
    }

    public static List<String> listFiles() throws IOException {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(schematicsDir())) {
            s.filter(p -> isSupported(p.getFileName().toString()))
                    .forEach(p -> out.add(p.getFileName().toString()));
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static LitematicSchematic load(String fileName) throws IOException {
        Path dir = schematicsDir().toAbsolutePath().normalize();
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir)) throw new IOException("Caminho fora da pasta schematics");
        if (!Files.isRegularFile(file)) throw new IOException("Arquivo não encontrado: " + fileName);
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".schem")) current = SpongeSchematic.read(file);
        else if (lower.endsWith(".litematic")) current = LitematicSchematic.read(file);
        else throw new IOException("Formato não suportado (use .litematic ou .schem): " + fileName);
        currentFile = fileName;
        return current;
    }

    public static boolean isSupported(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".litematic") || n.endsWith(".schem");
    }

    public static LitematicSchematic get() { return current; }
    public static String fileName() { return currentFile; }

    public static void setOrigin(int x, int y, int z) { originX = x; originY = y; originZ = z; }
    public static int originX() { return originX; }
    public static int originY() { return originY; }
    public static int originZ() { return originZ; }
}
