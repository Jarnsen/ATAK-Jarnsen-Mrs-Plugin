# Jarnsen Mrs Plugin

ATAK-CIV Plugin für eine einfache Mörser-/Richtungsdarstellung vom eigenen
Standort zu einem ausgewählten Kartenpunkt.

## Aktueller Stand

Version **0.1.0** ist die erste Implementierung des gemeinsam festgelegten
Kartenbildes.

### Bedienung

1. In ATAK das Werkzeug **Jarnsen Mrs Plugin** antippen.
2. Einen vorhandenen Punkt/Marker oder eine freie Stelle auf der Karte antippen.
3. Das Plugin zeichnet den Sektor vom eigenen ATAK-Self-Marker zum gewählten
   Ziel.
4. Bewegt sich der eigene Standort, wird die Darstellung automatisch neu
   berechnet. Wird ein beweglicher ATAK-Punkt als Ziel gewählt, folgt die
   Darstellung auch diesem Ziel.

## Darstellung

- Mittellinie: eigener Standort → ausgewähltes Ziel
- Richtung: **Gitternord**
- Winkelmaß: **NATO 6400 Strich**
- Sektor: **±600 Strich** um die Mittellinie
- maximale Sektorreichtiefe: **8 km**
- Entfernungsbögen alle **500 m**
- 500-m-Zwischenbögen: dünner / gestrichelt
- volle Kilometer: stärker / durchgezogen
- Entfernungsbeschriftungen liegen jeweils auf der eigenen Seite vor dem
  zugehörigen Bogen
- genau **eine** zentrale, um 90° gedrehte `)(`-Klammer bei 4 km
- an der Klammer:
  - oben: Richtung in **Str GN**
  - unten: tatsächliche Entfernung zum Ziel
- rotes Zielkreuz und Beschriftung **ZIEL**

## Gitternord

Die Karten-Geometrie wird mit dem geodätischen True-Bearing gezeichnet. Für die
angezeigte Richtung wird ATAKs eigene Grid-Convergence-Berechnung verwendet:

`Grid Bearing = True Bearing - Grid Convergence`

Danach erfolgt die Umrechnung:

`Strich = Grad × 6400 / 360`

Damit bleibt die Linie geometrisch korrekt, während die Richtungsangabe als
Gitternord/Strich ausgegeben wird.

## Technische Basis

Das Projekt orientiert sich an der offiziellen ATAK-Plugin-Struktur und ist
aktuell für **ATAK-CIV 5.5.1.8** konfiguriert.

Paket/Namespace:

`com.jarnsen.atak.mrs.plugin`

## Bauen

Zum Bauen wird das offizielle ATAK Plugin DevKit benötigt.

### Variante A – lokale DevKit-Datei

Die Datei `atak-gradle-takdev.jar` aus dem passenden ATAK DevKit in das
Repository-Hauptverzeichnis legen. Die Datei ist absichtlich in `.gitignore`
eingetragen.

### Variante B – TAK Maven Repository

In `local.properties` einen verfügbaren TAK-Repository-Zugang konfigurieren:

```properties
takrepo.url=...
takrepo.user=...
takrepo.password=...
```

Danach kann das Projekt in Android Studio geöffnet bzw. mit einer passenden
Gradle-Installation gebaut werden.

## Projektstruktur

- `JarnsenMrsLifecycle` – Plugin-Lifecycle
- `JarnsenMrsPluginTool` – ATAK-Toolbar-Eintrag
- `JarnsenMrsMapComponent` – Initialisierung und Overlay-Gruppe
- `JarnsenMrsSectorTool` – Zielauswahl, Gitternord-Berechnung und komplette
  Sektor-Geometrie
- `docs/GEOMETRY.md` – feste Geometrie und Berechnung

## Noch bewusst fest eingestellt

Die erste Version bildet exakt den besprochenen Entwurf ab. Später können die
Werte als Einstellungen ergänzt werden, z. B. Sektorbreite, Maximalreichweite,
Ringabstand, Farben sowie Strich/Grad-Umschaltung.
