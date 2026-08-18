# Quasar Addon API

Quasar uses an addon system in order to expose plugins to the Quasar API.
Without it, the API would not be able to find classes provided by other
plugins. At the moment, the Addon API doesn't provide much functionality,
most of its uses are planned for later, so this document only goes over how
to set up an addon.

## Addon Registration

Plugins expose themselves to the API by registering an addon class during
Paper bootstrapping. An addon class can be created by inheriting from `Addon`
and implementing the required methods/properties.

```kotlin
class MyPluginAddon : Addon<MyPlugin>() {
    override val identifier  = NamespacedKey("myplugin:myplugin")
    override val name        = "My Plugin"
    override val description = "Example addon"
    override val author      = "John Quasar"
    override val version     = "0.1.0"
    override val sourceURL   = "https://codeberg.org/johnquasar/myaddon/"
}
```

Once an addon class is created, it can be instantiated and then registered
with the API using `AddonManager.register(...)`. During regular startup, when
the Bukkit `onLoad` method is called, the plugin must attach itself to the
addon using `Addon::attachPlugin`.
