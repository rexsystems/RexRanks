**Modern permissions for Minecraft servers.** Groups, tracks, temporary nodes, MiniMessage prefixes, and a web editor whose colour studio is the part you'll actually enjoy using.

![Paper](https://img.shields.io/badge/Paper-supported-FCD34D) ![Purpur](https://img.shields.io/badge/Purpur-supported-FCD34D) ![Folia](https://img.shields.io/badge/Folia-supported-FCD34D) ![Java 21](https://img.shields.io/badge/Java-21-blue) ![Minecraft 1.20.4+](https://img.shields.io/badge/Minecraft-1.20.4%2B-62B47A)

## Why RexRanks?

RexRanks is a from-scratch permissions plugin for Paper, Purpur and Folia. It has the pieces you expect from a modern permission system — inheritance, wildcards, negation, temporary nodes, weighted prefixes, promotion tracks, YAML or SQL storage, Vault and PlaceholderAPI — and two things we wanted to do better:

- **A real web editor**, served either from the plugin itself or from a small Node host, with a clear apply/diff flow.
- **A colour studio** for prefixes and suffixes: multi-stop gradients with drag-able handles, RGB or HSV interpolation, easing, rainbows, decorations, live Minecraft-style previews (chat, tab list, nametag), and one-click MiniMessage / legacy hex output.

There is also an in-game workshop (`/rr color`) that mirrors the same formatter, including a dyed-leather GUI picker.

## Highlights

**Permissions**

- Groups, users and weighted inheritance
- Wildcards, negation and regex nodes
- Temporary permissions (`1d2h`, `7 days`)
- Contexts (`world`, `server`, optional gamemode and dimension)
- Promotion tracks (`/rr user <name> promote|demote`)
- MiniMessage prefixes, suffixes and display names

**Editor & colour**

- Integrated web editor (`/rr editor`) or a remote host for networks
- Multi-stop gradient builder with RGB/HSV, easing and rainbows
- Compact MiniMessage, per-character precise MiniMessage, and `&#RRGGBB` legacy
- Apply a gradient straight to a group or user prefix
- In-game colour workshop and GUI

**Ops & integrations**

- Vault permission and chat providers
- PlaceholderAPI (`%rexranks_prefix%`, `%rexranks_group%`, `%rexranks_meta_<key>%`, …)
- LuckPerms JSON/YAML import (`/rr import luckperms <file>`)
- Redis, database and plugin-messaging sync
- H2 (default), SQLite, MySQL / MariaDB, or YAML storage
- Folia-safe schedulers and a public Java API

## Installation

1. Download the JAR and drop it into your server's `plugins/` folder.
2. Restart the server. RexRanks loads at startup.
3. Edit `plugins/RexRanks/config.yml` — the default `h2` storage needs no extra setup.
4. Coming from LuckPerms? Copy an export into the plugin folder and run `/rr import luckperms <file>`.

**Requirements:** Paper / Purpur / Folia **1.20.4+**, **Java 21**.

## Web editor

`/rr editor` gives you a clickable, time-limited link.

- **Integrated** (default) — the plugin serves the UI on port 7780. Set `editor.public-url` if needed. Changes apply immediately.
- **Remote** — host the editor once (Node or Docker; see the [GitHub repo](https://github.com/rexsystems/RexRanks) `web/` folder), set `editor.mode: remote` and `editor.remote-url`, then apply with `/rr apply <code>`.

The colour studio is also usable without a session, as a MiniMessage playground.

## Colour workshop (in game)

```
/rr color solid #e8b84a Staff
/rr color gradient #ff6b6b #ffd36a #6bcbff VIP
/rr color rainbow !
/rr color apply prefix g:admin 100
/rr color gui
```

## A few useful commands

Aliases: `/rexranks`, `/rr`, `/rank`, `/ranks`, `/perm`.

- `/rr user <user> permission set <node> [true|false] [duration] [context]`
- `/rr user <user> parent add <group>` / `promote <track>` / `demote <track>`
- `/rr group <group> meta setprefix 100 "<gradient:#ff6b6b:#ffd36a>[Admin] "`
- `/rr track staff append helper`
- `/rr check <user> <node>` and `/rr verbose`
- `/rr search <node>`, `/rr tree`, `/rr export`, `/rr sync`

Full command and permission lists, PlaceholderAPI keys, the API snippet and hosting notes live in the [GitHub README](https://github.com/rexsystems/RexRanks).

## License

RexRanks is licensed under the **GNU Affero General Public License v3.0**.

Made with ☕ by [RexSystems](https://rexsystems.me).
