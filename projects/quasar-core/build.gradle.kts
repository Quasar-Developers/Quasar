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
        }

        resources {
            srcDir("src/main/resources/")
            srcDir(layout.buildDirectory.dir("generated/copyDataPack/resources"))
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
}

tasks.processResources {
    dependsOn(copyDataPack)

    duplicatesStrategy = DuplicatesStrategy.WARN
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
