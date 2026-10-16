// Fabric, obfuscated Minecraft (through 1.21.11). 26.x: fabric/build.26.gradle.kts.

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

val minecraft = stonecutter.current.version
val common = requireNotNull(stonecutter.node.sibling("")?.project) { "No common project for $project" }
evaluationDependsOn(common.path)

base { archivesName.set("${property("mod_id")}-fabric") }

architectury {
    platformSetupLoomIde()
    fabric()
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
    named("developmentFabric").get().extendsFrom(commonBundle)
}

loom {
    silentMojangMappingsLicense()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${common.property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${common.property("fabric_api_version")}")

    commonBundle(project(common.path, "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionFabric")) { isTransitive = false }
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier.set("dev-shadow")
    exclude("architectury.common.json")
}

tasks.remapJar {
    injectAccessWidener.set(true)
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
