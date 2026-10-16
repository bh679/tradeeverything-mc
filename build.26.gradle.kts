// Stonecutter central script for the common code on the unobfuscated 26.x line: no
// mappings and no remapping, via the loom-no-remap variant. Otherwise mirrors
// build.gradle.kts.

plugins {
    id("dev.architectury.loom-no-remap")
    id("architectury-plugin")
}

val minecraft = stonecutter.current.version
val modVersion = property("mod_version") as String

version = "$modVersion+$minecraft"
base { archivesName.set("${property("mod_id")}-common") }

architectury.common(stonecutter.tree.branches.mapNotNull {
    if (stonecutter.current.project !in it) null
    else it.project.findProperty("loom.platform") as String?
})

repositories {
    mavenCentral()
}

dependencies {
    minecraft("net.minecraft:minecraft:$minecraft")
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

apply(from = rootProject.file("gradle/common-shared.gradle.kts"))
