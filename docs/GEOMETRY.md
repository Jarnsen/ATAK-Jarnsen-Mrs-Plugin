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
| zentrale Klammer | Mitte zwischen Start und Ziel |

## Bezugssystem

Die Kartenpunkte werden geodätisch vom gewählten Startpunkt aus berechnet. Der
Start- und Zielpunkt kann jeweils die eigene Position, eine eingegebene
Dezimalgrad-Koordinate oder ein Punkt auf der Karte sein. Die Sektorgeometrie
benutzt den True-Bearing vom Start zum Ziel. Das ist korrekt, weil die Öffnung
von ±600 Strich relativ zur Mittellinie definiert ist.

Bei der Zielauswahl auf der Karte wird die komplette Darstellung ab dem
Berühren live aufgebaut. Während des Ziehens werden Ziellinie, Klammer,
Grundrichtung, Entfernung und Zielkoordinate fortlaufend neu berechnet; erst
beim Loslassen wird die Zielposition übernommen.

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

Die Klammer wird nur einmal gezeichnet. Sie liegt immer genau auf dem
geometrischen Mittelpunkt der Ziellinie zwischen Start und Ziel und besteht aus
zwei gegeneinander geöffneten Kurven ober- und unterhalb der Linie. Damit
entspricht sie dem besprochenen, um 90° gedrehten `)(`. Ändert sich Start oder
Ziel, wird ihre Position automatisch neu berechnet.

Direkt danach werden entlang der Mittellinie ausgerichtet dargestellt:

- oberhalb: `MRS 01  GR xxxx mils`
- unterhalb: aktuelle Zielentfernung in Metern

Das Zielkreuz besitzt keine dauerhafte Textbeschriftung. Beim Antippen der
Darstellung wird die aktuelle Zielkoordinate im MGRS-Format im Aktionsdialog
angezeigt.
