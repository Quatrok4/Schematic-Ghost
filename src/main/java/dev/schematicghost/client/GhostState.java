package dev.schematicghost.client;

import dev.schematicghost.schematic.FlatSchematic;
import dev.schematicghost.schematic.LitematicSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.IdentityHashMap;

/**
 * Calcula (a cada ~250 ms) quais blocos do schematic estão faltando ou errados perto do jogador.
 * O resultado é um Snapshot imutável, que o renderizador lê.
 */
public final class GhostState {
    public static final int MAX_BOXES = 1000;
    private static final long REFRESH_NANOS = 250_000_000L;

    /** kind: 0 = faltando (ar onde deveria ter bloco), 1 = bloco errado. xyz em coordenadas do mundo. */
    public record Snapshot(int[] xyz, byte[] kind, LitematicSchematic.BlockInfo[] states,
                           int drawn, int totalMissing, int totalWrong) {}

    private static final Snapshot EMPTY = new Snapshot(new int[0], new byte[0], new LitematicSchematic.BlockInfo[0], 0, 0, 0);

    public static volatile boolean enabled = true;
    public static volatile boolean showBlocks = true;   // blocos fantasmas
    public static volatile boolean showOutline = true;  // contornos coloridos
    public static volatile boolean translucent = true;  // fantasmas translúcidos (senão, sólidos)
    public static volatile int alphaPercent = 45;       // opacidade dos fantasmas translúcidos (10-100)
    public static volatile int range = 48;
    public static volatile Snapshot snapshot = EMPTY;

    private static long lastRefresh;
    private static LitematicSchematic flatSource;
    private static FlatSchematic flat;
    private static final IdentityHashMap<Block, String> NAME_CACHE = new IdentityHashMap<>();

    private GhostState() {}

    private static String nameOf(Block b) {
        String n = NAME_CACHE.get(b);
        if (n == null) {
            n = BuiltInRegistries.BLOCK.getKey(b).toString();
            NAME_CACHE.put(b, n);
        }
        return n;
    }

    public static void refresh(Minecraft mc) {
        LitematicSchematic s = SchematicManager.get();
        if (!enabled || s == null || mc.level == null || mc.player == null) {
            snapshot = EMPTY;
            return;
        }
        long now = System.nanoTime();
        if (now - lastRefresh < REFRESH_NANOS) return;
        lastRefresh = now;

        if (flatSource != s) {
            flat = FlatSchematic.from(s);
            flatSource = s;
        }
        final FlatSchematic f = flat;
        final int ox = SchematicManager.originX(), oy = SchematicManager.originY(), oz = SchematicManager.originZ();
        final BlockPos pp = mc.player.blockPosition();
        final int px = pp.getX(), py = pp.getY(), pz = pp.getZ();
        final int r = range;
        final long r2 = (long) r * r;

        long[] keys = new long[256];
        int n = 0, missing = 0, wrong = 0;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        for (int i = 0; i < f.count; i++) {
            int wx = ox + f.x[i], wy = oy + f.y[i], wz = oz + f.z[i];
            int dx = wx - px, dy = wy - py, dz = wz - pz;
            if (dx > r || dx < -r || dy > r || dy < -r || dz > r || dz < -r) continue;
            long d2 = (long) dx * dx + (long) dy * dy + (long) dz * dz;
            if (d2 > r2) continue;

            mp.set(wx, wy, wz);
            if (!mc.level.hasChunkAt(mp)) continue;
            BlockState st = mc.level.getBlockState(mp);
            if (nameOf(st.getBlock()).equals(f.names[f.pal[i]])) continue;

            int kind = st.isAir() ? 0 : 1;
            if (kind == 0) missing++; else wrong++;
            if (n == keys.length) keys = Arrays.copyOf(keys, n * 2);
            keys[n++] = (d2 << 32) | (long) (i | (kind << 30));
        }

        Arrays.sort(keys, 0, n); // mais perto primeiro
        int drawn = Math.min(n, MAX_BOXES);
        int[] xyz = new int[drawn * 3];
        byte[] kinds = new byte[drawn];
        LitematicSchematic.BlockInfo[] states = new LitematicSchematic.BlockInfo[drawn];
        for (int k = 0; k < drawn; k++) {
            int low = (int) (keys[k] & 0xFFFFFFFFL);
            int kind = (low >>> 30) & 1;
            int i = low & 0x3FFFFFFF;
            xyz[k * 3] = ox + f.x[i];
            xyz[k * 3 + 1] = oy + f.y[i];
            xyz[k * 3 + 2] = oz + f.z[i];
            kinds[k] = (byte) kind;
            states[k] = f.infos[f.pal[i]];
        }
        snapshot = new Snapshot(xyz, kinds, states, drawn, missing, wrong);
    }
}
