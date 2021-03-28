# Packwiz server updater

Updates packwiz serverside.

## Dependencies

Packwiz bootstrapper: https://github.com/comp500/packwiz-installer-bootstrap/releases (launches the packwiz installer)

## Installation

- Place the packwiz boostrapper in the root directory for your server
- On first launch the mod will generate a `packwiz-server-updater.properties` file. Simply provide a link to your pack.toml file, for e.g. `https://raw.githubusercontent.com/The-Tiny-Taters/TTT-Creative/main/pack.toml` and set `should_update` to `true` and the server will update when it starts.

## Restart command

If you have a system in place so that the server auto-restarts when the `/stop` command is given you can use the `/restart` command which by default is given to level 2 server operators. To enable set `enable-restarts` to `true` and set a cooldown period in minutes from when the server starts (so players don't randomly spam the command)

## License

Honestly do whatever u want with it.
