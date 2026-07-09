import net.quasarmc.quasar.build.weld.WeldTask

plugins {
    kotlin("jvm")

    id("com.gradleup.shadow") version "9.5.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.pswidersk.python-plugin") version "3.2.17"

    id("weld")
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

    runServer {
        minecraftVersion("26.2")
    }

    processResources {
        dependsOn("copyDataPack")
    }

    register<WeldTask>("weldDataPack") {
        output = layout.buildDirectory.file("weld/data.zip")

        include(layout.projectDirectory.dir("src/main/datapack"))
    }

    register<WeldTask>("weldResourcePack") {
        output = layout.buildDirectory.file("weld/resources.zip")

        include(layout.projectDirectory.dir("src/main/resourcepack"))
    }

    // We can technically skip welding the datapack and copying it into the resources
    // directory and just keep the entire datapack in resources/custom_datapack, but
    // in the future we may need to include something else, so it's nice to keep
    // this here just in case since it doesn't add much overhead.
    register<Copy>("copyDataPack") {
        dependsOn("weldDataPack")

        from(zipTree(layout.buildDirectory.file("weld/data.zip")))
        into("src/generated/resources/datapack/")
    }

    register<Zip>("packageServer") {
        dependsOn(build)
        dependsOn("weldResourcePack")

        from(layout.buildDirectory.file("libs/quasar-all.jar")) {
            into("plugins/")
        }

        from(layout.buildDirectory.file("weld/resources.zip")) {
            rename { "resources.zip" }
        }

        archiveFileName = "quasar-server.zip"
        destinationDirectory = layout.buildDirectory.dir("package")
    }
}
