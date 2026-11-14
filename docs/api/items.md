# Quasar Item API

The custom item api allows one to easily create new items with custom behavior.
All of its code lies in `quasar-core/net.quasarmc.quasar.api.item` and can
be used in any Quasar addons.

## Creating Custom Items

Custom items are split into two parts. A custom item class and a custom item
definition. A custom item class inherits from the Quasar API `CustomItem` class
and implements the behavior of the custom item. A custom item definition is an
instance of a custom item class with information about that specific item.

### Custom Item Classes

The custom item class consists of a set of properties, event handlers, and 
optionally (preferably) a builder.

The class's properties are defined in its constructor argument list, and are the
values that custom item definitions can change in order to fine-tune the item's
behavior. They are set using properties collected by the builder when constructing
a item definition.

The class's event handlers are methods in the custom item class that override
event methods in the base `CustomItem` class. All event handlers start with `on`
and most just forward game events from the Bukkit API. Information on specific
events can be found in the [Paper Javadocs](https://jd.papermc.io/paper).

The class's builder provides a developer friendly interface for setting up a
custom item instance's property, behavior components, and prototype. Builders
are for the most part boilerplate code, and will likely become auto-generated
some time in the future. Almost all custom item builders follow the following
pattern:

```kotlin
open class Builder(identifier: NamespacedKey, material: Material)
         : CustomItemBuilder<CustomItemClass>(identifier, material)
{
    var itemProperty: ItemPropertyType = itemDefaultValue;
    // more item properties...
    
    fun property(value: ItemPropertyType): Builder 
            = this.apply { itemProperty = value }
    // more item property setters...
    
    override fun build(): CustomItemClass {
        val item = CustomItemClass(
            identifier,
            ItemStack.of(material),
            behavior.build(),
            itemProperty,
            // more item property constructor arguments...
        )
        
        augment(item) // apply prototype changes from superclass
        
        return item
    }
}
```

### Custom Item Instances

Custom item instances are created through a custom item class's builder. Item
instances consist of a prototype, a set of behavior components, and the custom
item's properties.

The custom item's prototype is the ItemStack representation of the item that is
created when making an actual Minecraft item and sending it to the client. It
contains all the vanilla properties of the item, such as its stack size,
durability, and item model. Vanilla components can be added to the prototype
using the `setComponent` method or removed using the `removeComponent` method.

Behavior components are Quasar's version of Minecraft's components. They do
not exist on the item stack, and are instead tied to the custom item instance
itself (for simplicity.) Behavior components can be added to the instance using
the `setBehavior` method or removed using the `removeBehavior` method.

The custom item instance's properties are the values set on the custom item
class's properties. They are described in the previous section.

All custom item instances in Core can be found in the `CoreCustomItems` object.
All custom item instances used for debugging the Quasar API can be found directly
defined in the `CustomItemRegistry` object.

## Behavior Components

As mentioned previously, behavior components are values set on custom item instances
to define how they should interact with other Quasar mechanics. They are defined
with a `CustomItemBehaviorType` and registered in the `CustomItemBehaviorTypeRegistry`

Behavior components do not derive from a special base class, and instead can be any
type. The generic type on behavior components are only used for type checking and to
hint to an IDE or language server that something is a specific type so that developers
do not have to keep track.
