// Stonecutter turns the root project (common code, src/) and the fabric/forge/neoforge
// loader projects into one copy per Minecraft version under versions/<mc>/ and
// <loader>/versions/<mc>/. The loader axis is the classic Architectury split.
// Structure adapted from github.com/FusionMods/stonecutter-architectury-template.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.architectury.dev/") { name = "Architectury" }
        maven("https://maven.minecraftforge.net/") { name = "Forge" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForge" }
        maven("https://maven.kikugie.dev/releases") { name = "Stonecutter" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
    // 1.20.1 needs Java 17, 26.x needs Java 25 — let Gradle fetch whichever JDK a node needs.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "tradeeverything-mc"

// 26.x ships unobfuscated, so it builds through the no-remap Loom variant (build.26.gradle.kts).
val modern = arrayOf("26.3")

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true

    create(rootProject) {
        versions("1.20.1", "1.21.1", "1.21.11")
        versions(*modern).buildscript("build.26.gradle.kts")

        branch("fabric") {
            versions("1.20.1", "1.21.1", "1.21.11")
            versions(*modern).buildscript("build.26.gradle.kts")
        }
        // NeoForge 1.20.1 is internally Forge; the forge jar covers it.
        branch("neoforge") {
            versions("1.21.1", "1.21.11")
            versions(*modern).buildscript("build.26.gradle.kts")
        }
        branch("forge") {
            versions("1.20.1", "1.21.1", "1.21.11")
        }

        vcsVersion = "1.21.1"
    }
}
