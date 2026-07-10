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
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin")
            srcDir("src/generated/kotlin")
        }

        resources {
            srcDir("src/main/resources")
            srcDir("src/generated/resources")
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

    register<Copy>("copyDataPack") {
        from("src/main/datapack/")
        into("src/generated/resources/datapack/")
    }

    register<Zip>("packageResourcePack") {
        from("src/main/resourcepack/")

        archiveFileName = "resources.zip"
    }
}
