package kz.verity.client;

import kz.verity.VerityEntity;
import kz.verity.VerityMod;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Настоящий шар: вместо кубов Minecraft рисуем UV-сферу.
 * Лицо нарисовано прямо на текстуре в центре (u=0.5, v=0.5), то есть спереди.
 */
public class VerityRenderer extends EntityRenderer<VerityEntity> {
    private static final Identifier TEX =
            Identifier.of(VerityMod.MOD_ID, "textures/entity/verity.png");
    private static final Identifier TEX_TALK =
            Identifier.of(VerityMod.MOD_ID, "textures/entity/verity_talk.png");

    private static final int LAT = 20;
    private static final int LON = 32;
    private static final float RADIUS = 0.45f;

    public VerityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.4f;
    }

    @Override
    public Identifier getTexture(VerityEntity entity) {
        return entity.isTalking() ? TEX_TALK : TEX;
    }

    @Override
    public void render(VerityEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();

        float bob = MathHelper.sin((entity.age + tickDelta) * 0.12f) * 0.03f;
        matrices.translate(0.0, RADIUS + 0.05 + bob, 0.0);

        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevHeadYaw, entity.headYaw);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-headYaw));

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(getTexture(entity)));
        MatrixStack.Entry entry = matrices.peek();

        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                vertex(vc, entry, i, j, light);
                vertex(vc, entry, i + 1, j, light);
                vertex(vc, entry, i + 1, j + 1, light);
                vertex(vc, entry, i, j + 1, light);
            }
        }

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, int i, int j, int light) {
        float theta = (float) (Math.PI * i / LAT);                    // 0 = верх, PI = низ
        float phi = (float) (2 * Math.PI * j / LON - Math.PI);        // 0 = вперёд (+Z)
        float nx = MathHelper.sin(theta) * MathHelper.sin(phi);
        float ny = MathHelper.cos(theta);
        float nz = MathHelper.sin(theta) * MathHelper.cos(phi);
        vc.vertex(entry.getPositionMatrix(), nx * RADIUS, ny * RADIUS, nz * RADIUS)
                .color(255, 255, 255, 255)
                .texture((float) j / LON, (float) i / LAT)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, nx, ny, nz);
    }
}
