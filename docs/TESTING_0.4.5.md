# Jarnsen Mrs Plugin 0.4.5 – Ziel ziehen ohne Kartenverschiebung

## Gerätetest mit ATAK-CIV 5.6.0

1. Plugin starten, Startpunkt wählen und **Auf der Karte wählen** für das Ziel aufrufen.
2. Einen Finger auf die Karte setzen und ziehen. Die Kartenansicht darf dabei nicht mitwandern; die Zielvorschau muss dem Finger folgen.
3. Finger loslassen. Die Zielauswahl soll abgeschlossen werden.
4. Zielauswahl erneut starten und mit zwei Fingern hinein- und herauszoomen. Die Karte muss zoomen, das Ziel darf dabei nicht festgesetzt werden.
5. Nach dem Pinch beide Finger anheben. Mit einer neuen Ein-Finger-Berührung das Ziel ziehen. Die Karte bleibt stehen; das Ziel folgt und wird erst beim Loslassen übernommen.
6. Einen ATAK-Marker antippen und ziehen. Prüfen, dass Zielauswahl und Markerbewegung weiter funktionieren.
7. Abbrechen und ein neues Ziel starten. Prüfen, dass normales Kartenverschieben außerhalb der Zielauswahl wieder funktioniert.
8. Beim Startpunkt die Karte verschieben und einen Punkt wählen. Prüfen, dass Kartenverschieben in diesem Auswahlmodus weiterhin möglich ist.

## Technische Erwartung

Während der Zielauswahl wird ATAKs MAP_SCROLL-Kamerabewegung im temporären Event-Listener-Satz entfernt. MAP_DRAW bleibt aktiv und aktualisiert die Zielvorschau. MAP_SCALE wird explizit an ATAKs Standard-Skalierungsbehandlung weitergereicht. Der Event-Listener-Satz wird beim Beenden der Auswahl wiederhergestellt.

Die CI prüft Build, JVM-Regressionstests und Lint. Echte Touch- und ATAK-Eventfolgen benötigen weiterhin einen Gerätetest.
