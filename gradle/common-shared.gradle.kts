// Shared by build.gradle.kts and build.26.gradle.kts (the per-version common project).

val javaVersion = JavaVersion.toVersion(property("java_version") as String)

configure<JavaPluginExtension> {
    withSourcesJar()
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.majorVersion.toInt()))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val resourceProps = mapOf(
    "mixin_compat_level" to property("mixin_compat_level") as String,
    "pack_format" to property("pack_format") as String,
)
tasks.named<ProcessResources>("processResources") {
    val props = resourceProps
    inputs.properties(props)
    filesMatching(listOf("tradeeverything.mixins.json", "pack.mcmeta")) { expand(props) }
}

