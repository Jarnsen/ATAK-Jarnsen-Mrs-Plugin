# Jarnsen Mrs Plugin 0.4.4 – Zielauswahl und Pinch-Zoom

## Auf einem Gerät mit ATAK-CIV 5.6.0 prüfen

1. Plugin starten und Startpunkt wählen.
2. Als Ziel **Auf der Karte wählen** auswählen.
3. Mit zwei Fingern hinein- und herauszoomen. Beim Loslassen darf der Zielpunkt nicht übernommen werden; die Zielauswahl und Kartenanweisung bleiben aktiv.
4. Danach mit einem Finger den Zielpunkt an die gewünschte Stelle ziehen. Die Vorschau muss während des Ziehens folgen und erst beim Loslassen abgeschlossen werden.
5. Zielauswahl erneut starten und einen Punkt mit einem einzelnen Tipp setzen. Die Auswahl muss wie bisher abgeschlossen werden.
6. Mit einem ATAK-Marker wiederholen: Pinch-Zoom darf die Auswahl nicht beenden; Marker-Tipp oder Ziehen muss weiterhin möglich sein.

Erwartung: MAP_SCALE während der Zielauswahl markiert die Touch-Geste als Pinch-Zoom. Dazu gehörende Release-/Click-Ereignisse setzen den Zielpunkt nicht endgültig. Eine neue Karten- oder Markerberührung beginnt eine normale Platzierungsgeste.

Der GitHub-Build kompiliert gegen ATAK-CIV 5.6.0 und führt die JVM-Regressionstests sowie Lint aus. Die Touch-Prüfschritte benötigen ein ATAK-Gerät.
