// Forge (1.20.1 – 1.21.11).

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

val minecraft = stonecutter.current.version
val common = requireNotNull(stonecutter.node.sibling("")?.project) { "No common project for $project" }
evaluationDependsOn(common.path)

base { archivesName.set("${property("mod_id")}-forge") }

architectury {
    platformSetupLoomIde()
    forge()
}

val commonBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
configurations {
    compileClasspath.get().extendsFrom(commonBundle)
    runtimeClasspath.get().extendsFrom(commonBundle)
    named("developmentForge").get().extendsFrom(commonBundle)
}

loom {
    silentMojangMappingsLicense()
    // Forge only loads mixin configs named in the jar's MixinConfigs manifest
    // attribute ([[mixins]] in mods.toml is NeoForge-only); this writes it.
    forge.mixinConfig("tradeeverything.mixins.json")
}

// Architectury Loom's Forge dev launch pulls in net.fabricmc:fabric-log4j-util (a shaded
// terminalconsoleappender) that clashes with Forge's own copy on the module path.
configurations.all {
    exclude(group = "net.fabricmc", module = "fabric-log4j-util")
}

repositories {
    maven("https://maven.minecraftforge.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())
    "forge"("net.minecraftforge:forge:${common.property("forge_version")}")

    commonBundle(project(common.path, "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionForge")) { isTransitive = false }
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier.set("dev-shadow")
    exclude("fabric.mod.json", "architectury.common.json")
}

tasks.remapJar {
    input.set(tasks.shadowJar.flatMap { it.archiveFile })
    dependsOn(tasks.shadowJar)
    archiveClassifier.set(null as String?)
}

tasks.jar {
    archiveClassifier.set("dev")
}

extra["minecraft"] = minecraft
extra["common"] = common
extra["finalJarFile"] = tasks.remapJar.flatMap { it.archiveFile }
apply(from = rootProject.file("gradle/loader-shared.gradle.kts"))
