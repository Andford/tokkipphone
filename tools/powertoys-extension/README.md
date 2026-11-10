# Tokkip Phone PowerToys Command Palette / PowerToys Run Extension (`tokkiphone-ptrun`)

`tokkiphone-ptrun` is a standalone, zero-dependency Rust helper that connects Windows launcher tools (PowerToys Command Palette, PowerToys Run, Flow Launcher, Raycast for Windows) to Tokkip Phone's [Local Authenticated API](../../docs/api.md).

## Building

```powershell
cargo build --release --manifest-path tools/powertoys-extension/Cargo.toml
```

## Prerequisites

1. Open **Tokkip Phone → Settings → Local automation API** and turn on **Enable local automation API**.
2. Tokkip Phone writes `%LOCALAPPDATA%\Tokkip Phone\api-token.json` containing the loopback port (`53320`) and a 256-bit Bearer token.

## Usage

### Querying Palette Items

```powershell
tokkiphone-ptrun query ""
tokkiphone-ptrun query "ring"
tokkiphone-ptrun query "https://tokkiphone.dev"
```

Outputs a JSON array of palette commands (`send-clipboard`, `ring`, `camera` `photo`/`scan`, `send-url`, `send-text`, plus recent timeline items).

### Invoking an Action

```powershell
tokkiphone-ptrun invoke ring
tokkiphone-ptrun invoke send-clipboard
tokkiphone-ptrun invoke camera photo
tokkiphone-ptrun invoke camera scan
tokkiphone-ptrun invoke send-url "https://tokkiphone.dev"
tokkiphone-ptrun invoke send-text "Copied from PowerToys"
tokkiphone-ptrun invoke send-file "C:\Users\Alice\Desktop\notes.pdf"
```
