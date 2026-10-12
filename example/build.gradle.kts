plugins {
    `java-library`
}

base {
    archivesName.set("HyFluxExample")
}

dependencies {
    // HyFlux is provided at runtime by the HyFlux plugin jar in the Mods folder,
    // so it is compileOnly here, exactly like HytaleServer.jar.
    compileOnly(project(":hyflux"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("manifest.json") { expand(props) }
}
