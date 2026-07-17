package net.quasarmc.quasar.api.registration

/**
 * A custom resource pointer managed by a hardcoded registry loader. Stores a reference to the resource it points to
 * in order to speed up registry accesses.
 */
class HardcodedCustomResourcePointer<out TBase, TValue : TBase>(
    key: CustomResourceKey<TValue>
) : CustomResourcePointer<TValue>(key) {
    /**
     * Stored reference to the true value of the referenced resource
     */
    var known: TValue? = null;

    /**
     * Update the known reference
     */
    internal fun update(value: TValue) {
        known = value;
    }

    override fun get(): TValue {
        // todo: some kind of error
        return known!!;
    }
}
