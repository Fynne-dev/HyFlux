import java.io.File

/**
 * Finds HytaleServer.jar. It is a compileOnly dependency: the server provides it at runtime,
 * so it is never bundled into our jars.
 */
fun resolveHytaleServerJar(): File {
    val explicit = (findProperty("hytaleServerJar") as String?) ?: System.getenv("HYTALE_SERVER_JAR")
    if (explicit != null) return file(explicit)

    val home = System.getProperty("user.home")
    val candidates = listOfNotNull(
        System.getenv("APPDATA")?.let { "$it/Hytale/install/release/package/game/latest/Server/HytaleServer.jar" },
        "$home/Library/Application Support/Hytale/install/release/package/game/latest/Server/HytaleServer.jar",
        "$home/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Server/HytaleServer.jar",
        "$home/.local/share/Hytale/install/release/package/game/latest/Server/HytaleServer.jar",
    ).map(::File)
    return candidates.firstOrNull { it.isFile } ?: candidates.first()
}

val hytaleServerJar = resolveHytaleServerJar()
extra["hytaleServerJar"] = hytaleServerJar

subprojects {
    apply(plugin = "java-library")

    group = "dev.hyflux"
    version = rootProject.property("hyfluxVersion") as String

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        // Hytale's server runs on Java 25, so emit Java 25 bytecode whatever JDK builds it.
        options.release.set(25)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing,-classfile", "-parameters"))
    }

    dependencies {
        "compileOnly"(files(hytaleServerJar))
    }

    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.named("compileJava") {
        doFirst {
            if (!hytaleServerJar.isFile) {
                throw GradleException(
                    "HytaleServer.jar not found at $hytaleServerJar. " +
                        "Pass -PhytaleServerJar=<path> or set HYTALE_SERVER_JAR."
                )
            }
        }
    }
}
