package net.quasarmc.quasar.api.util.component

/**
 * An immutable map of component types to component data.
 *
 * @param TComponentType The component type used as a key within the map
 * @param TBaseValue     The base type for all component values
 * @param data           The underlying map object.
 */
data class CustomComponentMap<TComponentType : ICustomComponentType<TComponentType, TBaseValue>, TBaseValue>(
    /**
     * The actual map. Any writes to this map should be checked to make sure that the value in the
     * map is actually the type that type info says it is. All reads from the data map use unchecked
     * casts.
     */
    private val data: Map<ICustomComponentType<TComponentType, TBaseValue>, TBaseValue>
) {
    /**
     * Get a value from the map or return null if not present.
     *
     * @param TType  The component type to fetch.
     * @param TValue The data type of the component
     * @param type   The component type to fetch.
     */
    fun <TType, TValue> getOrNull(type: TType): TValue?
        where TType  : ICustomComponentType<TComponentType, TValue>,
              TValue : TBaseValue
        = data[type] as TValue?

    /**
     * Get a value from the map or error if not present.
     *
     * @param TType  The component type to fetch.
     * @param TValue The data type of the component
     * @param type   The component type to fetch.
     *
     * @throws NoSuchElementException The component was not found.
     */
    operator fun <TType, TValue> get(type: TType): TValue
        where TType : ICustomComponentType<TComponentType, TValue>,
              TValue : TBaseValue
        = getOrNull(type) ?: throw NoSuchElementException("No component of type \"${type}\".")
}
