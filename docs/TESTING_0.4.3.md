# Jarnsen Mrs Plugin 0.4.3 – Dialog-Regressionstest

## Arbeitsübersicht

1. ATAK-CIV 5.6.0 starten und **Jarnsen Mrs Plugin** antippen.
2. Bei vorhandenen Zeichnungen muss das Plugin-Menü den Status im Titel zeigen.
3. Prüfen, dass darunter alle Menüeinträge sichtbar und antippbar sind:
   **Neue Zeichnung**, **Zeichnungen verwalten**, **Alle anzeigen**,
   **Alle ausblenden**, **Import / Export**, **Rückgängig**, **Wiederholen**,
   **Diagnose**, **Nach Update suchen**, **Schließen**.
4. **Zeichnungen verwalten** öffnen und eine Zeichnung über **Löschen** entfernen.
5. Eine Zeichnung auf der Karte antippen und prüfen, dass **Bearbeiten**,
   **Entfernen** und **Zur Übersicht** in der Aktionsliste sichtbar sind.
6. Einen Import mit mehreren Zeichnungen beginnen und prüfen, dass die Auswahl
   **Zusammenführen / Vorhandene ersetzen / Abbrechen** sichtbar ist.

Erwartung: Statusinformationen verdecken auf keinem dieser Dialoge mehr die
Auswahlaktionen. Zeichnungsdaten bleiben mit Version 0.4.2 kompatibel.

Die automatische Prüfung bestätigt den Build und die Quellarchivstruktur.
Die Dialogdarstellung muss zusätzlich auf einem ATAK-Gerät geprüft werden.
