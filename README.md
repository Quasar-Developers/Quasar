This is Brillanix's own development fork for Quasar.
(and yes thats mostly it)

# Quasar's original README.md

# Quasar Monorepo

Quasar is a collection of plugins, data/resource packs, and tools aiming to
implement modded gameplay on vanilla Minecraft clients. 

Quasar's code is licensed under the GNU AGPL 3.0, with exceptions allowing it to be 
run and distributed alongside Minecraft. Most resources are licensed under the Creative
Commons licenses, although some exceptions apply. Licensing details can be viewed in
the `LICENSE.md` file. Text for Quasar's licenses can be found in the `LICENSES` folder.

Quasar is very early in development and may contain non-free placeholder assets, a
list of all non-free assets currently being used by the project can be found in the
`NONFREE.md` file. Contributions with free replacement assets are always welcome.

Quasar does not accept AI-generated or AI-assisted contributions. Please do not make
merge requests with AI-generated code, they will be denied and you will be blocked
from contributing in the future.

## Project structure

The Quasar repository is split into multiple projects, each of which can be found in
the `/projects` directory. Some projects depend on other projects, so they may need to
be built first. The most important project is `quasar-core` which contains the Quasar
content and the Quasar API. If you are a contributor or hosting a forked server this is
probably what you're looking for.

Projects may contain additional top-level files or folders with project-specific details.
Otherwise, all projects follow the same structure:
```
projects/*
L README.md        - project-specific information
L LICENSES/        - project-specific licenses
I -------------------------------------------------------
L build.gradle.kts - project build script
L build/           - build outputs 
L src/             - project source files
  L main/          - all runtime code
```

## Build/Install instructions

The Quasar plugin can be built by running the following
```shell
./gradlew :quasar-core:packageServer
```

The build will create a zip file containing all of the files needed to add Quasar to a server
at `/projects/quasar-core/build/package/quasar-server.zip`:
```
quasar-server.zip
L plugins/      - Quasar and its dependencies
L resources.zip - A resource pack you will have to host and point to in your server configuration.
```
