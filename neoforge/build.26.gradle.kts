// NeoForge on the unobfuscated 26.x line: loom-no-remap, no mappings, and the
// shadow jar is the distributable (nothing to remap). Otherwise mirrors build.gradle.kts.

plugins {
    id("dev.architectury.loom-no-remap")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

val minecraft = stonecutter.current.version
val common = requireNotNull(stonecutter.node.sibling("")?.project) { "No common project for $project" }
evaluationDependsOn(common.path)

base { archivesName.set("${property("mod_id")}-neoforge") }

architectury {
    platformSetupLoomIde()
    neoForge()
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
    named("developmentNeoForge").get().extendsFrom(commonBundle)
}

repositories {
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    minecraft("net.minecraft:minecraft:$minecraft")
    "neoForge"("net.neoforged:neoforge:${common.property("neoforge_version")}")

    commonBundle(project(common.path)) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionNeoForge")) { isTransitive = false }
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier.set("")
    exclude("fabric.mod.json", "architectury.common.json")
}

tasks.jar {
    archiveClassifier.set("dev")
}

extra["minecraft"] = minecraft
extra["common"] = common
extra["finalJarFile"] = tasks.shadowJar.flatMap { it.archiveFile }
apply(from = rootProject.file("gradle/loader-shared.gradle.kts"))
