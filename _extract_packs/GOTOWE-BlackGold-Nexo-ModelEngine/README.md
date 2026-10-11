# Gotowe — BlackGold Crates (Nexo + ModelEngine)

Skopiuj zawartość folderów **do `plugins/`** na serwerze (merge, nie kasuj całego Nexo/ME).

```
GOTOWE-BlackGold-Nexo-ModelEngine/
├── Nexo/                    → plugins/Nexo/
├── ModelEngine/             → plugins/ModelEngine/
├── AquaticCrates/           → plugins/AquaticCrates/  (opcjonalnie)
└── README.md
```

## 1. ModelEngine

```
ModelEngine/blueprints/blackgold/fairbg1.bbmodel
ModelEngine/blueprints/blackgold/fairbg2.bbmodel
ModelEngine/blueprints/blackgold/fairbg3.bbmodel
```

→ `plugins/ModelEngine/blueprints/blackgold/`

Potem: **`/meg reload`** (albo restart).  
Modele w grze: **`fairbg1`**, **`fairbg2`**, **`fairbg3`**.

## 2. Nexo

```
Nexo/items/blackgold/blackgold_keys.yml
Nexo/pack/assets/blackgold/models/
Nexo/pack/assets/blackgold/textures/
```

→ wklej do `plugins/Nexo/` (items + pack/assets).

Potem: **`/nexo reload`** + przeładuj resource pack.

| ID Nexo     | Opis        |
|-------------|-------------|
| `blackkey1` | Klucz #1    |
| `blackkey2` | Klucz #2    |
| `blackkey3` | Klucz #3    |

`/nexo give blackkey1`

## 3. AquaticCrates (skrzynie 3D + klucze Nexo)

```
AquaticCrates/crates/fairbg1.yml
AquaticCrates/crates/fairbg2.yml
AquaticCrates/crates/fairbg3.yml
```

→ `plugins/AquaticCrates/crates/`  
W configu: `model: modelengine:fairbg1`, klucz: `nexo:blackkey1`.

```
/acrates crate give fairbg1
/acrates key give fairbg1
```

## Wymagane pluginy

- **ModelEngine** + **Nexo** (masz zamiast Oraxen z paczki)
- **AquaticCrates** — jeśli używasz folderu `AquaticCrates/`

## ME — rozjeżdżające się części modelu

`plugins/ModelEngine/config.yml` → `Force-Update-Tick: 1` (opcjonalnie, więcej CPU).

## Licencja FairStudio

Credit przy promocji; bez odsprzedaży — patrz oryginalny README w `BlackGold Crates.zip`.
