val buildMetaProperties = mapOf(
    "version"   to "3.0.0.1",
    "commit"    to providers.exec { commandLine("git", "describe", "--always", "--tags", "--abbrev=10", "--dirty") }.standardOutput.asText.get().trim(),
    "sourceURL" to "https://github.com/Quasar-Developers/Quasar/"
)

plugins {
    kotlin("jvm")

    id("com.gradleup.shadow") version "9.5.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    paperweight.paperDevBundle("26.2.build.+")
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin/")
            srcDir(layout.buildDirectory.dir("generated/processTemplates/kotlin"))
        }

        resources {
            srcDir("src/main/resources/")
            srcDir(layout.buildDirectory.dir("generated/copyDataPack/resources/"))
        }
    }
}

kotlin {
    jvmToolchain(25)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    minecraftVersion("26.2")

    // if running on a jvm with dcevm support, enable it
    if (rootProject.ext["jvmSupportsDCEVM"] as Boolean) {
        jvmArgs("-XX:+AllowEnhancedClassRedefinition")
    }
}

tasks.processResources {
    dependsOn(copyDataPack)

    duplicatesStrategy = DuplicatesStrategy.WARN

    filesMatching("paper-plugin.yml") {
        expand(buildMetaProperties)
    }
}

val processTemplates = tasks.register<Copy>("processTemplates") {
    from("src/template")
    into(layout.buildDirectory.dir("generated/processTemplates"))

    expand(buildMetaProperties) {
        escapeBackslash = false
    }

    outputs.upToDateWhen { false }
}

tasks.compileKotlin {
    dependsOn(processTemplates)
}

// The data pack is kept out of the resource folder so that if we need to we can
// use something like weld in the future
val copyDataPack = tasks.register<Copy>("copyDataPack") {
    from("src/main/datapack/")

    into(layout.buildDirectory.dir("generated/copyDataPack/resources/datapack"))
}

val packageResourcePack = tasks.register<Zip>("packageResourcePack") {
    from(file("src/main/resourcepack/"))

    archiveFileName = "resources.zip"
}

val packageServer = tasks.register<Zip>("packageServer") {
    from(tasks.shadowJar) {
        into("plugins/")
    }

    from(packageResourcePack) {
        rename { "resources.zip" }
    }

    archiveFileName = "quasar-server.zip"
    destinationDirectory = layout.buildDirectory.dir("package")
}
