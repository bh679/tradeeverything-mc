plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.17-SNAPSHOT" apply false
    // Plain architectury-loom can't resolve official mappings for unobfuscated 26.x
    // (architectury/architectury-loom#328); the no-remap variant is the workaround.
    id("dev.architectury.loom-no-remap") version "1.17-SNAPSHOT" apply false
    id("architectury-plugin") version "3.5-SNAPSHOT" apply false
    id("com.gradleup.shadow") version "9.4.3" apply false
}

// The version opened by default in the IDE / by a bare `./gradlew build`.
stonecutter active "1.21.1" /* [SC] DO NOT EDIT */

// Pure renames between Minecraft versions, applied to the source as it is copied
// into each version (the source itself is written against the vcsVersion, 1.21.1).
// Anything that changes behaviour or signatures uses a //? if block instead.
stonecutter parameters {
    val modern = current.parsed >= "1.21.11"
    val year = current.parsed >= "26"
    val legacy = current.parsed < "1.20.5"
    replacements {
        // 1.20.1 has no data components; the NBT-era equivalents.
        string(legacy) { replace("isSameItemSameComponents", "isSameItemSameTags") }
        string(modern) { replace("net.minecraft.world.entity.npc.AbstractVillager", "net.minecraft.world.entity.npc.villager.AbstractVillager") }
        string(modern) { replace("net/minecraft/world/entity/npc/AbstractVillager", "net/minecraft/world/entity/npc/villager/AbstractVillager") }
        string(modern) { replace("net.minecraft.world.entity.npc.Villager;", "net.minecraft.world.entity.npc.villager.Villager;") }
        string(modern) { replace("net.minecraft.world.entity.npc.WanderingTrader", "net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader") }
        string(modern) { replace("ResourceLocation", "Identifier") }
        string(modern) { replace("DataComponentPredicate", "DataComponentExactPredicate") }
        string(modern) { replace("getVillagerData().getLevel()", "getVillagerData().level()") }
        // 26.x renamed GUI rendering to "extraction".
        string(year) { replace("GuiGraphics", "GuiGraphicsExtractor") }
        string(year) { replace("renderButtonArrows", "extractButtonArrows") }
        string(year) { replace("getItemHolder()", "typeHolder()") }
        string(year) { replace("ClickType", "ContainerInput") }
        string(year) { replace("handleInventoryMouseClick", "handleContainerInput") }
    }
}

// Builds every Minecraft version x loader combo and copies the distributable jars to
// build/release/ (the release workflow uploads exactly that directory).
tasks.register("chiseledBuild") {
    group = "project"
    description = "Builds every Minecraft version x loader variant."
    dependsOn(stonecutter.tasks.named("build").map { it.values })
}

tasks.register("chiseledTest") {
    group = "project"
    description = "Runs the common unit tests on every Minecraft version."
    dependsOn(stonecutter.tasks.named("test") { branch.id.isEmpty() }.map { it.values })
}

tasks.register("collectReleaseJars") {
    group = "project"
    description = "Builds every variant and collects distributable jars into build/release/."
    dependsOn(stonecutter.tasks.named("collectJar") { branch.id.isNotEmpty() }.map { it.values })
}
