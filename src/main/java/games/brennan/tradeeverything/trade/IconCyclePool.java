package games.brennan.tradeeverything.trade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The pool of items the "Trade Anything" placeholder icon cycles through:
 * everything in the item registry that a player could actually obtain in
 * survival.
 *
 * <p>Vanilla has no "obtainable" flag, so the filter is negative — drop
 * feature-disabled items, spawn eggs, and an explicit deny set of
 * creative/operator-only blocks. Modded items pass: a modded item in a modded
 * world is obtainable by definition.</p>
 *
 * <p>The pool is shuffled once with a fixed seed, so the sequence looks random
 * but is stable for the lifetime of the game — no per-villager state to keep.</p>
 */
public final class IconCyclePool {

    private static final long SHUFFLE_SEED = 0x7E7EC0DEL;

    /** Creative/operator-only vanilla items — never obtainable in survival. */
    private static final Set<String> DENIED = Set.of(
        "minecraft:barrier",
        "minecraft:light",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:jigsaw",
        "minecraft:command_block",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block",
        "minecraft:command_block_minecart",
        "minecraft:debug_stick",
        "minecraft:knowledge_book",
        "minecraft:bedrock",
        "minecraft:end_portal_frame",
        "minecraft:spawner",
        "minecraft:trial_spawner",
        "minecraft:vault",
        "minecraft:budding_amethyst",
        "minecraft:reinforced_deepslate",
        "minecraft:petrified_oak_slab",
        "minecraft:farmland",
        "minecraft:dirt_path",
        "minecraft:chorus_plant"
    );

    private static volatile List<Item> pool;

    private IconCyclePool() {}

    /**
     * The item shown for cycle {@code step}, offset by {@code salt} (the
     * villager id, so two villagers are never in lockstep). Walks forward past
     * anything in {@code excluded}; falls back to a chest if the pool is empty
     * or every candidate is excluded.
     */
    public static Item at(long step, int salt, Set<Item> excluded) {
        List<Item> items = pool();
        if (items.isEmpty()) return Items.CHEST;
        int start = (int) Math.floorMod(step + salt, items.size());
        for (int offset = 0; offset < items.size(); offset++) {
            Item candidate = items.get((start + offset) % items.size());
            if (!excluded.contains(candidate)) return candidate;
        }
        return Items.CHEST;
    }

    private static List<Item> pool() {
        List<Item> local = pool;
        if (local != null) return local;
        synchronized (IconCyclePool.class) {
            if (pool == null) pool = build();
            return pool;
        }
    }

    private static List<Item> build() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (isObtainable(item)) items.add(item);
        }
        Collections.shuffle(items, new Random(SHUFFLE_SEED));
        return List.copyOf(items);
    }

    private static boolean isObtainable(Item item) {
        if (item == Items.AIR) return false;
        if (!item.isEnabled(FeatureFlags.VANILLA_SET)) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return false;
        if (id.getPath().endsWith("_spawn_egg")) return false;
        return !DENIED.contains(id.toString());
    }
}
