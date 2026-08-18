package net.quasarmc.quasar.api.addon

/**
 * The state in t
 */
enum class AddonState {
    /**
     * The addon has just loaded and has not started initialization.
     */
    LOADED,

    /**
     * The addon provider has attached its plugin and is ready for initialization.
     */
    PRE_INIT,

    /**
     * The addon is initializing.
     */
    INIT,

    /**
     * The addon is fully initialized.
     */
    ACTIVE
}
