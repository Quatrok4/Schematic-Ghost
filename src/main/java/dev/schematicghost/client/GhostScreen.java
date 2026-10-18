package dev.schematicghost.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Painel do mod: carregar arquivo, ligar/desligar opções, ajustar opacidade/alcance e mover a origem. */
public class GhostScreen extends Screen {
    private static final int ROW = 21;      // distância entre linhas
    private static final int H = 20;        // altura dos botões (fixa em 20)
    private static final int COL_W = 140;
    private static final int FILES_PER_PAGE = 5;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFAA00;
    private static final int AQUA = 0xFF55FFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int RED = 0xFFFF5555;

    private int page = 0;
    private int pages = 1;
    private List<String> files = List.of();
    private String message = "";

    // posições calculadas em init(), usadas também ao desenhar os textos
    private int top, left, right, y0;

    public GhostScreen() {
        super(Component.literal("Schematic Ghost"));
    }

    @Override
    protected void init() {
        try {
            files = SchematicManager.listFiles();
        } catch (IOException e) {
            files = List.of();
            message = "Erro ao listar arquivos: " + e.getMessage();
        }
        pages = Math.max(1, (files.size() + FILES_PER_PAGE - 1) / FILES_PER_PAGE);
        page = Math.min(page, pages - 1);

        int cx = this.width / 2;
        top = Math.max(4, (this.height - 232) / 2);
        left = cx - COL_W - 5;
        right = cx + 5;
        y0 = top + 36;

        // ---- coluna esquerda: arquivos ----
        for (int i = 0; i < FILES_PER_PAGE; i++) {
            int idx = page * FILES_PER_PAGE + i;
            if (idx >= files.size()) break;
            final String name = files.get(idx);
            boolean loaded = name.equals(SchematicManager.fileName());
            String label = (loaded ? "> " : "") + shorten(name, loaded ? 17 : 19);
            this.addRenderableWidget(Button.builder(Component.literal(label), btn -> loadFile(name))
                    .bounds(left, y0 + i * ROW, COL_W, H).build());
        }
        int navY = y0 + FILES_PER_PAGE * ROW;
        this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
            if (page > 0) { page--; this.rebuildWidgets(); }
        }).bounds(left, navY, 30, H).build());
        this.addRenderableWidget(Button.builder(Component.literal("Atualizar"), btn -> {
            message = "";
            this.rebuildWidgets();
        }).bounds(left + 34, navY, COL_W - 68, H).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
            if (page < pages - 1) { page++; this.rebuildWidgets(); }
        }).bounds(left + COL_W - 30, navY, 30, H).build());

        // ---- coluna direita: opções ----
        toggle("Ativo", 0, () -> GhostState.enabled, v -> GhostState.enabled = v);
        toggle("Blocos fantasmas", 1, () -> GhostState.showBlocks, v -> GhostState.showBlocks = v);
        toggle("Translúcido", 2, () -> GhostState.translucent, v -> GhostState.translucent = v);
        toggle("Contornos", 3, () -> GhostState.showOutline, v -> GhostState.showOutline = v);

        // opacidade: [-] texto [+]
        int yA = y0 + 4 * ROW;
        this.addRenderableWidget(Button.builder(Component.literal("-"), btn ->
                GhostState.alphaPercent = Math.max(10, GhostState.alphaPercent - 5))
                .bounds(right, yA, 30, H).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), btn ->
                GhostState.alphaPercent = Math.min(100, GhostState.alphaPercent + 5))
                .bounds(right + COL_W - 30, yA, 30, H).build());

        // alcance: [-] texto [+]
        int yR = y0 + 5 * ROW;
        this.addRenderableWidget(Button.builder(Component.literal("-"), btn ->
                GhostState.range = Math.max(8, GhostState.range - 8))
                .bounds(right, yR, 30, H).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), btn ->
                GhostState.range = Math.min(128, GhostState.range + 8))
                .bounds(right + COL_W - 30, yR, 30, H).build());

        // origem aqui
        this.addRenderableWidget(Button.builder(Component.literal("Origem: onde estou"), btn -> {
            setOriginHere();
        }).bounds(right, y0 + 6 * ROW, COL_W, H).build());

        // ---- faixa de baixo: mover a origem ----
        String[] names = {"X-", "X+", "Y-", "Y+", "Z-", "Z+"};
        int[][] d = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
        int bw = 38, gap = 4;
        int startX = cx - (6 * bw + 5 * gap) / 2;
        for (int i = 0; i < 6; i++) {
            final int[] delta = d[i];
            this.addRenderableWidget(Button.builder(Component.literal(names[i]), btn ->
                    SchematicManager.setOrigin(SchematicManager.originX() + delta[0],
                            SchematicManager.originY() + delta[1],
                            SchematicManager.originZ() + delta[2]))
                    .bounds(startX + i * (bw + gap), y0 + 7 * ROW, bw, H).build());
        }
    }

    @FunctionalInterface
    private interface BoolSetter { void set(boolean v); }

    private void toggle(String label, int row, BooleanSupplier get, BoolSetter set) {
        this.addRenderableWidget(Button.builder(toggleText(label, get.getAsBoolean()), btn -> {
            set.set(!get.getAsBoolean());
            btn.setMessage(toggleText(label, get.getAsBoolean()));
        }).bounds(right, y0 + row * ROW, COL_W, H).build());
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "SIM" : "NÃO"));
    }

    private void loadFile(String name) {
        try {
            SchematicManager.load(name);
            message = "";
            // primeira vez: coloca a origem onde o jogador está
            if (!SchematicManager.hasOrigin()) setOriginHere();
        } catch (Exception e) {
            message = "Erro ao carregar: " + e.getMessage();
        }
        this.rebuildWidgets();
    }

    private void setOriginHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos p = mc.player.blockPosition();
        SchematicManager.setOrigin(p.getX(), p.getY(), p.getZ());
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;
        center(graphics, "Schematic Ghost", cx, top, GOLD);

        String loaded = SchematicManager.fileName();
        center(graphics, loaded == null ? "Nenhum arquivo carregado - clique em um arquivo"
                : "Arquivo: " + loaded, cx, top + 12, WHITE);
        if (SchematicManager.get() != null) {
            GhostState.Snapshot sn = GhostState.snapshot;
            center(graphics, "Faltando: " + sn.totalMissing() + "   Errados: " + sn.totalWrong()
                    + "   Origem: " + SchematicManager.originX() + " " + SchematicManager.originY()
                    + " " + SchematicManager.originZ(), cx, top + 23, AQUA);
        }

        graphics.text(this.font, "Arquivos (" + (page + 1) + "/" + pages + ")", left, y0 - 10, GRAY, true);
        if (files.isEmpty()) {
            graphics.text(this.font, "Pasta schematics vazia", left + 4, y0 + 6, RED, true);
        }
        graphics.text(this.font, "Mover origem:", left, y0 + 6 * ROW + 6, GRAY, true);

        center(graphics, "Opacidade: " + GhostState.alphaPercent + "%", right + COL_W / 2, y0 + 4 * ROW + 6, WHITE);
        center(graphics, "Alcance: " + GhostState.range, right + COL_W / 2, y0 + 5 * ROW + 6, WHITE);

        String bottom = message.isEmpty() ? "ESC fecha o painel" : message;
        center(graphics, bottom, cx, y0 + 8 * ROW + 4, message.isEmpty() ? GRAY : RED);
    }

    private void center(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(this.font, text, x - this.font.width(text) / 2, y, color, true);
    }
}
