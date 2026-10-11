# BlackGold Crates (FairStudio) — Nexo + ModelEngine

Źródło: `BlackGold Crates.zip` (FairStudio / Morozovoff).

## Co jest w repo

| Folder | Zawartość |
|--------|-----------|
| `_extract_archive_new/ModelEngine/blueprints/blackgold/` | `fairbg1.bbmodel`, `fairbg2.bbmodel`, `fairbg3.bbmodel` |
| `_extract_packs/archive/Nexo/pack/assets/blackgold/` | tekstury + modele kluczy |
| `_extract_packs/archive/Nexo/items/blackgold/blackgold_keys.yml` | `blackkey1`, `blackkey2`, `blackkey3` |
| `_extract_packs/archive/AquaticCrates/crates/` | `fairbg1.yml` … (klucze: **nexo:**) |
| `_extract_packs/archive/MagicCrates/crates/` | oryginał pod MagicCrates (klucze Oraxen w YAML — patrz niżej) |

Modele skrzyń w grze: **`modelengine:fairbg1`**, **`fairbg2`**, **`fairbg3`**.

## Wgranie na serwer

1. **ModelEngine**  
   Skopiuj `blueprints/blackgold/*.bbmodel` → `plugins/ModelEngine/blueprints/blackgold/`  
   `/meg reload` (lub restart). Wygeneruje modele `fairbg1`–`fairbg3` + assety do resource packu ME.

2. **Nexo**  
   Wgraj z `_extract_packs/archive/Nexo/` (całość lub merge):
   - `items/blackgold/blackgold_keys.yml`
   - `pack/assets/blackgold/**`  
   `/nexo reload` + przeładuj resource pack u graczy.

3. **AquaticCrates** (zalecane z Nexo)  
   `plugins/AquaticCrates/crates/fairbg*.yml` z `_extract_packs/archive/AquaticCrates/crates/`  
   Klucze: `material: nexo:blackkey1` itd.

   ```
   /acrates crate give fairbg1
   /acrates key give fairbg1
   ```

4. **MagicCrates** (opcjonalnie)  
   Pliki w `archive/MagicCrates/` mają w kluczach zapis **Oraxen** (`PublicBukkitValues: oraxen:id`).  
   Po przejściu na Nexo: w edytorze `/magiccrates editor` ustaw klucze na itemy z `/nexo give blackkey1` albo używaj AquaticCrates.

## ModelEngine — rozjeżdżające się części

W `plugins/ModelEngine/config.yml` możesz ustawić `Force-Update-Tick: 1` (więcej obciążenia CPU).

## ID Nexo (sklep / komendy)

- `blackkey1`, `blackkey2`, `blackkey3`  
- `/nexo give blackkey1`

## Licencja

Patrz README w paczce FairStudio — credit przy promocji, bez odsprzedaży.
