package kz.verity;

import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;

import java.util.Optional;

/** Действия, которые Gemini может попросить выполнить. Вызывать только из серверного потока. */
public class Actions {

    public static boolean canAct(ServerPlayerEntity p) {
        VerityConfig c = VerityConfig.get();
        return c.actionsEnabled && (c.allowActionsForAll || p.hasPermissionLevel(2));
    }

    public static String run(ServerPlayerEntity p, String name, JsonObject a) {
        try {
            return switch (name) {
                case "give_item" -> giveItem(p, a);
                case "spawn_mob" -> spawnMob(p, a);
                case "build" -> build(p, a);
                default -> "Не умею действие " + name;
            };
        } catch (Exception e) {
            VerityMod.LOGGER.warn("Ошибка действия {}", name, e);
            return "Не получилось выполнить " + name;
        }
    }

    // ---------- helpers ----------
    private static String str(JsonObject a, String k, String def) {
        return a != null && a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsString() : def;
    }

    private static int num(JsonObject a, String k, int def) {
        try {
            return a != null && a.has(k) ? (int) Math.round(a.get(k).getAsDouble()) : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static Identifier id(String s) {
        s = s.trim().toLowerCase().replace(' ', '_');
        if (!s.contains(":")) s = "minecraft:" + s;
        return Identifier.tryParse(s);
    }

    // ---------- give_item ----------
    private static String giveItem(ServerPlayerEntity p, JsonObject a) {
        Identifier id = id(str(a, "item", ""));
        Optional<Item> opt = id == null ? Optional.empty() : Registries.ITEM.getOrEmpty(id);
        if (opt.isEmpty() || opt.get() == Items.AIR) return "Не знаю такого предмета: " + str(a, "item", "?");
        Item item = opt.get();
        int total = MathHelper.clamp(num(a, "count", 1), 1, VerityConfig.get().maxItems);
        int given = total;
        while (total > 0) {
            ItemStack stack = new ItemStack(item, 1);
            stack.setCount(Math.min(total, stack.getMaxCount()));
            total -= stack.getCount();
            if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
        }
        return "Выдала: " + given + " × " + id.getPath();
    }

    // ---------- spawn_mob ----------
    private static String spawnMob(ServerPlayerEntity p, JsonObject a) {
        Identifier id = id(str(a, "mob", ""));
        Optional<EntityType<?>> opt = id == null ? Optional.empty() : Registries.ENTITY_TYPE.getOrEmpty(id);
        if (opt.isEmpty()) return "Не знаю такого моба: " + str(a, "mob", "?");
        EntityType<?> type = opt.get();
        if (!type.isSummonable() || VerityConfig.get().bannedMobs.contains(id.toString()))
            return "Этого моба призывать нельзя: " + id.getPath();
        int n = MathHelper.clamp(num(a, "count", 1), 1, VerityConfig.get().maxMobs);
        ServerWorld w = p.getServerWorld();
        Vec3i f = p.getHorizontalFacing().getVector();
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            BlockPos pos = p.getBlockPos().add(f.getX() * 3 + (i % 3) - 1, 0, f.getZ() * 3 + (i / 3));
            if (type.spawn(w, pos, SpawnReason.COMMAND) != null) spawned++;
        }
        return "Призвала: " + spawned + " × " + id.getPath();
    }

    // ---------- build ----------
    private static class Builder {
        final ServerWorld world;
        final ServerPlayerEntity player;
        final BlockPos base;
        final Vec3i f, r;
        int count = 0;
        final int cap = VerityConfig.get().maxBlocks;

        Builder(ServerPlayerEntity p) {
            this.player = p;
            this.world = p.getServerWorld();
            Direction d = p.getHorizontalFacing();
            this.f = d.getVector();
            this.r = d.rotateYClockwise().getVector();
            this.base = p.getBlockPos().add(f.getX() * 3, 0, f.getZ() * 3);
        }

        void set(int x, int y, int z, BlockState st) {
            if (count >= cap) return;
            BlockPos pos = base.add(r.getX() * x + f.getX() * z, y, r.getZ() * x + f.getZ() * z);
            if (!world.canPlayerModifyAt(player, pos)) return;
            BlockState old = world.getBlockState(pos);
            if (old.getHardness(world, pos) < 0) return; // не трогаем бедрок и т.п.
            world.setBlockState(pos, st, Block.NOTIFY_ALL);
            count++;
        }
    }

    private static String build(ServerPlayerEntity p, JsonObject a) {
        VerityConfig cfg = VerityConfig.get();
        String structure = str(a, "structure", "house").toLowerCase();
        Identifier mid = id(str(a, "material", "oak_planks"));
        Optional<Block> mo = mid == null ? Optional.empty() : Registries.BLOCK.getOrEmpty(mid);
        if (mo.isEmpty() || mo.get() == Blocks.AIR) return "Не знаю такого блока: " + str(a, "material", "?");
        BlockState mat = mo.get().getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();

        int max = cfg.maxBuildSize;
        int w = MathHelper.clamp(num(a, "width", 7), 3, max);
        int h = MathHelper.clamp(num(a, "height", 4), 1, max);
        int d = MathHelper.clamp(num(a, "depth", 7), 3, max);

        Builder b = new Builder(p);

        switch (structure) {
            case "house", "tower" -> {
                if (structure.equals("tower")) { w = 5; d = 5; h = Math.max(h, 6); }
                if (structure.equals("house") && h < 3) h = 3;
                int x0 = -(w / 2), x1 = x0 + w - 1;
                for (int x = x0; x <= x1; x++) for (int z = 0; z < d; z++) b.set(x, -1, z, mat); // пол
                for (int y = 0; y < h; y++)
                    for (int x = x0; x <= x1; x++)
                        for (int z = 0; z < d; z++) {
                            boolean edge = x == x0 || x == x1 || z == 0 || z == d - 1;
                            b.set(x, y, z, edge ? mat : air);
                        }
                for (int x = x0; x <= x1; x++) for (int z = 0; z < d; z++) b.set(x, h, z, mat); // крыша
                b.set(0, 0, 0, air);                      // дверной проём
                b.set(0, 1, 0, air);
                for (int y = 1; y < h; y += 4) {         // окна по бокам
                    b.set(x0, y, d / 2, glass);
                    b.set(x1, y, d / 2, glass);
                }
                b.set(0, 0, d / 2, Blocks.TORCH.getDefaultState());
            }
            case "wall" -> {
                int x0 = -(w / 2);
                for (int y = 0; y < h; y++) for (int x = x0; x < x0 + w; x++) b.set(x, y, 0, mat);
            }
            case "platform" -> {
                int x0 = -(w / 2);
                for (int x = x0; x < x0 + w; x++) for (int z = 0; z < d; z++) b.set(x, -1, z, mat);
            }
            case "pyramid" -> {
                int x0 = -(w / 2), x1 = x0 + w - 1;
                for (int k = 0; x0 + k <= x1 - k && k < d - k; k++)
                    for (int x = x0 + k; x <= x1 - k; x++)
                        for (int z = k; z <= d - 1 - k; z++) b.set(x, k, z, mat);
            }
            default -> {
                return "Не умею строить «" + structure + "». Умею: house, tower, wall, platform, pyramid.";
            }
        }
        String note = b.count >= cfg.maxBlocks ? " (упёрлась в лимит блоков)" : "";
        return "Построила " + structure + " из " + mid.getPath() + ": " + b.count + " блоков" + note;
    }
}
