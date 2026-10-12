plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("HyFlux")
}

java {
    withJavadocJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("manifest.json") { expand(props) }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "HyFlux",
            "Implementation-Version" to project.version,
        )
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        windowTitle = "HyFlux ${project.version} API"
        docTitle = "HyFlux ${project.version} API"
    }
    // Only the public API is documented; internals and the plugin bootstrap are not.
    include("dev/hyflux/api/**")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "hyflux"
            from(components["java"])
            pom {
                name.set("HyFlux")
                description.set("Energy network API for Hytale server plugins, inspired by Redstone Flux.")
            }
        }
    }
}
