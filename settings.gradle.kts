rootProject.name = "quasar"

// Custom Gradle plugins
includeBuild("projects/build-logic/")

// Quasar plugin
include("quasar")
project(":quasar").projectDir = file("projects/quasar/")

