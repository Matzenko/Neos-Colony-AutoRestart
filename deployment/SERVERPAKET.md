# Privates Serverpaket – AutoRestart 1.0.1

Das Paket enthält die unveränderte veröffentlichte JAR und die bereits vorbereitete Neos-Colony-Vertrauensdatei. Private Verbindungsdaten: NICHT auf GitHub oder öffentlich hochladen. Die Vertrauensdatei wird als Client-Payload im privaten AutoModpack-Hostinhalt gespeichert, nicht als Server-Mod oder im aktiven Server-config. Zustimmungs-/known-hosts-Dateien werden weder kopiert noch erstellt.

## Einmalig auf dem Serverrechner

1. ZIP außerhalb von C:\NeosColony entpacken, z.B. auf dem Administrator-Desktop.
2. PowerShell im Paketordner öffnen und nur prüfen:
   .\Install-ServerPackage.ps1 -ServerRoot 'C:\NeosColony'
3. Server nach eurem üblichen Verfahren vollständig stoppen. Das Skript steuert keine Serverprozesse.
4. Installieren:
   .\Install-ServerPackage.ps1 -ServerRoot 'C:\NeosColony' -Apply -ServerStopped
5. Server normal starten. Die vorhandene AutoModpack-Startgenerierung erstellt das Manifest; dieses nicht manuell bearbeiten.
6. Abschließend im Paketordner prüfen:
   .\Install-ServerPackage.ps1 -ServerRoot 'C:\NeosColony' -Verify

Nur VERIFIED bestätigt das serverseitige Manifest. Neue Spieler erst nach Start/erfolgreicher Prüfung aktualisieren lassen; ein noch laufender Server kann ein altes Manifest im RAM ausliefern.

## Exakte Änderungen

- Alte hashgeprüfte AutoRestart-1.0.0-JARs aus automodpack\host-modpack\main und ggf. der direkten mods-Verteilquelle in eine Sicherung außerhalb des Serverordners zurücknehmen. Umbenannte/unbekannte AutoRestart-Versionen führen zum Abbruch.
- automodpack\host-modpack\main\mods\neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar
- automodpack\host-modpack\main\config\neoscolony-autorestart-trusted.properties
- In automodpack\automodpack-server.json bestehende Einträge erhalten und ausschließlich ergänzen:
  forceCopyFilesToStandardLocation: /config/neoscolony-autorestart-trusted.properties
  nonModpackFilesToDelete: /mods/neos-colony-automodpack-autorestart-1.21.1-1.0.0.jar -> d0f8f39848b77e6ea6034b6a85f8400b3267140b

Die exakte Kopierregel garantiert den vom Mod gelesenen Clientpfad config/..., auch bei allgemeinen Editable-Regeln. Die Löschregel betrifft nur die alte 1.0.0-JAR mit passendem SHA-1 und nutzt die vorhandene AutoModpack-Funktion. Andere Werte bleiben semantisch unverändert; JSON-Formatierung kann sich ändern. Kein zusätzlicher syncedFiles-Eintrag für separaten Hostinhalt erforderlich.

automodpack\host-modpack\automodpack-content.json wird von AutoModpack neu erzeugt. Die Prüfung verlangt genau die neue JAR mit richtigem Hash/Größe und die Konfiguration mit richtigem Hash und forceCopy=true. GUI, andere Mods, Zertifikate, Schlüssel und Neustartkern bleiben unverändert.

## Alle fünf Clients

Beim normalen Beitritt werden dieselbe JAR 1.0.1 und config/neoscolony-autorestart-trusted.properties automatisch verteilt. Keine individuellen Dateikopien nötig.

Jeder Spieler bestätigt sein erstes Vertrauen im unveränderten AutoModpack-Zertifikatsdialog selbst. Die Zielkonfiguration allein erteilt kein Vertrauen. Bei bekanntem passendem Zertifikat sind spätere Bestätigungen vereinfacht; fremde/abweichende Server behalten Sicherheitsabfragen.

Bei bereits geladenem AutoRestart 1.0.0/1.0.1 bleibt der vorhandene automatische Neustart aktiv. Wer AutoRestart noch nie geladen hat, benötigt beim ersten Installationsdurchlauf dessen ursprünglichen initialen Neustartweg.

Die native Löschregel respektiert allowRemoteNonModpackDeletions. Ist dies bewusst deaktiviert und liegt eine alte manuelle JAR außerhalb des verwalteten Modpacks, darf sie nicht gegen den Spielerwillen entfernt werden. Vor breitem Rollout einen Client auf doppelte Mod-IDs prüfen. Normal verwaltete alte Dateien entfernt der reguläre Abgleich.

## Sicherung und Grenzen

backups/<ID>/ im Paketordner enthält Original-JSON, ersetzte Dateien und restore-map.json. Paket/Sicherungen nie in den Serverordner verschieben. Bei Installationsfehlern versucht das Skript die betroffenen Originaldateien wiederherzustellen. Spätere Rücknahme bei gestopptem Server anhand dieser Sicherung, danach normales neues Manifest.

C:\NeosColony war bei Erstellung nicht erreichbar. Das Paket ist vorbereitet und isoliert geprüft, aber noch nicht auf dem Produktivserver installiert. Tatsächliche fünf Spielersysteme nicht als getestet behauptet. Zuerst einen Spieler prüfen, dann die übrigen.
