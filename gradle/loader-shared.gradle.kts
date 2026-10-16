// Shared by every loader build script (fabric/forge/neoforge, remap and no-remap).
// Expects `extra["minecraft"]` (the MC version), `extra["common"]` (the sibling common Project) and `extra["finalJarFile"]`
// (the distributable jar file provider) to be set first.

val common = extra["common"] as Project
val minecraft = extra["minecraft"] as String
val modVersion = property("mod_version") as String
val isPrimary = minecraft == property("primary_minecraft_version")

// Primary MC version keeps the flat jar name Dungeon Train depends on.
version = if (isPrimary) modVersion else "$modVersion+mc$minecraft"

val javaVersion = JavaVersion.toVersion(common.property("java_version") as String)
configure<JavaPluginExtension> {
    withSourcesJar()
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.majorVersion.toInt()))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

val keys = listOf(
    "fabric_loader_version", "fabric_minecraft_dep", "java_version",
    "forge_loader_range", "neoforge_version", "minecraft_range",
)
val resourceProps = mutableMapOf(
    "version" to project.version.toString(),
    "mod_name" to property("mod_name") as String,
    "mod_description" to property("mod_description") as String,
    "mod_authors" to property("mod_authors") as String,
    "mod_license" to property("mod_license") as String,
)
keys.forEach { k -> common.findProperty(k)?.let { resourceProps[k] = it.toString() } }
tasks.named<ProcessResources>("processResources") {
    val props = resourceProps
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")) {
        expand(props)
    }
}

// A Provider<RegularFile> of the distributable (passed untyped: the loader's jar task
// classes live in the plugin classloader, not this script's).
val finalJarFile = extra["finalJarFile"]!!

tasks.register<Copy>("collectJar") {
    group = "versioned"
    description = "Copies this variant's distributable jar into the root build/release/."
    from(finalJarFile)
    into(rootProject.layout.buildDirectory.dir("release"))
}
