plugins {
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.139"
    id("idea")
}

val parchment_minecraft_version : String by project
val parchment_mappings_version  : String by project
val minecraft_version           : String by project
val minecraft_version_range     : String by project
val neo_version                 : String by project
val neo_version_range           : String by project
val loader_version_range        : String by project
val mod_id                      : String by project
val mod_name                    : String by project
val mod_license                 : String by project
val mod_version                 : String by project
val mod_group_id                : String by project

tasks.wrapper.configure {
    distributionType = Wrapper.DistributionType.BIN
}

version = mod_version
group = mod_group_id

base {
    archivesName.set("$mod_id-$minecraft_version-$mod_version")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

neoForge {
    version = neo_version

    parchment {
        mappingsVersion = parchment_mappings_version
        minecraftVersion = parchment_minecraft_version
    }

    runs {
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("server") {
            server()
            gameDirectory = file("run-server")
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = file("run-server")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("data") {
            data()
            gameDirectory = file("run-data")
            programArguments.addAll(
                "--mod",
                mod_id,
                "--all",
                "--output",
                file("src/generated/resources/").absolutePath,
                "--existing",
                file("src/main/resources/").absolutePath
            )
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        create(mod_id) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources {
    srcDir("src/generated/resources")
}

val localRuntime: Configuration by configurations.creating
configurations {
    runtimeClasspath {
        extendsFrom(localRuntime)
    }
}

repositories {
    // CurseMaven 仓库，用于获取 Spectrum 等 CurseForge 上托管的模组依赖
    maven { url = uri("https://cfa2.cursemaven.com") }
}

dependencies {
    // Spectrum 模组依赖（通过 CurseMaven 获取，CurseForge 项目 ID: 556967，文件 ID: 8037133 对应 1.11.8-1.21.1-neo）
    implementation("curse.maven:spectrum-556967:8037133")
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("generateModMetadata")
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version"       to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "neo_version"             to neo_version,
        "neo_version_range"       to neo_version_range,
        "loader_version_range"    to loader_version_range,
        "mod_id"                  to mod_id,
        "mod_name"                to mod_name,
        "mod_license"             to mod_license,
        "mod_version"             to mod_version,
    )
    inputs.properties(replaceProperties)

    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}
sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

tasks.compileJava {
    options.encoding = "UTF-8"
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
