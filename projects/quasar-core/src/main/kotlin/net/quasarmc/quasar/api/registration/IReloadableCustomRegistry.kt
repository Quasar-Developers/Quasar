package net.quasarmc.quasar.api.registration

/**
 * Custom registry class that is managed by the [RegistrationManager] and participates in registry reloads.
 */
interface IReloadableCustomRegistry<TValue> : ICustomRegistry<TValue>;
