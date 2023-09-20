# Packwiz server updater

Updates packwiz on a server from a single command.

![quilt badge](https://raw.githubusercontent.com/intergrav/devins-badges/1aec26abb75544baec37249f42008b2fcc0e731f/assets/cozy/supported/quilt_vector.svg)
![fabric badge](https://raw.githubusercontent.com/intergrav/devins-badges/1aec26abb75544baec37249f42008b2fcc0e731f/assets/cozy/supported/fabric_vector.svg)
![forge badge](https://raw.githubusercontent.com/intergrav/devins-badges/1aec26abb75544baec37249f42008b2fcc0e731f/assets/cozy/supported/forge_vector.svg)

### Forge Version
The forge version requires [Sinytra's Connector](https://modrinth.com/mod/connector) to work properly

## Usage

- Place the [packwiz-installer-bootstrap.jar](https://github.com/packwiz/packwiz-installer-bootstrap/releases/tag/v0.0.3) in the root directory for your server
- Use `/packwizsu link [URL]` to link to your `pack.toml` file
- Update the modpack using `/packwizsu update`. This will update the modpack and then shutdown the server

## Server hosting provider

If you are using this mod on anything other than a dedicated server which you own yourself, please check with your hosting provider if you are allowed to install this mod. This is because it may violate your provider's TOS by remotely downloading files from a location you specify.

It should be noted Packwiz is designed to do this safely by using a hashing algorithm to determine if a file is valid or not but your server host may not know this or understand where the files are coming from if you don't tell them.

## License

Honestly do whatever you want with it.
