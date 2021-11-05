![](/screenshots/1.png)

# observable -- see what's lagging your server

This is a spiritual successor to [LagGoggles](https://www.curseforge.com/minecraft/mc-mods/laggoggles) for Minecraft 1.16 (and hopefully later versions). Licensed under MPLv2.

See [CurseForge](https://www.curseforge.com/minecraft/mc-mods/observable) for download links.

# Development notes

Fabric 1.17:

- To run the mod in a development environment, you currently need to set the `DEV_ENV` environment variable, [like so](https://user-images.githubusercontent.com/10052313/140556395-adc23683-5a77-452c-949c-ef08d320280b.png). This will be improved in the future, as it is also a problem for Forge 1.17 and I'd like to handle it in a more batter manner.

ROADMAP:

1. Allow exporting of profiling data.
2. Currently, blocks and entities are profiled by simply timing how long it takes for the blocks to tick. I'm exploring methods of sampling stack traces for individual entities, so users and devs can get an idea of what exactly the block is doing to take up tick time.
