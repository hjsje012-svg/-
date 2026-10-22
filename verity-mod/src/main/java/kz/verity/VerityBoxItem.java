package kz.verity;

import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Коробка Верити: поставь на землю, и Верити выскочит из неё. */
public class VerityBoxItem extends Item {
    public VerityBoxItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (!(world instanceof ServerWorld sw)) return ActionResult.SUCCESS;

        BlockPos pos = ctx.getBlockPos().offset(ctx.getSide());
        VerityEntity v = VerityMod.VERITY.spawn(sw, pos, SpawnReason.SPAWN_ITEM_USE);
        if (v == null) return ActionResult.FAIL;

        sw.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                25, 0.3, 0.3, 0.3, 0.03);
        sw.playSound(null, pos, SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.NEUTRAL, 1.0f, 1.2f);
        v.startTalking(60);
        sw.getServer().getPlayerManager().broadcast(
                Text.literal("<Верити> ").formatted(Formatting.AQUA)
                        .append(Text.literal("Ура, я выбралась из коробки! Зови меня: «Верити, ...»")), false);

        if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) ctx.getStack().decrement(1);
        return ActionResult.SUCCESS;
    }
}
