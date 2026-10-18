package dev.schematicghost.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.schematicghost.schematic.LitematicSchematic;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Comandos (somente cliente):
 *   /ghost list            lista os .litematic da pasta .minecraft/schematics
 *   /ghost load <arquivo>  carrega e mostra um resumo
 *   /ghost origin          define a origem na posição do jogador
 *   /ghost compare         compara com o mundo (só chunks carregados) e mostra faltando/errado
 */
public final class GhostCommands {
    private GhostCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            LiteralArgumentBuilder.<FabricClientCommandSource>literal("ghost")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(ctx -> {
                    try {
                        List<String> files = SchematicManager.listFiles();
                        if (files.isEmpty()) {
                            say(ctx.getSource(), "Nenhum .litematic em " + SchematicManager.schematicsDir());
                        } else {
                            say(ctx.getSource(), "Schematics: " + String.join(", ", files));
                        }
                    } catch (Exception e) {
                        say(ctx.getSource(), "Erro: " + e.getMessage());
                    }
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("load")
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                            "arquivo", StringArgumentType.greedyString()).executes(ctx -> {
                        String file = StringArgumentType.getString(ctx, "arquivo");
                        try {
                            LitematicSchematic s = SchematicManager.load(file);
                            say(ctx.getSource(), "Carregado: " + s.name + " (autor " + s.author
                                    + ", versão " + s.version + ")");
                            say(ctx.getSource(), "Regiões: " + s.regions.size()
                                    + " | blocos (sem ar): " + s.totalNonAir());
                            int shown = 0;
                            for (Map.Entry<String, Integer> e : s.countBlocks().entrySet()) {
                                say(ctx.getSource(), "  " + e.getValue() + "x " + e.getKey());
                                if (++shown >= 8) break;
                            }
                        } catch (Exception e) {
                            say(ctx.getSource(), "Erro ao carregar: " + e.getMessage());
                        }
                        return 1;
                    })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("origin").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) return 0;
                    BlockPos p = mc.player.blockPosition();
                    SchematicManager.setOrigin(p.getX(), p.getY(), p.getZ());
                    say(ctx.getSource(), "Origem definida em " + p.getX() + " " + p.getY() + " " + p.getZ());
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(ctx -> {
                    GhostState.enabled = !GhostState.enabled;
                    say(ctx.getSource(), "Ghost " + (GhostState.enabled ? "LIGADO" : "DESLIGADO"));
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range")
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                            "blocos", IntegerArgumentType.integer(8, 128)).executes(ctx -> {
                        GhostState.range = IntegerArgumentType.getInteger(ctx, "blocos");
                        say(ctx.getSource(), "Alcance: " + GhostState.range + " blocos");
                        return 1;
                    })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx -> {
                    GhostState.Snapshot sn = GhostState.snapshot;
                    say(ctx.getSource(), (GhostState.enabled ? "ligado" : "desligado") + " | alcance "
                            + GhostState.range + " | faltando " + sn.totalMissing() + " | errados "
                            + sn.totalWrong() + " | desenhados " + sn.drawn() + " (máx " + GhostState.MAX_BOXES + ")");
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("blocks").executes(ctx -> {
                    GhostState.showBlocks = !GhostState.showBlocks;
                    say(ctx.getSource(), "Blocos fantasmas " + (GhostState.showBlocks ? "LIGADOS" : "DESLIGADOS"));
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("outline").executes(ctx -> {
                    GhostState.showOutline = !GhostState.showOutline;
                    say(ctx.getSource(), "Contornos " + (GhostState.showOutline ? "LIGADOS" : "DESLIGADOS"));
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("translucent").executes(ctx -> {
                    GhostState.translucent = !GhostState.translucent;
                    say(ctx.getSource(), "Fantasmas " + (GhostState.translucent ? "TRANSLÚCIDOS" : "SÓLIDOS"));
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("alpha")
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
                            "porcento", IntegerArgumentType.integer(10, 100)).executes(ctx -> {
                        GhostState.alphaPercent = IntegerArgumentType.getInteger(ctx, "porcento");
                        say(ctx.getSource(), "Opacidade dos fantasmas: " + GhostState.alphaPercent + "%");
                        return 1;
                    })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("compare").executes(ctx -> {
                    compare(ctx.getSource());
                    return 1;
                }))
        );
    }

    private static void compare(FabricClientCommandSource src) {
        LitematicSchematic s = SchematicManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (s == null) { say(src, "Carregue um schematic primeiro: /ghost load <arquivo>"); return; }
        if (mc.level == null) { say(src, "Entre em um mundo primeiro."); return; }

        final int ox = SchematicManager.originX(), oy = SchematicManager.originY(), oz = SchematicManager.originZ();
        final int[] missing = {0}, wrong = {0}, correct = {0}, unloaded = {0};

        for (LitematicSchematic.Region r : s.regions.values()) {
            r.forEachNonAir((x, y, z, expected) -> {
                BlockPos pos = new BlockPos(ox + x, oy + y, oz + z);
                if (!mc.level.hasChunkAt(pos)) { unloaded[0]++; return; }
                BlockState actual = mc.level.getBlockState(pos);
                String actualId = BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString();
                if (actualId.equals(expected.name())) correct[0]++;
                else if (actual.isAir()) missing[0]++;
                else wrong[0]++;
            });
        }
        say(src, "Corretos: " + correct[0] + " | Faltando: " + missing[0] + " | Errados: " + wrong[0]
                + " | Em chunks não carregados: " + unloaded[0]);
    }

    private static void say(FabricClientCommandSource src, String msg) {
        src.sendFeedback(Component.literal(msg));
    }
}
