# Quasar Registration API

Quasar uses a custom registry API to simplify locating and using content.
All the classes related to it lie in 
`quasar-core/net.quasarmc.quasar.api.registration` and can be used in any
Quasar addon.

## Creating Custom Registries

Addons can create their own registries through three means:
- Implementing `ICustomRegistry`
  - You should not need to do this for the vast majority of cases, the other
    API registry classes should handle your needs.
- Extending `CustomRegistry`
  - CustomRegistry is the base class that all API registries are built off of.
    It does nothing except for implement `ICustomRegistry`
- Extending `SimpleCustomRegistry`
  - `SimpleCustomRegistry` provides helper methods for registering hardcoded
    content and automatically handles `RegistrationEvent`s. This is the 
    preferred method for most registries. 

`SimpleCustomRegistries` require you to register their event listeners by
calling `registerEventHandlers` with your addon's `Plugin` class. You can
register hardcoded values by calling `registerHardcoded` within your custom
registry class. The values provided by the lambdas will be automatically
added to them when it is reloaded.

In all cases, you will have to make the registry known to the Quasar API
by handling the `RegistryRegistrationEvent` and registering your registry
to the root registry.

## Adding Objects to Custom Registries

Addons can add objects to custom registries through two means:
- Handling the `RegistrationEvent`
- Using a `HardcodedCustomResourceLoader`

`HardcodedCustomResourceLoader`s automatically handle `RegistrationEvent`s
and work the same way that a `SimpleCustomRegistry` does, just outside a custom
registry class.

## Using Registered Resources

Holding references to registered resources directly means that your code will
break when custom registries are reloaded. Instead, you should use a
`CustomResourcePointer`, which will automatically fetch the correct object
from the registry for you.
