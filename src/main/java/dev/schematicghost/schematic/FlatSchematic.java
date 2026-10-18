package dev.schematicghost.schematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Versão "achatada" do schematic: só os blocos não-ar, em arrays simples.
 * Ideal para varrer rápido a cada atualização. Não usa classes do Minecraft.
 * Coordenadas relativas à origem do schematic.
 */
public final class FlatSchematic {
    public final int count;
    public final int[] x, y, z;
    public final int[] pal;      // índice em names[]
    public final String[] names; // id do bloco para cada entrada, ex. "minecraft:stone"
    public final LitematicSchematic.BlockInfo[] infos; // estado completo (com propriedades), mesmo índice de names

    private FlatSchematic(int count, int[] x, int[] y, int[] z, int[] pal, String[] names,
                          LitematicSchematic.BlockInfo[] infos) {
        this.count = count;
        this.x = x; this.y = y; this.z = z;
        this.pal = pal;
        this.names = names;
        this.infos = infos;
    }

    public static FlatSchematic from(LitematicSchematic s) {
        int total = s.totalNonAir();
        int[] xs = new int[total], ys = new int[total], zs = new int[total], ps = new int[total];
        Map<LitematicSchematic.BlockInfo, Integer> index = new HashMap<>();
        List<LitematicSchematic.BlockInfo> infos = new ArrayList<>();
        int[] n = {0};
        for (LitematicSchematic.Region r : s.regions.values()) {
            r.forEachNonAir((x, y, z, b) -> {
                Integer id = index.get(b);
                if (id == null) {
                    id = infos.size();
                    infos.add(b);
                    index.put(b, id);
                }
                int i = n[0]++;
                xs[i] = x; ys[i] = y; zs[i] = z; ps[i] = id;
            });
        }
        LitematicSchematic.BlockInfo[] infoArr = infos.toArray(new LitematicSchematic.BlockInfo[0]);
        String[] names = new String[infoArr.length];
        for (int i = 0; i < infoArr.length; i++) names[i] = infoArr[i].name();
        return new FlatSchematic(n[0], xs, ys, zs, ps, names, infoArr);
    }
}
