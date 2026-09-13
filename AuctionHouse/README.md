# MinecraftPlugins

A list of Custom plugins created by me.

| Plugin | Description |
|---|---|
| AuctionHouse | Offer-based auction house. List items, others offer item bundles, seller accepts/rejects; everything routes through a claims menu. Item-for-item (optional economy support disabled by default). Java + Bedrock (Geyser). |

## Build

```powershell
.\gradlew.bat build
```

Jars are produced in `auction-house/build/libs/AuctionHouse-1.0.0.jar` (self-contained — no external dependencies).

Drop the jar into the server's `plugins/` folder and restart. Loads `config.yml`, `lang.yml`, `gui.yml`, `sounds.yml` on first run.

## Docs

- Spec: `docs/superpowers/specs/2026-09-06-auction-house-design.md`
- Implementation plan: `docs/superpowers/plans/2026-09-06-auction-house.md`
- Manual QA checklist: `docs/superpowers/plans/2026-09-06-auction-house-QA.md`