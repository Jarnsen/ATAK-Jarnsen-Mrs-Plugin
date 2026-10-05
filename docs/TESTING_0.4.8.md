# Jarnsen MRS Plugin 0.4.8 – Beschriftungen am Zielpfeil

## Gerätetest mit ATAK-CIV 5.6.0

1. Zeichnung mit kurzem Zielabstand anlegen. Die GR/MRS-Beschriftung und die Entfernungsangabe bleiben ausgeblendet, solange die jeweilige Textbreite nicht auf den freien Abschnitt zwischen Mittelklammer und Zielpfeil passt.
2. Zielpunkt schrittweise weiter vom Startpunkt wegbewegen. Jede Beschriftung erscheint einzeln, sobald ihr Text mit Sicherheitsabstand Platz hat.
3. Prüfen, dass beide Angaben vor der Pfeilspitze liegen, der GR-Wert nicht abgeschnitten wird und die Distanz lesbar bleibt.
4. Karte in mehreren Zoomstufen sowie gedreht betrachten. Die Beschriftungen müssen dem Zielpfeil folgen und beim Platzverlust wieder verschwinden.
5. Zeichnung bearbeiten, aus-/einblenden und ATAK neu starten. Es dürfen keine veralteten Beschriftungen zurückbleiben.

## Automatische Prüfungen

GitHub Actions baut das ATAK-CIV-5.6-Plugin, führt JVM-Regressionstests und Lint aus. Die Sichtbarkeitsschwellen und das Touch-/Darstellungsverhalten müssen zusätzlich auf einem ATAK-Gerät oder kompatiblen Emulator bestätigt werden.
