# FastPlayerHP

FastPlayerHP is a Paper/Spigot plugin that displays each player's health above their head using an invisible armor stand.

## Compatibility

- Best-effort compatibility: `1.21+` (targeting `1.21` API baseline)
- No NMS, no external dependencies most time
- Recommended runtime: Paper

## Features

- Two render backends:
  - `below_name` (native scoreboard, smoother and recommended)
  - `armorstand` (custom floating text above head)
- Optional `protocollib` render selection (auto-fallback to `below_name` when ProtocolLib is absent)
- Two display modes:
  - `hearts` -> `❤ 18.0`
  - `hearts_and_max` -> `❤ 18.0/20.0`
- Event-driven health refresh (damage/heal/respawn/join/world change)
- Per-tick follow task for smooth movement
- Low-frequency fallback polling task for safety sync
- Distance-based visibility control
- Configurable text template, color, symbol, and decimal precision

## Commands

- `/fphp reload` - reload config
- `/fphp toggle` - enable/disable runtime display
- `/fphp mode <hearts|full|toggle>` - switch display mode
- `/fphp render <armorstand|belowname|protocollib>` - switch render backend

## Permission

- `fastplayerhp.admin` (default: op)

## Configuration

File: `src/main/resources/config.yml`

Default keys:

```yaml
enabled: true
render-mode: below_name
display-mode: hearts_and_max
top-offset: 0.35
visible-distance: 32
poll-interval-ticks: 20

text:
  heart-symbol: "&c❤"
  below-name-title: "&c❤"
  hearts-format: "{heart} &f{health}"
  hearts-and-max-format: "{heart} &f{health}&7/&f{max_health}"
  health-decimals: 1
```

### Text placeholders

- `{heart}`
- `{health}`
- `{max_health}`

### Color codes

Use `&` color/format codes, for example:

- `&c` red
- `&f` white
- `&7` gray
- `&l` bold

Example:

```yaml
text:
  heart-symbol: "&d❤"
  hearts-format: "{heart} &b{health}"
  hearts-and-max-format: "{heart} &b{health}&7/&a{max_health}"
  health-decimals: 1
```

After changing config, run:

```text
/fphp reload
```

## Build

```bat
cd /d "E:\MineCraft Server\FastPlayerHP"
mvn clean package
```

Output jar is generated under `target/`.

## Notes

- If text is too high/low, tune `top-offset`.
- Large `visible-distance` values increase per-player visibility checks on busy servers.
