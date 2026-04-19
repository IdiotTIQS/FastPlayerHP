# FastPlayerHP

FastPlayerHP is a Minecraft (Paper) plugin focused on displaying player health (HP) above player heads in real time.

## Features

- Shows player HP above each player.
- Designed for Paper API `1.21.8`.
- Lightweight plugin structure for easy extension.

## Requirements

- Java `21`
- Paper server `1.21.8` (or compatible builds)

## Installation

1. Build the plugin:
   ```bash
   mvn clean package
   ```
2. Copy the generated `.jar` from `target/` to your server `plugins/` folder.
3. Start or restart your server.

## Plugin Info

- **Name:** `FastPlayerHP`
- **Main class:** `cn.brocraft.fastPlayerHP.FastPlayerHP`
- **Author:** `TIQS`

## Notes

- This repository currently provides the plugin base and metadata.
- You can extend `FastPlayerHP` to add custom HP display behavior and configuration.
