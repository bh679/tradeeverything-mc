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

// Builds every Minecraft version x loader combo and copies the distributable jars to
// build/release/ (the release workflow uploads exactly that directory).
tasks.register("chiseledBuild") {
    group = "project"
    description = "Builds every Minecraft version x loader variant."
    dependsOn(stonecutter.tasks.named("build").map { it.values })
}

tasks.register("collectReleaseJars") {
    group = "project"
    description = "Builds every variant and collects distributable jars into build/release/."
    dependsOn(stonecutter.tasks.named("collectJar") { branch.id.isNotEmpty() }.map { it.values })
}
