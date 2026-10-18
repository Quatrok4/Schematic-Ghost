package dev.schematicghost.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.Set;

/**
 * Desenha o modelo real de cada bloco que falta COM transparência.
 *
 * O jogo não oferece alpha para blocos "em movimento", então aqui escrevemos nós mesmos as faces
 * do modelo (BlockStateModel -> partes -> quads) num tipo de render translúcido, com a cor
 * ARGB (alpha) definida em um QuadInstance.
 */
final class TranslucentGhost {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float SCALE = 0.998f; // quase 1: evita faces no mesmo plano que blocos vizinhos
    private static final float INSET = (1f - SCALE) / 2f;

    // null = quads sem face de corte; depois as 6 direções
    private static final Direction[] FACES = {
            null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private TranslucentGhost() {}

    /** Chama um método do tipo "preenche esta lista" e devolve a lista (o tipo dos itens é deduzido). */
    private static <T> List<T> collect(BiConsumer<RandomSource, List<T>> filler, RandomSource random) {
        List<T> list = new ArrayList<>();
        filler.accept(random, list);
        return list;
    }

    static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 cam,
                       GhostState.Snapshot snap, int alphaPercent) {
        var modelSet = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        RenderType renderType = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);

        int a = Math.max(16, Math.min(255, alphaPercent * 255 / 100));
        final int color = (a << 24) | 0xFFFFFF;

        // posições que têm fantasma: escondemos a face entre dois fantasmas vizinhos
        Set<Long> ghosts = new HashSet<>();
        for (int i = 0; i < snap.drawn(); i++) {
            if (snap.kind()[i] == 0) {
                ghosts.add(BlockPos.asLong(snap.xyz()[i * 3], snap.xyz()[i * 3 + 1], snap.xyz()[i * 3 + 2]));
            }
        }

        for (int i = 0; i < snap.drawn(); i++) {
            if (snap.kind()[i] != 0) continue;
            BlockState state = BlockResolver.resolve(snap.states()[i]);
            if (state == null || state.isAir()) continue;

            final int bx = snap.xyz()[i * 3], by = snap.xyz()[i * 3 + 1], bz = snap.xyz()[i * 3 + 2];
            var model = modelSet.get(state);

            poseStack.pushPose();
            poseStack.translate(bx - cam.x + INSET, by - cam.y + INSET, bz - cam.z + INSET);
            poseStack.scale(SCALE, SCALE, SCALE);

            collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
                QuadInstance qi = new QuadInstance();
                qi.setColor(color);
                qi.setLightCoords(FULL_BRIGHT);
                qi.setOverlayCoords(OverlayTexture.NO_OVERLAY);

                RandomSource random = RandomSource.create(42L);
                var parts = collect(model::collectParts, random);
                for (var part : parts) {
                    for (Direction face : FACES) {
                        if (face != null && ghosts.contains(BlockPos.asLong(
                                bx + face.getStepX(), by + face.getStepY(), bz + face.getStepZ()))) {
                            continue;
                        }
                        for (var quad : part.getQuads(face)) {
                            consumer.putBakedQuad(pose, quad, qi);
                        }
                    }
                }
            });

            poseStack.popPose();
        }
    }
}
