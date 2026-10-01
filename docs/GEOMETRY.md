# Geometrie des Jarnsen Mrs Plugin

## Feste Geometriewerte

| Wert | Einstellung |
|---|---:|
| Vollkreis | 6400 Strich |
| Sektor links | 600 Strich |
| Sektor rechts | 600 Strich |
| Gesamtöffnung | 1200 Strich |
| 600 Strich in Grad | 33,75° |
| Ringabstand | 500 m |
| volle Kilometer | stärker / durchgezogen |
| 500-m-Zwischenringe | dünner / gestrichelt |
| Sektor-Maximum | 8000 m |
| zentrale Klammer | 4000 m |

## Bezugssystem

Die Kartenpunkte werden geodätisch vom gewählten Startpunkt aus berechnet. Der
Start- und Zielpunkt kann jeweils die eigene Position, eine eingegebene
Dezimalgrad-Koordinate oder ein Punkt auf der Karte sein. Die Sektorgeometrie
benutzt den True-Bearing vom Start zum Ziel. Das ist korrekt, weil die Öffnung
von ±600 Strich relativ zur Mittellinie definiert ist.

Die sichtbare Richtungsangabe wird auf Gitternord umgerechnet:

1. True-Bearing Start → Ziel
2. ATAK Grid Convergence bestimmen
3. Grid-Bearing = True-Bearing - Grid Convergence
4. Grid-Bearing auf 0…360° normalisieren
5. auf 0…6399 Strich umrechnen

## 500-m- und 1-km-Bögen

Jede 500-m-Stufe wird gezeichnet. Bei vollen Kilometern wird eine stärkere
durchgezogene Linie verwendet; die Zwischenstufen bei 500 m werden dünner und
gestrichelt dargestellt.

## Klammer

Die Klammer wird nur einmal gezeichnet. Sie liegt bei 4 km auf der Mittellinie
und besteht aus zwei gegeneinander geöffneten Kurven ober- und unterhalb der
Linie. Damit entspricht sie dem besprochenen, um 90° gedrehten `)(`.

Direkt danach werden entlang der Mittellinie ausgerichtet dargestellt:

- oberhalb: `xxxx Str GN`
- unterhalb: aktuelle Zielentfernung
