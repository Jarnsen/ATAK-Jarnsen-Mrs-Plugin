# Jarnsen Mrs Plugin 0.4.6 – Kartenobjekte und Eingaben

## Gerätetest mit ATAK-CIV 5.6.0

1. Plugin öffnen und genau eine Zeichnung anlegen. Karte verschieben, Zielpunkt ziehen und die Zeichnung speichern.
2. Zeichnung aus- und wieder einblenden sowie die Verwaltung öffnen. Es darf nur ein Sektor sichtbar sein und der Eintrag darf nicht vervielfacht werden.
3. Zeichnung bearbeiten und Start- oder Zielpunkt als Eigenposition wählen. Der Dialog muss eine sichtbare Option „Eigenposition folgen“ enthalten. Ohne Häkchen bleibt der gewählte Punkt fest; mit Häkchen folgt er späteren Positionsänderungen.
4. Beim Erstellen der Beschriftung Text eingeben und übernehmen. Die Tastatur muss aufgehen; der Text muss am Pfeil nahe dem Zielpunkt erscheinen und zusammen mit der GR-Angabe aktualisiert werden.
5. Farbfüllung wählen und die Zeichnung aus- und wieder einblenden. Die Karte muss durch die Füllung sichtbar bleiben.
6. Zoomstufen und Geräteausrichtung ändern. Beschriftungen und Sektorgrenzen müssen der Zeichnung folgen, ohne zusätzliche Sektoren zurückzulassen.

## Automatische Prüfungen

GitHub Actions baut das ATAK-CIV-5.6-Plugin, führt JVM-Regressionstests und Lint aus und erstellt das separate TAK.gov-Quellarchiv. Die Touch-Bedienung und die Darstellung müssen zusätzlich auf einem ATAK-Gerät bestätigt werden.
