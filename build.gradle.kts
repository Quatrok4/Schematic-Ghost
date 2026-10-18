plugins {
    // 26.x não tem ofuscação: plugin "não remapeado"
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    `maven-publish`
}

fun prop(vararg names: String, default: String): String {
    for (n in names) {
        val v = project.findProperty(n)
        if (v != null) return v.toString()
    }
    return default
}

version = prop("mod_version", "version", default = "0.1.0")
group = prop("maven_group", "group", default = "dev.schematicghost")

base {
    archivesName.set(prop("archives_base_name", "archivesBaseName", default = "schematic-ghost"))
}

dependencies {
    minecraft("com.mojang:minecraft:${prop("minecraft_version", default = "26.3")}")
    implementation("net.fabricmc:fabric-loader:${prop("loader_version", "fabric_loader_version", default = "0.19.5")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_version", "fabric_api_version", default = "0.160.5+26.3")}")
}

tasks.processResources {
    // valor calculado na configuração (compatível com o configuration cache)
    val modVersion: String = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to modVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
