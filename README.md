![](/screenshots/1.png)

# observable -- see what's lagging your server

This is a spiritual successor to [LagGoggles](https://www.curseforge.com/minecraft/mc-mods/laggoggles) for Minecraft 1.16 (and hopefully later versions). Licensed under MPLv2.

See [CurseForge](https://www.curseforge.com/minecraft/mc-mods/observable) for download links.

ROADMAP:

1. Allow exporting of profiling data.
2. Currently, blocks and entities are profiled by simply timing how long it takes for the blocks to tick. I'm exploring methods of sampling stack traces for individual entities, so users and devs can get an idea of what exactly the block is doing to take up tick time.
