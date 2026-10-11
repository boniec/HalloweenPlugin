# Darth Vader NPC — ModelEngine + Nexo (Minecraft 1.21.4)

Pakiet: pełna postać voxel (~36 px wysokości), blueprint `darth_vader`, animacja `idle`.

## 1. ModelEngine

1. Skopiuj folder `ModelEngine/blueprints/darth_vader/` do `plugins/ModelEngine/blueprints/darth_vader/`.
2. Upewnij się, że obok `darth_vader.bbmodel` leży `darth_vader.png` (128×128).
3. Na serwerze: `/meg reload` (lub restart).
4. Test (OP): `/meg summon darth_vader` — jeśli plugin ma summon; inaczej użyj MythicMobs poniżej.

**ID modelu:** `darth_vader` (w MythicMobs: `model{mid=darth_vader}`).

## 2. Resource pack (jeden wspólny z Nexo)

ModelEngine po `/meg reload` generuje assety do swojego folderu packa. Po merge z Nexo:

- Wgraj **jeden** pack (Nexo build + pliki ModelEngine) na hosting / `server.properties` → `resource-pack=`.
- Gracze: F3+T po aktualizacji.

Opcjonalnie w Blockbench otwórz `darth_vader.bbmodel`, dopasuj UV do `darth_vader.png`, zapisz i ponów `/meg reload`.

**Świecące lampki (chest/belt):** w edytorze tekstur dodaj warstwę emissive w ModelEngine Resource Pack lub osobny `_e.png` według dokumentacji ME dla Twojej wersji.

## 3. MythicMobs (zalecane NPC)

1. Skopiuj `MythicMobs/darth_vader_npc.yml` → `plugins/MythicMobs/Mobs/`.
2. `/mm reload`
3. `/mm m spawn darth_vader_npc`

Mob: cichy, bez AI, model + animacja `idle`, bez obrażeń z ataku.

## 4. Nexo

1. Skopiuj `Nexo/items/flamehub/darth_vader_npc.yml` do `plugins/Nexo/items/` (podfolder dowolny).
2. `/nexo reload` + przebuduj pack Nexo jeśli używasz generatora.
3. Opcjonalnie `/nexo give darth_vader_marker` (sam item; spawn nadal przez MythicMobs).

Nexo **nie** renderuje samo modelu 3D moba — to robi ModelEngine na encji. Nexo trzyma wspólny resource pack i ewentualnie item-komendę.

## 5. Citizens (alternatywa)

Po załadowaniu modelu na NPC:

```
/npc create Vader
/npc trait modelengine darth_vader
/npc trait command add -p mm m spawn darth_vader_npc
```

(lub trait ModelEngine zgodny z Twoją wersją Citizens + MEG4)

## 6. Dalsza edycja (1:1 jak referencja)

1. Otwórz `darth_vader.bbmodel` w **Blockbench** (format: Free, 128 UV).
2. Dodaj kolejne małe cube’y na hełmie / płaszczu — potem `node tools/generate_darth_vader_bbmodel.mjs` (lub wersja Python).
3. Eksport nie jest konieczny — ME czyta `.bbmodel` z folderu blueprints.

## Struktura

```
GOTOWE-DarthVader-NPC/
  ModelEngine/blueprints/darth_vader/
    darth_vader.bbmodel
    darth_vader.png
  MythicMobs/darth_vader_npc.yml
  Nexo/items/flamehub/darth_vader_npc.yml
  tools/generate_darth_vader_bbmodel.py
  INSTALACJA.md
```
