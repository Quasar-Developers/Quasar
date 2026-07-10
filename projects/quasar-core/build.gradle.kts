plugins {
    kotlin("jvm")

    id("com.gradleup.shadow") version "9.5.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin/")
            srcDir("src/generated/kotlin/")
        }

        resources {
            srcDir("src/main/resources/")
            srcDir("src/generated/resources/")
        }
    }
}

kotlin {
    jvmToolchain(25)
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

        duplicatesStrategy = DuplicatesStrategy.WARN
    }

    // The data pack is kept out of the resource folder so that if we need to we can
    // use something like weld in the future
    register<Copy>("copyDataPack") {
        from("src/main/datapack/")

        into("src/generated/resources/datapack/")
    }

    register<Zip>("packageResourcePack") {
        from(file("src/main/resourcepack/"))
        from(file("src/generated/resourcepack/"))

        archiveFileName = "resources.zip"
    }

    register<Zip>("packageServer") {
        from(shadowJar.map { it.archiveFile }) {
            into("plugins/")
        }

        from(project.tasks.named("packageResourcePack")) {
            rename { "resources.zip" }
        }

        archiveFileName = "quasar-server.zip"
        destinationDirectory = layout.buildDirectory.dir("package")
    }
}
