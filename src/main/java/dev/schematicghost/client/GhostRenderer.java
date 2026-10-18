package dev.schematicghost.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mostra o schematic:
 *  - blocos fantasmas: o modelo real do bloco (com textura e direção), um pouco menor que o bloco,
 *    nos lugares onde falta construir;
 *  - contornos: azul = falta, vermelho = bloco errado.
 *
 * Fase 1 (extração): calcula o que falta e envia os contornos (Gizmos).
 * Fase 2 (COLLECT_SUBMITS): entrega os blocos ao jogo pela fila de submits, sem tocar na GPU.
 */
public final class GhostRenderer {
    private static final double INSET = 0.02;
    private static final int COLOR_MISSING = 0xFF3399FF;
    private static final int COLOR_WRONG = 0xFFFF3333;

    private static final float GHOST_SCALE = 0.9f;               // um pouco menor que o bloco real
    private static final float GHOST_INSET = (1f - GHOST_SCALE) / 2f;

    private GhostRenderer() {}

    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(GhostRenderer::extract);

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            GhostState.Snapshot snap = GhostState.snapshot;
            var level = Minecraft.getInstance().level;
            if (!GhostState.showBlocks || snap.drawn() == 0 || level == null) return;

            var collector = context.submitNodeCollector();
            var poseStack = context.poseStack();
            Vec3 cam = context.levelState().cameraRenderState.pos;

            if (GhostState.translucent) {
                TranslucentGhost.submit(collector, poseStack, cam, snap, GhostState.alphaPercent);
                return;
            }

            for (int i = 0; i < snap.drawn(); i++) {
                if (snap.kind()[i] != 0) continue; // só onde falta bloco (onde há bloco errado já há um bloco)
                BlockState state = BlockResolver.resolve(snap.states()[i]);
                if (state == null || state.isAir()) continue;

                int bx = snap.xyz()[i * 3], by = snap.xyz()[i * 3 + 1], bz = snap.xyz()[i * 3 + 2];
                BlockPos pos = new BlockPos(bx, by, bz);

                // Mesmo estado que o jogo usa para desenhar blocos caindo / pistões.
                MovingBlockRenderState rs = new MovingBlockRenderState();
                rs.randomSeedPos = pos;
                rs.blockPos = pos;
                rs.blockState = state;
                rs.biome = level.getBiome(pos);
                rs.cardinalLighting = level.cardinalLighting();
                rs.lightEngine = level.getLightEngine();

                poseStack.pushPose();
                poseStack.translate(bx - cam.x + GHOST_INSET, by - cam.y + GHOST_INSET, bz - cam.z + GHOST_INSET);
                poseStack.scale(GHOST_SCALE, GHOST_SCALE, GHOST_SCALE);
                collector.submitMovingBlock(poseStack, rs, 0);
                poseStack.popPose();
            }
        });
    }

    /** Roda uma vez por frame, na fase de extração. */
    private static void extract(LevelExtractionContext context) {
        GhostState.refresh(Minecraft.getInstance());
        if (!GhostState.showOutline) return;

        GhostState.Snapshot snap = GhostState.snapshot;
        for (int i = 0; i < snap.drawn(); i++) {
            double x = snap.xyz()[i * 3];
            double y = snap.xyz()[i * 3 + 1];
            double z = snap.xyz()[i * 3 + 2];
            AABB box = new AABB(x + INSET, y + INSET, z + INSET,
                                x + 1 - INSET, y + 1 - INSET, z + 1 - INSET);
            int color = snap.kind()[i] == 0 ? COLOR_MISSING : COLOR_WRONG;
            Gizmos.cuboid(box, GizmoStyle.stroke(color));
        }
    }
}
