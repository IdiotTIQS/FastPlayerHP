# FastPlayerHP

FastPlayerHP is a Paper/Spigot plugin that displays player health above each player's head using an invisible armor stand.

## Features

- 1.21+ best-effort compatibility (no NMS, no external dependencies)
- Invisible armor stand above player head
- Heart-style health text
- Two display modes:
  - `hearts` -> `❤ 18.0`
  - `hearts_and_max` -> `❤ 18.0/20.0`
- Event-driven updates with low-frequency polling fallback
- Distance-based visibility control

## Commands

- `/fphp reload` - reload config
- `/fphp toggle` - enable/disable plugin display runtime
- `/fphp mode <hearts|full|toggle>` - switch display mode

## Permission

- `fastplayerhp.admin` (default: op)

## Config

See `src/main/resources/config.yml`.

## Build

```bash
mvn clean package
```

Build output jar is generated under `target/`.

