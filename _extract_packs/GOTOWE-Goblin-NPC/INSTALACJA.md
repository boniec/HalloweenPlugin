# Goblin NPC — ModelEngine + Nexo (1.21.4)

## Pliki modelu

- `ModelEngine/blueprints/goblin/goblin.bbmodel`
- `ModelEngine/blueprints/goblin/goblin.png` (**512×512**, malowana tekstura HD)

**ID modelu:** `goblin`  
**Animacje:** `idle`, `walk`

## Instalacja

1. Skopiuj folder `ModelEngine/blueprints/goblin/` → `plugins/ModelEngine/blueprints/goblin/`
2. `/meg reload`
3. `MythicMobs/goblin_npc.yml` → `plugins/MythicMobs/Mobs/` → `/mm reload`
4. Spawn: **`/mm m spawn goblin_npc`**
5. Nexo (opcjonalnie): `Nexo/items/flamehub/goblin_npc.yml` → `/nexo reload` + merge packa z ModelEngine

## Regeneracja z generatora

```text
node tools/generate_goblin_bbmodel.mjs
```

(Pakiet Darth Vader w `GOTOWE-DarthVader-NPC` możesz zignorować — zastąpiony tym goblinem.)
