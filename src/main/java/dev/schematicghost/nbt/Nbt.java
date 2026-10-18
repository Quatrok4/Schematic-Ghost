package dev.schematicghost.nbt;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Leitor NBT mínimo, sem depender de nenhuma classe do Minecraft.
 * Isso deixa o parser testável fora do jogo e imune a mudanças de API entre versões.
 *
 * Tipos retornados: Byte, Short, Integer, Long, Float, Double, byte[], String,
 * List&lt;Object&gt;, Map&lt;String,Object&gt;, int[], long[].
 */
public final class Nbt {
    private static final int MAX_DEPTH = 512;
    private static final int MAX_ARRAY = 1 << 28;

    private Nbt() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readCompressed(InputStream in) throws IOException {
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)))) {
            int type = d.readUnsignedByte();
            if (type != 10) throw new IOException("Arquivo NBT inválido: raiz não é um compound");
            d.readUTF(); // nome da raiz (ignorado)
            return (Map<String, Object>) readPayload(d, 10, 0);
        }
    }

    private static Object readPayload(DataInputStream d, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT profundo demais");
        switch (type) {
            case 1: return d.readByte();
            case 2: return d.readShort();
            case 3: return d.readInt();
            case 4: return d.readLong();
            case 5: return d.readFloat();
            case 6: return d.readDouble();
            case 7: {
                int n = checkLen(d.readInt());
                byte[] a = new byte[n];
                d.readFully(a);
                return a;
            }
            case 8: return d.readUTF();
            case 9: {
                int elemType = d.readUnsignedByte();
                int n = checkLen(d.readInt());
                List<Object> list = new ArrayList<>(Math.min(n, 4096));
                for (int i = 0; i < n; i++) list.add(readPayload(d, elemType, depth + 1));
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = d.readUnsignedByte();
                    if (t == 0) break;
                    String key = d.readUTF();
                    map.put(key, readPayload(d, t, depth + 1));
                }
                return map;
            }
            case 11: {
                int n = checkLen(d.readInt());
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = d.readInt();
                return a;
            }
            case 12: {
                int n = checkLen(d.readInt());
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = d.readLong();
                return a;
            }
            default:
                throw new IOException("Tipo NBT desconhecido: " + type);
        }
    }

    private static int checkLen(int n) throws IOException {
        if (n < 0 || n > MAX_ARRAY) throw new IOException("Tamanho de array NBT inválido: " + n);
        return n;
    }

    // ---------- helpers de acesso ----------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compound(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> compoundList(Map<String, Object> m, String key) {
        Object o = m.get(key);
        if (!(o instanceof List<?> l)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object e : l) if (e instanceof Map) out.add((Map<String, Object>) e);
        return out;
    }

    public static int getInt(Map<String, Object> m, String key, int def) {
        Object o = m.get(key);
        return o instanceof Number n ? n.intValue() : def;
    }

    public static String getString(Map<String, Object> m, String key, String def) {
        Object o = m.get(key);
        return o instanceof String s ? s : def;
    }

    public static long[] getLongArray(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof long[] a ? a : new long[0];
    }

    public static byte[] getByteArray(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof byte[] a ? a : new byte[0];
    }
}
