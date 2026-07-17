package net.quasarmc.quasar.api.registration

import net.quasarmc.quasar.api.registration.events.RegistrationEvent
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.Vector

/**
 * Helper class that automatically loads hardcoded data into a registry on registry reloads.
 */
class HardcodedCustomRegistryLoader<TValue>(private val registry: NamespacedKey) : Listener {
    /**
     * Data that will be loaded into the custom registry.
     */
    private val plan = HashMap<NamespacedKey, Plan<TValue, out TValue>>()

    /**
     * Add a new provider to register data for.
     */
    fun <TTrue : TValue> register(identifier: NamespacedKey, provider: () -> TTrue): HardcodedCustomResourcePointer<TValue, TTrue> {
        val pointer = HardcodedCustomResourcePointer<TValue, TTrue>(CustomResourceKey(registry, identifier));

        plan[identifier] = Plan(provider, pointer);

        return pointer;
    }

    @EventHandler
    private fun onRegister(ev: RegistrationEvent) {
        ev.registerBase<TValue>(registry) { registry ->
            for ((identifier, value) in plan) {
                registry[identifier] = value.run();
            }
        }
    }

    /**
     * In order to ensure that run always contains the type that the provider provides (and to satisfy the type checker),
     * we keep them together in a class so that they're always correct.
     */
    private data class Plan<TPBase, TPValue : TPBase>(val provider: () -> TPValue, val pointer: HardcodedCustomResourcePointer<TPBase, TPValue>) {
        fun run(): TPValue {
            val value = provider()
            pointer.update(value)
            return value;
        }
    }
}
