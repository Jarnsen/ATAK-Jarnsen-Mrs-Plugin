# Jarnsen MRS Plugin 0.4.9 – 50-Strich-Raster und Startübersicht

## Gerätetest mit ATAK-CIV 5.6.0

1. Plugin mit leerem Zeichnungsspeicher öffnen. Die vollständige Übersicht mit „Neue Zeichnung“, „Zeichnungen verwalten“, „Alle anzeigen“, „Alle ausblenden“ und „Import / Export“ muss zuerst erscheinen.
2. Über „Neue Zeichnung“ Start und Ziel per Karte, MGRS, Marker und Eigenposition wählen.
3. Prüfen, dass GR-Werte nur Vielfache von 50 mils (6400er-System) anzeigen, einschließlich der Nordgrenze: 6375 wird auf 0000 gerundet.
4. Kontrollieren, dass Pfeil, Zielkreuz und Sektorgeometrie dieselbe gerundete Richtung verwenden und die Zielentfernung dabei erhalten bleibt.
5. Vorhandene Zeichnungen bearbeiten, aus-/einblenden, neu laden und mit Eigenposition-Folgen testen.

Die Richtungsrundung kann den sichtbaren Zielpunkt gegenüber dem angeklickten Punkt seitlich verschieben: höchstens 25 mils (1,40625°), bei 8 km ungefähr 196 m.

## Automatische Prüfungen

`MrsCoreLogicTest` prüft Rundung, Grenzübergang Nord und Umrechnung von mils in Grad. Build und Gerätebedienung müssen zusätzlich im ATAK-CIV-5.6-Umfeld bestätigt werden.
