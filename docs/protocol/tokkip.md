# Tokkip, shared between the phone and Tokkip PC

Tokkip Phone carries Tokkip between the Tokkip app on the phone
(`mx.loperstack.tokkip`) and Tokkip PC, so he is the same on both: species,
colours and outfit, and on the PC, how you feel today.

## The way it goes

```
Tokkip (phone) ──broadcast──▶ Tokkip Phone (phone) ──DeviceInfo.tokkip──▶ Tokkip Phone (PC) ──▶ %APPDATA%\Tokkip\shared\phone-tokkip.json ──▶ Tokkip PC
Tokkip (phone) ◀──broadcast── Tokkip Phone (phone) ◀──DeviceInfo.tokkip── Tokkip Phone (PC) ◀── %APPDATA%\Tokkip\shared\pc-tokkip.json ◀── Tokkip PC
```

- **On the phone**, the Tokkip app sends an explicit broadcast
  `mx.loperstack.tokkiphone.action.TOKKIP` (extra `json`) to Tokkip Phone,
  which keeps it and passes it to the core (`update_tokkip`). A PC's Tokkip
  goes back as `mx.loperstack.tokkip.action.PC_TOKKIP` to the Tokkip app
  (`android/…/tokkip/TokkipLink.kt`).
- **Between the devices**, the JSON rides in `DeviceInfo.tokkip` (sent with
  `hello` and `hello.update`, kept in the peers table, at most
  `MAX_TOKKIP_LEN` = 4096 bytes). Older versions ignore the field.
- **On the PC**, Tokkip Phone writes the newest phone's JSON to
  `phone-tokkip.json` and sends what Tokkip PC leaves in `pc-tokkip.json`
  (`desktop/app/src/tokkip_share.rs`). The folder is `%APPDATA%\Tokkip\shared`
  (`~/.config/Tokkip/shared` on Linux), where Tokkip Healthy PC leaves its note too.

The core and the PC app only check the size and that it is a version 1 object;
the Tokkip apps read the rest.

## The JSON

```json
{
  "v": 1,
  "from": "phone",
  "changedAt": 1762732800000,
  "look": {
    "species": "conejo",
    "skin": "lisa",
    "body": "#F7EAD3",
    "accent": "#3F9E9A",
    "ink": "",
    "cheek": "",
    "outfit": "auto"
  },
  "feeling": { "day": "2026-11-10", "mood": 4, "rest": "ok" }
}
```

- `changedAt`: when the look last changed (Unix ms). The newer side wins and
  the other takes its look *and* its `changedAt`, so nothing bounces back.
- `look.species`: `conejo`, `gato`, `perro`, `robot`, `mapache`. `skin`: `lisa`
  or `ensueno`. Colours are `#RRGGBB`; `ink` and `cheek` empty mean automatic.
- `look.outfit`: Tokkip PC's ids (`auto`, `none`, `partyHat`, `crown`,
  `beanie`, `hearts`, `flowerCrown`, `sunglasses`, `strawHat`, `tricolor`,
  `witchHat`, `marigolds`, `scarf`, `antlers`, `santaHat`, `roundGlasses`,
  `bow`, `headphones`). The phone maps them to its own names.
- `feeling` (phone only, optional): the diary's mood today (1 bad … 5 great,
  0 none) and last night's rest (`tired`, `ok`, `great` or empty). Tokkip PC
  shows it in the Wellness card and reacts once a day; it never goes back.

Anything a reader doesn't know falls back to its default instead of failing.
