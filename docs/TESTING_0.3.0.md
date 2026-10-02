# Jarnsen Mrs Plugin 0.3.0 – Geräte-Abnahmetest

Zielgerät: **ATAK-CIV 5.6.0 Release**

Dieser Test prüft ausschließlich die Bedien- und Darstellungsfunktionen des
Plugins. Die maximale Sektorreichtiefe bleibt fest auf **8 km**.

## 1. Mehrere Zeichnungen und Persistenz

1. Zwei unterschiedliche Mrs-Zeichnungen anlegen.
2. Unterschiedliche Namen und Farben vergeben.
3. ATAK vollständig beenden und erneut starten.
4. Prüfen, dass beide Zeichnungen wieder erscheinen und getrennt bearbeitbar
   bleiben.

Erwartung: Beide Zeichnungen bleiben gespeichert; keine Zeichnung überschreibt
die andere.

## 2. Direktes Öffnen / Schnellaktionen

1. Mittellinie oder breite unsichtbare Trefferzone einer Zeichnung antippen.
2. Prüfen, dass das Menü der richtigen Zeichnung öffnet.
3. Ziel neu setzen, Start neu setzen, Kopie erstellen und Entfernen testen.
4. Nach Entfernen einmal Rückgängig und danach Wiederholen testen.

Erwartung: Aktionen betreffen nur die ausgewählte Zeichnung.

## 3. Drag-Griffe und Live-Vorschau

1. **Punkte direkt ziehen** öffnen.
2. Start-Griff anfassen und bewegen.
3. Während des Ziehens auf die komplette Darstellung achten.
4. Loslassen.
5. Dasselbe mit dem Ziel-Griff wiederholen.

Erwartung: Sektor, Mittellinie, Richtung, Entfernung, Zielkreuz und Klammer
werden bereits **vor dem Loslassen** live nachgeführt. Nach Loslassen wird die
neue Position gespeichert.

## 4. MGRS

1. Start und Ziel jeweils per MGRS eingeben.
2. Eine MGRS-Koordinate aus der Zwischenablage einfügen.
3. Eingaben mit mehreren Leerzeichen bzw. Kleinbuchstaben testen.
4. Absichtlich eine ungültige Koordinate eingeben.
5. Dialog erneut öffnen und prüfen, dass die letzte gültige MGRS-Eingabe
   angeboten wird.

Erwartung: Gültige Eingaben werden übernommen; ungültige Eingaben werden vor
dem Speichern markiert/abgewiesen.

## 5. Darstellung pro Zeichnung

Jeweils einzeln ein-/ausschalten:

- 500-m-Zwischenbögen
- 1-km-Bögen
- Entfernungsbeschriftungen
- zentrale )( -Klammer
- Zielkreuz
- Sektorfüllung

Zusätzlich Transparenz und Farbe ändern.

Erwartung: Änderungen betreffen nur die aktive Zeichnung und bleiben nach
ATAK-Neustart erhalten.

## 6. Zoom

Mehrfach deutlich hinein- und herauszoomen.

Erwartung: Pfeilspitze, Zielkreuz, Ticks und die zentrale )( -Klammer bleiben
optisch proportional. Ringbeschriftungen im Bereich der Klammer werden
seitlich weiter versetzt, damit sich die Texte nicht direkt mit der
Klammerbeschriftung überlagern.

## 7. Diagnose

**Jarnsen Mrs Plugin → Diagnose** öffnen.

Prüfen:

- Plugin-Version 0.3.0
- installierte ATAK-Version
- Ziel-API 5.6.0 CIV
- Signatur-Zertifikat und SHA-256-Fingerprint
- Anzahl gespeicherter Zeichnungen
- Undo-/Redo-Stand
- letzte MGRS-Eingabe
- letzter erkannter Eingabefehler

## 8. Reichweite

Prüfen, dass der Sektor immer bis maximal **8 km** gezeichnet wird und keine
variable Reichweiteneinstellung vorhanden ist.
