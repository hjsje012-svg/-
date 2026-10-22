package kz.verity.client;

import kz.verity.VerityMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class VerityClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(VerityMod.VERITY, VerityRenderer::new);
    }
}
