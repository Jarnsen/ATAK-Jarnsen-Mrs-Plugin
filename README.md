# Jarnsen Mrs Plugin

ATAK-CIV Plugin für eine einfache Mörser-/Richtungsdarstellung zwischen einem
frei gewählten Start- und Zielpunkt.

## Aktueller Stand

Version **0.3.0** erweitert das Plugin zu einem persistenten Arbeitsbereich mit
mehreren Mrs-Zeichnungen. Zeichnungen bleiben nach einem ATAK-Neustart erhalten,
können direkt auf der Karte geöffnet, dupliziert, bearbeitet und entfernt werden.
Start und Ziel können per Karte, MGRS oder Breite/Länge gesetzt werden. Für
bestehende Zeichnungen stehen direkte Drag-Griffe sowie Rückgängig/Wiederholen
zur Verfügung.

### Bedienung

1. In ATAK das Werkzeug **Jarnsen Mrs Plugin** antippen.
2. Den Startpunkt wählen: **Eigene Position**, **Koordinaten eingeben** oder
   **Auf der Karte wählen**.
3. Den Zielpunkt auf die gleiche Weise wählen.
   Bei **Auf der Karte wählen** wird die Darstellung bereits beim Berühren
   aufgebaut, beim Ziehen live nachgeführt und erst beim Loslassen festgelegt.
4. Das Plugin zeichnet den Sektor vom gewählten Start zum gewählten Ziel.
5. Optional eine Sektorfarbe auswählen: **Weiß, Rot, Gelb, Blau, Grün** oder
   **Schwarz**. Mit **Standard** oder durch Schließen der Auswahl bleibt
   die bisherige Standardfarbe erhalten.
6. Wird die eigene Position oder ein beweglicher ATAK-Marker verwendet, wird
   die Darstellung bei Positionsänderungen automatisch neu berechnet.
7. Die eingezeichnete Darstellung oder das Plugin-Symbol antippen. Im Menü kann
   sie **Bearbeitet** (Start und Ziel neu wählen) oder **Entfernt** werden.

## Darstellung

- Mittellinie: ausgewählter Startpunkt → ausgewähltes Ziel
- Richtung: **Gitternord**
- Winkelmaß: **NATO 6400 Strich**
- Sektor: **±600 Strich** um die Mittellinie
- maximale Sektorreichtiefe: **8 km**
- Entfernungsbögen alle **500 m**
- 500-m-Zwischenbögen: dünner / gestrichelt
- volle Kilometer: stärker / durchgezogen
- optionale transparente Sektorschattierung in Weiß, Rot, Gelb, Blau, Grün
  oder Schwarz
- Entfernungsbeschriftungen liegen jeweils auf der eigenen Seite vor dem
  zugehörigen Bogen
- genau **eine** zentrale, um 90° gedrehte `)(`-Klammer in der Mitte der
  Ziellinie
- an der Klammer:
  - oben: Bezeichnung und Grundrichtung als **MRS 01  GR xxxx mils**
  - unten: tatsächliche Entfernung zum Ziel in Metern
- rotes Zielkreuz ohne dauerhafte Zielbeschriftung
- beim Antippen der Darstellung wird die Zielkoordinate in **MGRS** angezeigt

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
aktuell für **ATAK-CIV 5.6.0** konfiguriert.

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

Die maximale Sektorreichtiefe bleibt fest auf **8 km**. Andere
Darstellungsoptionen werden pro Zeichnung verwaltet.


## CI-Build für ATAK 5.6.0

Die GitHub-Action baut das Plugin mit `ATAK_VERSION = 5.6.0`.

Da zum aktuellen öffentlichen ATAK-CIV-Quellstand noch kein vollständiges
binäres 5.6.0-SDK als Release-Artefakt veröffentlicht ist, erzeugt CI den
aktuellen `atak-gradle-takdev` direkt aus dem offiziellen ATAK-CIV-5.6-Quellstand
und verwendet daraus außerdem Signierschlüssel und Core-Proguard-Regeln.

Der ältere öffentliche `main.jar` aus dem 5.5.1.8-SDK wird ausschließlich als
Compile-Stub verwendet. Die vom Plugin verwendeten ATAK-APIs wurden zusätzlich
gegen den offiziellen 5.6.0-Quellstand geprüft. Zur Laufzeit liefert ATAK 5.6.0
die tatsächlichen Core-Klassen.


## Version 0.3.0

- mehrere gleichzeitig sichtbare, persistent gespeicherte Mrs-Zeichnungen
- direktes Antippen der Darstellung für Schnellaktionen
- direkte Drag-Griffe für Start und Ziel
- Live-Vorschau beim Ziehen vor dem Loslassen, auch bei Marker-Drag-Events
- MGRS-Eingabe mit Zwischenablage, Sofortprüfung und letzter Eingabe
- Rückgängig/Wiederholen
- Duplizieren, Bearbeiten und Löschen
- per Zeichnung ein-/ausblendbar: 500-m-Bögen, 1-km-Bögen,
  Entfernungsbeschriftungen, Klammer, Zielkreuz und Sektorfüllung
- einstellbare Transparenz der Sektorfüllung
- zoomadaptive Pfeil-, Zielkreuz-, Klammer- und Tick-Geometrie
- Diagnoseansicht mit ATAK-/Plugin-Version, Signaturstatus, Zeichnungszahl,
  Undo/Redo-Stand und letzter fehlerhafter Eingabe
- maximale Sektorreichtiefe bleibt bewusst **fest bei 8 km**

