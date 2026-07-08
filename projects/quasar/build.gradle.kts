import com.pswidersk.gradle.python.VenvTask

plugins {
    kotlin("jvm") version "2.4.0"

    id("com.gradleup.shadow") version "9.5.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.pswidersk.python-plugin") version "3.2.17"
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin/")
            srcDir("src/generated/kotlin/")
        }

        resources {
            srcDir("src/main/generated/")
            srcDir("src/generated/resources/")
        }
    }
}

kotlin {
    jvmToolchain(25)
}

pythonPlugin {
    pythonVersion = "3.10"
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    register("package") {
        dependsOn("packageServer")
    }

    runServer {
        minecraftVersion("26.2")
    }

    processResources {
        dependsOn("copyDataPack")
    }

    // TODO(XWASHERE): These always run, this can probably be solved with a gradle plugin.
    register<VenvTask>("configurePython") {
        venvExec = "pip"
        args = listOf("install", "smithed")
    }

    register<VenvTask>("weldDataPack") {
        dependsOn("configurePython")

        venvExec = "weld"
        args = listOf("--dir",  layout.buildDirectory.dir("weld/data").get().toString(),
                      "--name", "main.zip",
                      "src/main/datapack")
    }

    register<VenvTask>("weldResourcePack") {
        dependsOn("configurePython")

        venvExec = "weld";
        args = listOf("--dir",  layout.buildDirectory.dir("weld/resources").get().toString(),
                      "--name", "main.zip",
                      "src/main/resourcepack")
    }

    // We can technically skip welding the datapack and copying it into the resources
    // directory and just keep the entire datapack in resources/custom_datapack, but
    // in the future we may need to include something else, so it's nice to keep
    // this here just in case since it doesn't add much overhead.
    register<Copy>("copyDataPack") {
        dependsOn("weldDataPack")

        from(zipTree(layout.buildDirectory.file("weld/data/main.zip")))
        into("src/generated/resources/datapack/")
    }

    register<Zip>("packageServer") {
        dependsOn(build)
        dependsOn("weldResourcePack")

        from(layout.buildDirectory.file("libs/quasar.jar")) {
            into("plugins/")
        }

        from(layout.buildDirectory.file("weld/resources/main.zip")) {
            rename { "resources.zip" }
        }

        archiveFileName = "quasar-server.zip"
        destinationDirectory = layout.buildDirectory.dir("package")
    }
}
