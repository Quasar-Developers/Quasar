plugins {
    `kotlin-dsl`

    id("com.pswidersk.python-plugin") version "3.2.17"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.pswidersk.python-plugin:com.pswidersk.python-plugin.gradle.plugin:3.2.17")
}

gradlePlugin {
    plugins {
        register("weld") {
            id = "weld"
            implementationClass = "net.quasarmc.quasar.build.weld.WeldPlugin"
        }
    }
}
