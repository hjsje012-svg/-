package kz.verity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class VerityMod implements ModInitializer {
    public static final String MOD_ID = "verity";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final EntityType<VerityEntity> VERITY = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "verity"),
            EntityType.Builder.create(VerityEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.9f, 0.9f)
                    .maxTrackingRange(10)
                    .build("verity"));

    public static final Item VERITY_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "verity_spawn_egg"),
            new SpawnEggItem(VERITY, 0x5FCFC4, 0xFFFFFF, new Item.Settings()));

    public static final Item VERITY_BOX = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "verity_box"),
            new VerityBoxItem(new Item.Settings().maxCount(16)));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(VERITY, VerityEntity.createVerityAttributes());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> e.add(VERITY_SPAWN_EGG));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> { e.add(VERITY_BOX); e.add(VERITY_SPAWN_EGG); });
        VerityConfig.load();

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                handleChat(sender, message.getSignedContent()));
    }

    private static void handleChat(ServerPlayerEntity player, String text) {
        List<VerityEntity> near = player.getServerWorld().getEntitiesByClass(
                VerityEntity.class, player.getBoundingBox().expand(24), e -> true);
        if (near.isEmpty()) return;

        String lower = text.toLowerCase();
        boolean addressed = lower.startsWith("верити") || lower.startsWith("verity");
        if (!addressed && !VerityConfig.get().answerAllNearby) return;

        String question = text;
        if (addressed) {
            question = text.substring(6).replaceFirst("^[\\s,:!\\-]+", "");
            if (question.isBlank()) question = "Привет!";
        }

        VerityEntity verity = near.stream()
                .min((a, b) -> Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player)))
                .get();
        GeminiClient.ask(player, question, verity);
    }
}
