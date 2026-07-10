rootProject.name = "quasar"

// Quasar plugin
include("quasar-core")
project(":quasar-core").projectDir = file("projects/quasar-core/")

// Quasar API
include("quasar-api")
project(":quasar-api").projectDir = file("projects/quasar-api/")
