# Immersive Portals (for Neo) 🌀

A NeoForge 1.21.1 port of the beloved Immersive Portals mod — step through a portal and actually *see* the other side first, then walk straight across without a loading screen breaking the illusion.

This is a local adaptation based on [iPortalTeam/ImmersivePortalsModForNeo](https://github.com/iPortalTeam/ImmersivePortalsModForNeo), originally created by qouteall and Nick1st, with the usual flock of open-source contributors. All credit for the core magic goes to them — this repo just keeps a tidy, buildable copy of the sources.

> 📦 Also available on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-portals-port).

## Versions

| Loader | Minecraft | Mod version | JDK | Source |
|---|---|---|---|---|
| neoforge | 1.21.1 | 6.0.7 | 21 | [neoforge/1.21.1](neoforge/1.21.1) |

## Building from source

Install the JDK listed above for the version you want, then build from inside that folder:

```sh
cd neoforge/1.21.1
./gradlew build
```

On Windows, use `gradlew.bat build` instead. The finished jar lands in `build/libs/`. The very first build will take a little longer, since Gradle needs to download itself and every dependency the project declares.

## About this repository

This repo keeps the source code and resources for the published version, folder by folder. Local caches, test worlds, compiled output and backup copies are intentionally left out — only the real, published code lives here.

## License & credits

Every license, credit and notice file that shipped with the original project has been kept as-is. Please check the upstream project and the license file inside the version folder before redistributing — publishing the source here doesn't change any of the declared licenses.
