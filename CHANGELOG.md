# Changelog

## 1.0.0

First public release.

- Public Java API (`rexranks-api`) with users, groups, tracks, nodes, contexts, events and a query layer.
- Permission calculator with inheritance, wildcards, negation, regex nodes, weights and expiry.
- Storage backends: H2 (default), SQLite, MySQL / MariaDB, and YAML. `/rr migrate` copies data between file backends.
- Full command tree for users, groups and tracks, including temporary nodes, contexts, promote/demote, search, tree, bulk update, import and export.
- LuckPerms export import.
- Integrated and remote web editor, with a colour studio (multi-stop gradients, HSV, easing, rainbows, MiniMessage / legacy output).
- In-game colour workshop and dyed-leather GUI.
- Vault permission and chat providers, PlaceholderAPI expansion.
- Cross-server sync via database polling, Redis pub/sub and plugin messaging.
- Bukkit `PermissibleBase` injection, Folia-aware schedulers, GitHub-releases update checker.
