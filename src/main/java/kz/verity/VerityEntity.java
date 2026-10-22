package kz.verity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/** Круглая помощница. Сама ничего не решает: отвечает через GeminiClient. */
public class VerityEntity extends PathAwareEntity {
    private static final TrackedData<Boolean> TALKING =
            DataTracker.registerData(VerityEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private int talkTicks = 0;

    public VerityEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createVerityAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        goalSelector.add(2, new WanderAroundFarGoal(this, 0.8));
        goalSelector.add(3, new LookAroundGoal(this));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TALKING, false);
    }

    public boolean isTalking() {
        return dataTracker.get(TALKING);
    }

    public void startTalking(int ticks) {
        talkTicks = ticks;
        dataTracker.set(TALKING, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && talkTicks > 0 && --talkTicks == 0) {
            dataTracker.set(TALKING, false);
        }
    }
}
