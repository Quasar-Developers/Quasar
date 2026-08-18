package net.quasarmc.quasar.core

/**
 * Template for the build system to insert build metadata.
 */
object QuasarBuildMetadata {
    const val version:   String = "${version}"
    const val commit:    String = "${commit}"
    const val sourceURL: String = "${sourceURL}"
}
