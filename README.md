# JotaPoke

<img src="icon.png" width="128">

App Android (baseado no [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher), LGPL-3.0) que já abre com Minecraft 1.12.2 + Forge, Pixelmon, MineColonies e o servidor JotaPoke salvo.

- `manifest.json`: lista de mods, arquivos e versão do app. O app lê este arquivo toda vez que abre e se atualiza sozinho.
- `files/`: arquivos extras do jogo (ex.: lista de servidores).
- `src/`: código da parte de atualização automática (`JotaApp.java`).
- Baixe o APK em [Releases](https://github.com/DevJotaVx/jotapoke/releases).

## Licenças

- `files/mods/phosphor-*.jar`: Phosphor, de JellySquid, licença LGPL-3.0 (https://github.com/CaffeineMC/phosphor-fabric). Redistribuído sem alterações.
