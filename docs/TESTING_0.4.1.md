# Jarnsen Mrs Plugin 0.4.1 – Geräte-Abnahme

Ziel: **ATAK-CIV 5.6.0 Release**

## Regression: mehrere Zeichnungen und Bearbeiten

1. Erste Zeichnung vollständig anlegen und speichern.
2. Prüfen, dass danach das Zeichnungsmenü geöffnet bleibt.
3. **Neue Zeichnung** wählen und eine zweite Zeichnung mit anderen Punkten anlegen.
4. **Zur Übersicht** öffnen und beide Zeichnungen prüfen.
5. Bei beiden Zeichnungen **Bearbeiten** öffnen und jeweils Start oder Ziel ändern.
6. ATAK neu starten und prüfen, dass beide Zeichnungen weiter vorhanden und
   getrennt bearbeitbar sind.

Erwartung: Neue Zeichnungen erhalten eigene IDs und überschreiben keine
vorhandene Zeichnung. Bearbeiten verändert ausschließlich die ausgewählte
Zeichnung.

## Regression: ausschließlich MGRS

1. Start- und Zielauswahl öffnen.
2. Prüfen, dass nur **Eigene Position**, **MGRS eingeben** und
   **Auf der Karte wählen** angeboten werden.
3. Im Bearbeitungsmenü Start und Ziel per MGRS ändern.
4. Prüfen, dass keine andere manuelle Koordinateneingabe angeboten wird.

Erwartung: Jede manuelle Koordinateneingabe erfolgt als MGRS.

## Zeichnungsverwaltung

1. Mindestens drei Zeichnungen anlegen.
2. In **Zeichnungen verwalten** jede Zeichnung einzeln ein-/ausblenden.
3. Farbe, MGRS-Ziel und Direktaktionen der richtigen Zeichnung zuordnen.
4. **Alle anzeigen** und **Alle ausblenden** testen.
5. Öffnen, Kopieren und Löschen direkt aus der Übersicht testen.

Erwartung: Nur die gewählte Zeichnung wird verändert.

## Hervorhebung und Drag-Griffe

1. Eine Zeichnung antippen.
2. Prüfen, dass Mittellinie, Pfeil und zentrale Klammer während der aktiven
   Bearbeitung deutlich hervorgehoben sind.
3. **Punkte direkt ziehen** wählen.
4. Start- und Zielgriff jeweils anfassen, bewegen und loslassen.

Erwartung: Der Griff wird beim Ziehen größer; die gesamte Darstellung wird
bereits vor dem Loslassen live aktualisiert. Nach dem Loslassen verschwinden
die Griffe und die neue Position ist gespeichert.

## MGRS

Folgende Varianten testen:

- `32U MV 12345 67890`
- `32UMV1234567890`
- Kleinbuchstaben
- Einfügen aus der Zwischenablage
- absichtlich ungültige Eingabe

Erwartung: Kompakte Eingaben werden kanonisch formatiert, die erkannte
Koordinate wird vor dem Übernehmen angezeigt und ungültige Eingaben werden
abgewiesen.

## Persistenz, Backup und Undo/Redo

1. Mehrere Änderungen durchführen.
2. ATAK vollständig beenden und neu starten.
3. Rückgängig/Wiederholen erneut verwenden.
4. Eine Zeichnung ändern oder löschen und anschließend über
   **Import / Export → Backup wiederherstellen** den vorherigen gültigen Stand
   laden.

Erwartung: Zeichnungen und bis zu 10 Undo/Redo-Zustände bleiben erhalten.
Ein beschädigter Primärstand kann intern auf den letzten gültigen
Backup-Stand zurückfallen.

## JSON-Import/Export

1. **JSON exportieren** ausführen.
2. Datei unter `atak/tools/jarnsen-mrs` prüfen.
3. Eine zweite Zeichnung hinzufügen.
4. Exportdatei einmal **Zusammenführen** und einmal **Vorhandene ersetzen**
   importieren.

Erwartung: Bei ID-Kollisionen erzeugt Zusammenführen eine neue Kopie, statt
eine vorhandene Zeichnung unbemerkt zu überschreiben.

## Darstellung und Performance

1. Mit mehreren sichtbaren Zeichnungen wiederholt hinein-/herauszoomen.
2. Eine Zeichnung mehrfach öffnen/schließen.
3. Diagnose öffnen und **Geometrie-Cache Treffer/Neu** beobachten.

Erwartung: Statische 500-m-/1-km-Geometrie wird bei unveränderter
Start-/Richtungslage wiederverwendet. Pfeil, Zielkreuz, Ticks und die zentrale
`)(`-Klammer bleiben zoomadaptiv.

## Diagnose

1. Diagnose öffnen.
2. **Kopieren** testen.
3. **Exportieren** testen.
4. Plugin-Zertifikat, ATAK-Version, Plugin-Version, Zeichnungszahl,
   Sichtbarkeitszahl, Undo/Redo und Cache-Statistik prüfen.

## Update-Prüfung

**Nach Update suchen** ausführen.

Erwartung:
- bei öffentlich erreichbarem Repository wird ein neueres Release angezeigt;
- bei privatem/nicht öffentlich erreichbarem Repository erscheint nur bei
  manueller Prüfung eine verständliche Meldung;
- automatische Prüfungen erzeugen bei Nichterreichbarkeit keine Störung.

## Feste Geometrie

Die Sektorreichtiefe bleibt immer **8 km**. Es darf keine Einstellung für eine
variable maximale Reichweite geben.
