plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
    id("run-hytale")
}

group = "dev.hytalemod"
version = "1.10.3"
description = "JET Item Browser - Browse all items with search, categories, and filtering"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Hytale Server API (provided by server at runtime)
    compileOnly(files("libs/hytale-server.jar"))

    // GSON is provided by Hytale at runtime
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("org.jetbrains:annotations:24.1.0")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(files("libs/hytale-server.jar"))
    testImplementation("com.google.code.gson:gson:2.10.1")
}

// Configure server testing
runHytale {
    jarUrl = "https://fill-data.papermc.io/v1/objects/d5f47f6393aa647759f101f02231fa8200e5bccd36081a3ee8b6a5fd96739057/paper-1.21.10-115.jar"
}

tasks {
    // Configure Java compilation
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
    }
    
    // Configure resource processing
    processResources {
        filteringCharset = Charsets.UTF_8.name()

        // Auto-detect Hytale server version from installed jar
        val userHome = System.getProperty("user.home")
        val serverJar = file("$userHome/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar")
        @Suppress("UNCHECKED_CAST")
        val serverVersion: String = if (serverJar.exists()) {
            val cls = Class.forName("java.util.jar.JarFile")
            val jar = cls.getConstructor(Class.forName("java.io.File")).newInstance(serverJar)
            val manifest = cls.getMethod("getManifest").invoke(jar)
            val attrs = manifest?.javaClass?.getMethod("getMainAttributes")?.invoke(manifest)
            val ver = attrs?.javaClass?.getMethod("getValue", String::class.java)?.invoke(attrs, "Implementation-Version") as? String ?: "*"
            cls.getMethod("close").invoke(jar)
            ver
        } else {
            "*"
        }

        val props = mapOf(
            "group" to project.group,
            "version" to project.version,
            "description" to project.description,
            "serverVersion" to serverVersion
        )
        inputs.properties(props)

        filesMatching("manifest.json") {
            expand(props)
        }
    }
    
    // Configure ShadowJar
    shadowJar {
        archiveBaseName.set("JET")
        archiveVersion.set("1.10.3")
        archiveClassifier.set("")
    }
    
    // Configure tests
    test {
        useJUnitPlatform()
    }
    
    // Make build depend on shadowJar
    build {
        dependsOn(shadowJar)
    }

    // Copy JAR to Hytale Mods folder after successful build
    register<Copy>("deployToMods") {
        dependsOn(shadowJar)
        from(shadowJar.get().archiveFile)

        // Dynamic path that works on any machine
        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val modsPath = when {
            os.contains("win") -> "$userHome/AppData/Roaming/Hytale/UserData/Mods"
            os.contains("mac") -> "$userHome/Library/Application Support/Hytale/UserData/Mods"
            else -> "$userHome/.local/share/Hytale/UserData/Mods" // Linux
        }
        into(modsPath)
    }

    build {
        finalizedBy("deployToMods")
    }
}

// Configure Java toolchain
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
