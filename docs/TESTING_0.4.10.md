# Jarnsen Mrs Plugin 0.4.10 – Gerätetest (Fehlerkorrekturen und Härtung)

Version 0.4.10 enthält Fehlerkorrekturen und Härtungen. Die JVM-Regressionstests
(`./gradlew :app:testCivReleaseUnitTest`) decken MGRS, Strich-Umrechnung, Codec, Import,
Undo/Redo und Versionsvergleich ab. Alles, was ATAK-Klassen oder Android
benötigt, muss auf einem Gerät mit ATAK-CIV 5.6.0 geprüft werden:

## Verknüpfung und Neuzeichnen

1. Start = **Eigenposition mit „folgen“**, Ziel beliebig. Den Startgriff ziehen
   und loslassen. Erwartung: Der Punkt bleibt an der neuen Stelle, auch nach
   mehreren GPS-Aktualisierungen. Dasselbe für das Ziel mit verknüpftem ATAK-Marker
   (Marker danach verschieben: die Zeichnung darf nicht mehr folgen).
2. Eine Zeichnung mit Eigenposition und eine ohne anlegen. Unterwegs/mit
   simuliertem GPS: Nur die folgende Zeichnung bewegt sich; die Karte bleibt
   flüssig, auch mit vielen Markern.
3. Zeichnung mit verknüpftem Marker anlegen, ATAK neu starten, Marker erst
   danach auf der Karte erscheinen lassen (z. B. per CoT). Erwartung: Die
   Verknüpfung wird wiedergefunden, sobald ein Positions-Update eintrifft.
4. Plugin deaktivieren/neu laden: kein Absturz, keine Overlay-Reste.

## Speicher

5. Von 0.4.7 aktualisieren (Zeichnungen vorhanden). Erwartung: Alle Zeichnungen,
   Undo/Redo und Beschriftungszähler sind nach dem Start da.
6. Neue Zeichnung anlegen: Nummerierung läuft weiter („Mrs n“).

## Import

7. Export und erneuter Import: **Zusammenführen** und **Vorhandene ersetzen**
   (Dialog zeigt die Anzahl der vorhandenen Zeichnungen).
8. Datei mit `[]`: Meldung „keine Zeichnungen“, nichts wird überschrieben.
9. Datei mit Breite 95 oder Länge 200: wird als ungültig abgelehnt.
10. Datei mit doppelten IDs: beide Zeichnungen erscheinen.

## MGRS

11. `32UMV1234567890` gültig, `00UMV1234567890`, `61UMV1234567890` und
    `32UMW1234567890` ungültig; ungerade Ziffernzahl ungültig.

## Update

12. Frische Installation: Es erfolgt **keine** Netzwerkabfrage beim Öffnen des
    Werkzeugs. Menüpunkt „Auto-Update-Prüfung“ einschalten: Prüfung läuft bei
    Bedarf einmal täglich; offline wird sie nach etwa einer Stunde wiederholt.
13. **Nach Update suchen** funktioniert unabhängig vom Schalter.
14. Download einer neueren Version: Datei erscheint in
    `/atak/support/apks/custom`, keine `.part`/`.bak`-Reste. Eine gleiche oder
    ältere Version wird abgelehnt. Download-Abbruch (Flugmodus mittendrin):
    beim nächsten Versuch werden Reste entfernt.

## Offen aus der Durchsicht

15. **Gitternord:** Die Umrechnung `Grid = True − Konvergenz` nutzt ATAKs
    `computeGridConvergence`. Mit Start/Ziel weit östlich und westlich vom
    Zonenmittelmeridian (z. B. Zone 32 bei 6° O und bei 12° O) die angezeigte
    Strich-Zahl gegen eine bekannte Referenz (z. B. ATAK-Gitternord-Anzeige)
    prüfen. Seit 0.4.9 wird die Richtung auf 50 Strich gerundet und mit
    derselben Konvergenz zurückgerechnet; ein Vorzeichenfehler würde die
    Linie selbst kaum verschieben, aber die angezeigte GR-Zahl verfälschen.
    Dieser Punkt wurde nicht verändert und lässt sich nicht per Unit-Test
    absichern.
16. **0.4.9-Hinweis:** Zeichnungsliste/Tipp-Dialog zeigen die gewählte
    Zielkoordinate, das Zielkreuz liegt auf der gerundeten Achse (bis 25 Strich
    daneben). Prüfen, ob das für die Bedienung so gewollt ist.
