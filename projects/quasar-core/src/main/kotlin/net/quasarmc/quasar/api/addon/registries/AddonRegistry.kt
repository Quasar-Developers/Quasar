package net.quasarmc.quasar.api.addon.registries

import net.quasarmc.quasar.api.addon.Addon
import net.quasarmc.quasar.api.addon.AddonManager;
import net.quasarmc.quasar.api.registration.CustomRegistry
import net.quasarmc.quasar.api.registration.IReloadableCustomRegistry

/**
 * Registry containing Quasar API addons. Do not mutate this registry directly! Use [AddonManager] instead.
 */
object AddonRegistry : CustomRegistry<Addon<*>>();
