// Stonecutter central script for the common code (src/) on every obfuscated Minecraft
// version (through 1.21.11). Copied once per version into versions/<mc>/. 26.x uses
// build.26.gradle.kts instead — see settings.gradle.kts.

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
}

val minecraft = stonecutter.current.version
val modVersion = property("mod_version") as String

version = "$modVersion+$minecraft"
base { archivesName.set("${property("mod_id")}-common") }

// Which loaders this Minecraft version builds for, asked of Stonecutter's branches.
architectury.common(stonecutter.tree.branches.mapNotNull {
    if (stonecutter.current.project !in it) null
    else it.project.findProperty("loom.platform") as String?
})

loom {
    silentMojangMappingsLicense()
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())
    // Only for the @Environment annotations; no other Fabric Loader class in common code.
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

apply(from = rootProject.file("gradle/common-shared.gradle.kts"))
