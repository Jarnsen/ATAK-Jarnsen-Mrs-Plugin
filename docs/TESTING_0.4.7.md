# Jarnsen Mrs Plugin 0.4.7 – Update-Hinweis und APK-Ablage

## Gerätetest mit ATAK-CIV 5.6.0

1. Im Plugin-Menü **Nach Update suchen** wählen. Bei einer neueren Version erscheint eine Auswahl, bevor etwas heruntergeladen wird.
2. **APK herunterladen** wählen. Es muss ausschließlich das TAK.gov-signierte Jarnsen-Mrs-APK geladen und per SHA-256 und Paketname geprüft werden.
3. Prüfen, dass die Datei in ATAKs lokalem Plugin-Verzeichnis `/atak/support/apks/custom` liegt. Die Datei wird nicht automatisch gestartet oder installiert; ATAK kann sie im **Lokalen APK-Verzeichnis** auswählen.
4. Prüfen, dass fremde APKs und `product.inf` nicht verändert werden und nur ältere gestagte APK-Dateien dieses Plugins ersetzt werden.
5. Bei fehlendem oder nicht gültigem APK-Hash muss der Download abbrechen; die Release-Seite darf weiter geöffnet werden.
6. Netzwerk aus: die Update-Prüfung muss mit verständlicher Meldung abbrechen, Zeichnungen und Bedienung bleiben verfügbar.

## Automatische Prüfungen

GitHub Actions baut das ATAK-CIV-5.6-Plugin, führt JVM-Regressionstests und Lint aus und validiert das TAK.gov-Quellarchiv. Der Download und die Dateiablage müssen zusätzlich auf dem ATAK-Gerät bestätigt werden.
